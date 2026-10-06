package com.mycompany.jcstands;

import java.sql.SQLException;
import java.util.Locale;

public class AsistenciaException extends Exception {
    public AsistenciaException(String mensaje) { super(mensaje); }

    public AsistenciaException(String mensaje, Throwable causa) { super(mensaje, causa); }

    /**
     * Convierte un error de base de datos en un mensaje comprensible para el usuario.
     * El detalle técnico (SQLException completa) se guarda en el log.
     *
     * @param accion frase en infinitivo que completa "No se pudo ...", por ejemplo "guardar la entrada"
     */
    public static AsistenciaException porErrorDeBase(String accion, SQLException e) {
        Log.error("Error de base de datos: no se pudo " + accion, e);
        return new AsistenciaException("No se pudo " + accion + ". " + describir(e), e);
    }

    /** true si el error es una violación de un índice/restricción UNIQUE. */
    public static boolean esViolacionUnica(SQLException e) {
        String m = e.getMessage() == null ? "" : e.getMessage().toUpperCase(Locale.ROOT);
        return m.contains("UNIQUE CONSTRAINT");
    }

    private static String describir(SQLException e) {
        String m = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        if (m.contains("locked") || m.contains("busy")) {
            return "La base de datos está ocupada. Intente de nuevo en unos segundos.";
        }
        if (m.contains("readonly") || m.contains("read-only")) {
            return "No hay permiso para escribir en la base de datos.";
        }
        if (m.contains("disk is full") || m.contains("database or disk is full")) {
            return "No hay espacio suficiente en el disco.";
        }
        return "Intente nuevamente. Si el problema continúa, avise a soporte "
                + "(el detalle quedó registrado en el archivo de log).";
    }
}
