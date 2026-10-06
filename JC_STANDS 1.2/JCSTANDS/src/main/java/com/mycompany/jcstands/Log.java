package com.mycompany.jcstands;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.format.DateTimeFormatter;

/**
 * Log simple en archivo (%APPDATA%\JCSTANDS\logs\jcstands.log).
 * Nunca lanza excepciones: si no puede escribir, lo informa por consola y sigue.
 * Rota el archivo al superar 1 MB (se conservan jcstands.log.1 a .3).
 */
public final class Log {
    private static final long TAMANIO_MAXIMO = 1_000_000L;
    private static final int ARCHIVOS_ROTADOS = 3;
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Log() {}

    public static void info(String mensaje) { escribir("INFO", mensaje, null); }
    public static void warn(String mensaje) { escribir("WARN", mensaje, null); }
    public static void error(String mensaje, Throwable causa) { escribir("ERROR", mensaje, causa); }

    private static synchronized void escribir(String nivel, String mensaje, Throwable causa) {
        StringBuilder linea = new StringBuilder();
        linea.append(ClockApp.ahora().format(FORMATO)).append(" [").append(nivel).append("] ").append(mensaje);
        if (causa != null) {
            StringWriter sw = new StringWriter();
            causa.printStackTrace(new PrintWriter(sw));
            linea.append(System.lineSeparator()).append(sw.toString().stripTrailing());
        }
        linea.append(System.lineSeparator());

        try {
            Path archivo = AppPaths.archivoLog();
            Files.createDirectories(archivo.getParent());
            rotarSiHaceFalta(archivo);
            Files.writeString(archivo, linea.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException e) {
            System.err.print(linea);
            System.err.println("(No se pudo escribir el log: " + e.getMessage() + ")");
        }
    }

    private static void rotarSiHaceFalta(Path archivo) throws IOException {
        if (!Files.exists(archivo) || Files.size(archivo) < TAMANIO_MAXIMO) return;
        for (int i = ARCHIVOS_ROTADOS; i >= 1; i--) {
            Path origen = i == 1 ? archivo : archivo.resolveSibling(archivo.getFileName() + "." + (i - 1));
            Path destino = archivo.resolveSibling(archivo.getFileName() + "." + i);
            if (Files.exists(origen)) Files.move(origen, destino, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
