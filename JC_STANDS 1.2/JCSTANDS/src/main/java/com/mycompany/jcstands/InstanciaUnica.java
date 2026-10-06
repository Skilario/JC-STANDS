package com.mycompany.jcstands;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

/**
 * Evita que el programa se abra dos veces sobre la misma base de datos
 * (cada ventana tendría su propia copia en memoria y quedarían desincronizadas).
 * El bloqueo lo libera el sistema operativo automáticamente al cerrar el programa,
 * incluso si se cierra de golpe.
 */
public final class InstanciaUnica {
    private static FileChannel canal;
    private static FileLock bloqueo;

    private InstanciaUnica() {}

    /** @return false solamente si se comprobó que ya hay otra ventana abierta. */
    public static synchronized boolean adquirir() {
        try {
            Files.createDirectories(AppPaths.carpetaBase());
            canal = FileChannel.open(AppPaths.archivoBloqueo(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            bloqueo = canal.tryLock();
            if (bloqueo == null) {
                canal.close();
                return false;
            }
            return true;
        } catch (OverlappingFileLockException e) {
            return false;
        } catch (IOException | RuntimeException e) {
            // Si no se puede usar el mecanismo de bloqueo, no se impide abrir el programa.
            Log.warn("No se pudo verificar si el programa ya está abierto: " + e);
            return true;
        }
    }
}
