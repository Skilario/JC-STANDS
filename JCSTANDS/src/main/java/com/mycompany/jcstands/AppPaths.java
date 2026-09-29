package com.mycompany.jcstands;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Ubicaciones de los archivos de la aplicación. La base de datos, los backups y
 * el log viven siempre en la misma carpeta, sin importar desde dónde se ejecute el programa:
 *
 *   %LOCALAPPDATA%\JCSTANDS\jcstands.db
 *   %LOCALAPPDATA%\JCSTANDS\backups\
 *   %LOCALAPPDATA%\JCSTANDS\logs\jcstands.log
 *
 * Se usa LOCALAPPDATA (carpeta local del equipo) y no APPDATA (Roaming): con perfiles móviles o
 * redirección de carpetas, una base SQLite en modo WAL sobre una ubicación de red puede corromperse.
 *
 * La propiedad de sistema "jcstands.home" permite cambiar la carpeta base (pensada para pruebas).
 */
public final class AppPaths {
    public static final String PROPIEDAD_CARPETA = "jcstands.home";

    private AppPaths() {}

    public static Path carpetaBase() {
        String forzada = System.getProperty(PROPIEDAD_CARPETA);
        if (forzada != null && !forzada.isBlank()) return Paths.get(forzada).toAbsolutePath();

        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) return Paths.get(localAppData, "JCSTANDS");

        // Sistemas sin %LOCALAPPDATA% (no es el caso normal en Windows).
        return Paths.get(System.getProperty("user.home"), "AppData", "Local", "JCSTANDS");
    }

    /**
     * Carpeta que usaron versiones intermedias del programa (%APPDATA%\JCSTANDS, Roaming).
     * Solo se consulta para importar una base que hubiera quedado ahí; nunca se escribe en ella.
     */
    public static Path carpetaRoamingAnterior() {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) return Paths.get(appData, "JCSTANDS");
        return Paths.get(System.getProperty("user.home"), "AppData", "Roaming", "JCSTANDS");
    }

    public static Path archivoBaseDatos() { return carpetaBase().resolve("jcstands.db"); }
    public static Path carpetaBackups() { return carpetaBase().resolve("backups"); }
    public static Path carpetaLogs() { return carpetaBase().resolve("logs"); }
    public static Path archivoLog() { return carpetaLogs().resolve("jcstands.log"); }
    public static Path archivoBloqueo() { return carpetaBase().resolve("jcstands.lock"); }

    public static String urlJdbc() {
        return "jdbc:sqlite:" + archivoBaseDatos();
    }

    /** Crea las carpetas necesarias si no existen. No toca ningún archivo existente. */
    public static void asegurarCarpetas() throws IOException {
        Files.createDirectories(carpetaBase());
        Files.createDirectories(carpetaBackups());
        Files.createDirectories(carpetaLogs());
    }
}
