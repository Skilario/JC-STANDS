package com.mycompany.jcstands;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Database {
    /** Versión actual del esquema (PRAGMA user_version). Sumar 1 por cada migración nueva. */
    static final int VERSION_ESQUEMA = 2;

    private static final int ESPERA_BLOQUEO_MS = 5000;

    private Database() {}

    public static Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(AppPaths.urlJdbc());
        try (Statement st = connection.createStatement()) {
            // busy_timeout primero: los pragmas siguientes ya esperan si la base está ocupada.
            st.execute("PRAGMA busy_timeout = " + ESPERA_BLOQUEO_MS);
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("PRAGMA journal_mode = WAL");
        } catch (SQLException | RuntimeException e) {
            try { connection.close(); } catch (SQLException ignorada) { /* ya se informa el error original */ }
            throw e;
        }
        return connection;
    }

    /**
     * Crea o actualiza el esquema. Es seguro ejecutarlo en cada inicio: solo aplica las
     * migraciones que faltan según PRAGMA user_version, y antes de migrar una base con
     * datos guarda un backup.
     */
    public static void inicializar() throws SQLException {
        try (Connection c = getConnection()) {
            int version = leerVersion(c);
            if (version > VERSION_ESQUEMA) {
                throw new SQLException("La base de datos fue creada por una versión más nueva de JC STANDS "
                        + "(esquema " + version + ", esta versión soporta hasta " + VERSION_ESQUEMA + ").");
            }
            if (version < VERSION_ESQUEMA) {
                if (existeTabla(c, "empleados")) {
                    try {
                        Respaldo.crearAntesDeMigrar(version);
                    } catch (IOException | SQLException e) {
                        // Sin backup previo no se toca una base con datos.
                        throw new SQLException("No se pudo crear el backup previo a la actualización de la base de datos.", e);
                    }
                }
                for (int v = version + 1; v <= VERSION_ESQUEMA; v++) aplicarMigracion(c, v);
            }
        }
    }

    public static File archivoBaseDatos() {
        return AppPaths.archivoBaseDatos().toFile();
    }

    // ------------------------------------------------------------------
    // Lectura de dinero
    // ------------------------------------------------------------------

    /** Lee un valor monetario como BigDecimal (con al menos 2 decimales), sin pasar por double. */
    static BigDecimal leerDinero(ResultSet rs, String columna) throws SQLException {
        String texto = rs.getString(columna);
        if (texto == null) throw new SQLException("Valor monetario vacío en la columna " + columna);
        try {
            BigDecimal v = new BigDecimal(texto.trim());
            return v.scale() < 2 ? v.setScale(2) : v;
        } catch (NumberFormatException e) {
            throw new SQLException("Valor monetario inválido en la columna " + columna + ": " + texto, e);
        }
    }

    // ------------------------------------------------------------------
    // Migraciones
    // ------------------------------------------------------------------

    private static int leerVersion(Connection c) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("PRAGMA user_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static boolean existeTabla(Connection c, String nombre) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            ps.setString(1, nombre);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    private static void aplicarMigracion(Connection c, int destino) throws SQLException {
        boolean autoCommit = c.getAutoCommit();
        c.setAutoCommit(false);
        try {
            switch (destino) {
                case 1 -> migracion1(c);
                case 2 -> migracion2(c);
                default -> throw new SQLException("No existe la migración " + destino);
            }
            try (Statement st = c.createStatement()) {
                st.execute("PRAGMA user_version = " + destino);
            }
            c.commit();
            Log.info("Esquema de la base de datos actualizado a la versión " + destino);
        } catch (SQLException | RuntimeException e) {
            try { c.rollback(); } catch (SQLException ignorada) { /* se informa el error original */ }
            throw e;
        } finally {
            c.setAutoCommit(autoCommit);
        }
    }

    /** v1: esquema original (tablas e índices). No modifica bases que ya lo tienen. */
    private static void migracion1(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS empleados (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    nombre TEXT NOT NULL,
                    valor_hora REAL NOT NULL CHECK(valor_hora > 0),
                    activo INTEGER NOT NULL DEFAULT 1,
                    fecha_alta TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS jornadas (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    empleado_id INTEGER NOT NULL,
                    entrada TEXT NOT NULL,
                    salida TEXT,
                    valor_hora REAL NOT NULL CHECK(valor_hora > 0),
                    FOREIGN KEY (empleado_id) REFERENCES empleados(id) ON DELETE RESTRICT
                )
            """);

            st.execute("CREATE INDEX IF NOT EXISTS idx_jornadas_empleado ON jornadas(empleado_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_jornadas_entrada ON jornadas(entrada)");
        }
    }

    /**
     * v2: protecciones a nivel base de datos.
     *  - Un empleado no puede tener dos jornadas abiertas (índice único parcial).
     *  - No puede haber dos empleados activos con el mismo nombre (sin distinguir mayúsculas).
     * No borra ni modifica empleados; solo, si una base vieja ya tuviera jornadas abiertas
     * duplicadas, cierra las más antiguas en el momento en que empezó la siguiente.
     */
    private static void migracion2(Connection c) throws SQLException {
        resolverJornadasAbiertasDuplicadas(c);
        try (Statement st = c.createStatement()) {
            st.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_jornada_abierta_por_empleado "
                    + "ON jornadas(empleado_id) WHERE salida IS NULL");
        }

        boolean nombresDuplicados;
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1 FROM empleados WHERE activo = 1 "
                     + "GROUP BY nombre COLLATE NOCASE HAVING COUNT(*) > 1 LIMIT 1")) {
            nombresDuplicados = rs.next();
        }
        if (nombresDuplicados) {
            // No se modifican empleados existentes; la validación en el programa igual impide nuevos duplicados.
            Log.warn("La base ya tiene empleados activos con nombres repetidos; se omite el índice único de nombres.");
        } else {
            try (Statement st = c.createStatement()) {
                st.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_empleado_nombre_activo "
                        + "ON empleados(nombre COLLATE NOCASE) WHERE activo = 1");
            }
        }
    }

    private static void resolverJornadasAbiertasDuplicadas(Connection c) throws SQLException {
        Map<Integer, List<Object[]>> porEmpleado = new LinkedHashMap<>();
        try (Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, empleado_id, entrada FROM jornadas "
                     + "WHERE salida IS NULL ORDER BY empleado_id, entrada, id")) {
            while (rs.next()) {
                porEmpleado.computeIfAbsent(rs.getInt("empleado_id"), k -> new ArrayList<>())
                        .add(new Object[]{rs.getInt("id"), LocalDateTime.parse(rs.getString("entrada"))});
            }
        }
        for (Map.Entry<Integer, List<Object[]>> e : porEmpleado.entrySet()) {
            List<Object[]> abiertas = e.getValue();
            for (int i = 0; i < abiertas.size() - 1; i++) {
                int id = (Integer) abiertas.get(i)[0];
                LocalDateTime entrada = (LocalDateTime) abiertas.get(i)[1];
                LocalDateTime siguiente = (LocalDateTime) abiertas.get(i + 1)[1];
                LocalDateTime salida = siguiente;
                try (PreparedStatement ps = c.prepareStatement("UPDATE jornadas SET salida = ? WHERE id = ? AND salida IS NULL")) {
                    ps.setString(1, salida.toString());
                    ps.setInt(2, id);
                    ps.executeUpdate();
                }
                Log.warn("Migración: el empleado " + e.getKey() + " tenía varias jornadas abiertas; "
                        + "se cerró la jornada " + id + " con salida " + salida);
            }
        }
    }

    // ------------------------------------------------------------------
    // Ubicación estable de la base: importar la base anterior (jcstands.db junto al .exe)
    // ------------------------------------------------------------------

    /**
     * Si todavía no existe la base en %LOCALAPPDATA%\JCSTANDS pero sí una base de la versión anterior
     * (jcstands.db en la carpeta desde donde se ejecutaba el programa), la importa mediante una copia
     * consistente. La base anterior NO se borra ni se modifica. Si la base nueva ya existe, no hace nada:
     * nunca se sobrescribe.
     *
     * @return texto para informar al usuario si se importó una base, o null si no hizo falta.
     */
    public static String importarBaseAnteriorSiCorresponde() throws IOException, SQLException {
        Path nueva = AppPaths.archivoBaseDatos();
        if (Files.exists(nueva)) return null;

        List<Path> candidatas = candidatasBaseAnterior(nueva);
        if (candidatas.isEmpty()) return null;

        Path elegida = candidatas.get(0);
        for (Path p : candidatas) {
            if (Files.getLastModifiedTime(p).compareTo(Files.getLastModifiedTime(elegida)) > 0) elegida = p;
        }

        Path temporal = nueva.resolveSibling("jcstands.db.importando");
        Files.deleteIfExists(temporal);
        try (Connection origen = DriverManager.getConnection("jdbc:sqlite:" + elegida);
             Statement st = origen.createStatement()) {
            st.execute("PRAGMA busy_timeout = " + ESPERA_BLOQUEO_MS);
            st.execute("VACUUM INTO '" + temporal.toString().replace("'", "''") + "'");
        } catch (SQLException e) {
            Files.deleteIfExists(temporal);
            throw e;
        }
        Respaldo.verificar(temporal);
        Files.move(temporal, nueva, StandardCopyOption.ATOMIC_MOVE);

        StringBuilder texto = new StringBuilder();
        texto.append("Se encontró la base de datos de la versión anterior y se importó a la nueva ubicación.\n\n")
             .append("Base anterior (se conserva sin cambios):\n").append(elegida).append("\n\n")
             .append("Base actual:\n").append(nueva);
        Log.info("Base anterior importada desde " + elegida + " hacia " + nueva);
        for (Path otra : candidatas) {
            if (!otra.equals(elegida)) {
                Log.warn("Había otra base anterior que NO se importó: " + otra);
                texto.append("\n\nAtención: también había otra base en\n").append(otra)
                     .append("\nque no se importó (se usó la más reciente).");
            }
        }
        return texto.toString();
    }

    private static List<Path> candidatasBaseAnterior(Path nueva) {
        Set<Path> carpetas = new LinkedHashSet<>();
        carpetas.add(Path.of("").toAbsolutePath());                  // carpeta desde donde se ejecutó
        try {
            Path codigo = Path.of(Database.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path carpeta = Files.isDirectory(codigo) ? codigo : codigo.getParent();
            if (carpeta != null) {
                carpetas.add(carpeta);
                if (carpeta.getParent() != null) carpetas.add(carpeta.getParent());
            }
        } catch (URISyntaxException | RuntimeException e) {
            Log.warn("No se pudo determinar la carpeta del programa: " + e);
        }
        carpetas.add(AppPaths.carpetaRoamingAnterior());              // ubicación Roaming de una versión intermedia
        Set<Path> resultado = new LinkedHashSet<>();
        for (Path carpeta : carpetas) {
            Path candidata = carpeta.resolve("jcstands.db").normalize();
            try {
                if (Files.isRegularFile(candidata) && Files.size(candidata) > 0 && !candidata.equals(nueva.normalize())) {
                    resultado.add(candidata);
                }
            } catch (IOException e) {
                Log.warn("No se pudo revisar " + candidata + ": " + e);
            }
        }
        return new ArrayList<>(resultado);
    }
}
