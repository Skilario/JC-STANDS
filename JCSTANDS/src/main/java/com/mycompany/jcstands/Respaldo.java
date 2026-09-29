package com.mycompany.jcstands;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Backups de la base de datos en %APPDATA%\JCSTANDS\backups.
 *
 *  - Un backup por día como máximo (se hace al abrir el programa y, si queda abierto, al cambiar el día).
 *  - Se generan con VACUUM INTO: copia consistente aunque la base esté en uso (no bloquea escrituras).
 *  - Cada backup se verifica antes de darlo por bueno, y nunca se sobrescribe uno existente.
 *  - Se conservan los últimos MAX_DIARIOS backups diarios; los anteriores se borran recién
 *    después de haber creado y verificado uno nuevo.
 *  - Antes de migrar el esquema de una base con datos se guarda un backup aparte (premigracion_*),
 *    que no se rota automáticamente.
 */
public final class Respaldo {
    static final int MAX_DIARIOS = 30;

    private static final DateTimeFormatter SELLO = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Pattern PATRON_DIARIO = Pattern.compile("^respaldo_\\d{8}_\\d{6}(_\\d+)?\\.db$");

    private Respaldo() {}

    /** Crea el backup del día si todavía no existe uno de hoy. @return el archivo creado, o null si ya había. */
    public static synchronized Path crearDiarioSiHaceFalta() throws IOException, SQLException {
        LocalDate hoy = ClockApp.hoy();
        if (existeDiarioDe(hoy)) return null;
        Path creado = crear("respaldo");
        rotar(creado);
        Log.info("Backup diario creado: " + creado);
        return creado;
    }

    public static synchronized Path crearAntesDeMigrar(int versionActual) throws IOException, SQLException {
        Path creado = crear("premigracion_v" + versionActual);
        Log.info("Backup previo a la migración creado: " + creado);
        return creado;
    }

    static boolean existeDiarioDe(LocalDate dia) throws IOException {
        String prefijo = "respaldo_" + DIA.format(dia) + "_";
        Path carpeta = AppPaths.carpetaBackups();
        if (!Files.isDirectory(carpeta)) return false;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(carpeta, "respaldo_*.db")) {
            for (Path p : ds) {
                if (p.getFileName().toString().startsWith(prefijo) && Files.size(p) > 0) return true;
            }
        }
        return false;
    }

    private static Path crear(String prefijo) throws IOException, SQLException {
        Path carpeta = AppPaths.carpetaBackups();
        Files.createDirectories(carpeta);

        String sello = SELLO.format(ClockApp.ahora());
        Path destino = carpeta.resolve(prefijo + "_" + sello + ".db");
        for (int n = 1; Files.exists(destino); n++) {
            destino = carpeta.resolve(prefijo + "_" + sello + "_" + n + ".db");   // nunca pisar un backup existente
        }
        Path temporal = destino.resolveSibling(destino.getFileName() + ".tmp");
        Files.deleteIfExists(temporal);

        try {
            try (Connection c = Database.getConnection(); Statement st = c.createStatement()) {
                st.execute("VACUUM INTO '" + temporal.toString().replace("'", "''") + "'");
            }
            verificar(temporal);
            Files.move(temporal, destino);            // sin REPLACE_EXISTING: si existiera, falla y no pisa nada
        } catch (IOException | SQLException | RuntimeException e) {
            try { Files.deleteIfExists(temporal); } catch (IOException ignorada) { /* se informa el error original */ }
            throw e;
        }
        return destino;
    }

    /** Comprueba que el archivo sea una base SQLite íntegra y contenga las tablas de la aplicación. */
    static void verificar(Path archivo) throws IOException, SQLException {
        if (!Files.isRegularFile(archivo) || Files.size(archivo) == 0) {
            throw new IOException("El archivo de base de datos generado está vacío: " + archivo);
        }
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + archivo);
             Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("PRAGMA quick_check")) {
                if (!rs.next() || !"ok".equalsIgnoreCase(rs.getString(1))) {
                    throw new IOException("La verificación de integridad falló para " + archivo);
                }
            }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM empleados")) { rs.next(); }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM jornadas")) { rs.next(); }
        }
    }

    /** Borra los backups diarios más viejos, dejando los últimos MAX_DIARIOS. Nunca toca el recién creado. */
    private static void rotar(Path recienCreado) {
        try {
            List<Path> diarios = new ArrayList<>();
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(AppPaths.carpetaBackups())) {
                for (Path p : ds) {
                    if (PATRON_DIARIO.matcher(p.getFileName().toString()).matches()) diarios.add(p);
                }
            }
            Collections.sort(diarios);          // el nombre lleva fecha y hora: orden alfabético = cronológico
            int sobrantes = diarios.size() - MAX_DIARIOS;
            for (int i = 0; i < sobrantes; i++) {
                Path viejo = diarios.get(i);
                if (viejo.equals(recienCreado)) continue;
                Files.deleteIfExists(viejo);
                Log.info("Backup antiguo eliminado: " + viejo.getFileName());
            }
        } catch (IOException e) {
            Log.error("No se pudo limpiar la carpeta de backups", e);
        }
    }
}
