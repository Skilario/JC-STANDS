package com.mycompany.jcstands;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

/**
 * Utilidades para trabajar con dinero (BigDecimal) y validar el valor por hora.
 */
public final class Dinero {
    /** Tope de sanidad para el valor por hora (evita errores de tipeo absurdos). */
    public static final BigDecimal VALOR_HORA_MAXIMO = new BigDecimal("10000000.00");

    private static final BigDecimal SEGUNDOS_POR_HORA = BigDecimal.valueOf(3600);
    private static final int ESCALA_CALCULO = 6;

    private Dinero() {}

    /**
     * Dinero ganado por una duración: valorHora * segundos / 3600.
     * Se conserva precisión de sobra (6 decimales); el redondeo a centavos se hace
     * recién al mostrar o exportar, sobre el total.
     */
    public static BigDecimal porDuracion(BigDecimal valorHora, Duration duracion) {
        BigDecimal segundos = BigDecimal.valueOf(duracion.toSeconds());
        return valorHora.multiply(segundos).divide(SEGUNDOS_POR_HORA, ESCALA_CALCULO, RoundingMode.HALF_UP);
    }

    public static BigDecimal aCentavos(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Interpreta el valor por hora escrito por el usuario. Acepta "5000", "5000,50", "5000.50",
     * "$ 5000" y "5.000,50". Rechaza vacío, texto, NaN, Infinity, notación científica, cero,
     * negativos, valores gigantes y formas ambiguas como "5.000" (¿cinco o cinco mil?).
     */
    public static BigDecimal parsearValorHora(String texto) throws AsistenciaException {
        String t = texto == null ? "" : texto.strip();
        if (t.startsWith("$")) t = t.substring(1).strip();
        if (t.isEmpty()) throw new AsistenciaException("Ingrese el valor por hora.");
        if (t.length() > 20 || !t.matches("[0-9.,]+")) throw new AsistenciaException("Ingrese un valor válido.");

        String normalizado = normalizar(t);
        if (normalizado == null) throw new AsistenciaException("Ingrese un valor válido.");
        return validarValorHora(new BigDecimal(normalizado));
    }

    /** Valida un valor por hora y lo devuelve con 2 decimales. */
    public static BigDecimal validarValorHora(BigDecimal valor) throws AsistenciaException {
        if (valor == null) throw new AsistenciaException("Ingrese el valor por hora.");
        BigDecimal v = valor.setScale(2, RoundingMode.HALF_UP);
        if (v.signum() <= 0) throw new AsistenciaException("El valor por hora debe ser mayor a 0.");
        if (v.compareTo(VALOR_HORA_MAXIMO) > 0) {
            throw new AsistenciaException("El valor por hora es demasiado grande (máximo $ "
                    + Formato.dinero(VALOR_HORA_MAXIMO) + ").");
        }
        return v;
    }

    /** Devuelve el número con punto decimal, o null si el formato no es válido. */
    private static String normalizar(String t) throws AsistenciaException {
        boolean punto = t.indexOf('.') >= 0;
        boolean coma = t.indexOf(',') >= 0;

        if (punto && coma) {
            if (t.lastIndexOf(',') > t.lastIndexOf('.')) {           // 1.234,56
                return t.matches("\\d{1,3}(\\.\\d{3})+,\\d+") ? t.replace(".", "").replace(',', '.') : null;
            }
            return t.matches("\\d{1,3}(,\\d{3})+\\.\\d+") ? t.replace(",", "") : null;   // 1,234.56
        }
        if (coma) {
            if (!t.matches("\\d+,\\d+")) return null;
            if (t.matches("\\d{1,3},\\d{3}")) throw ambiguo(t);
            return t.replace(',', '.');
        }
        if (punto) {
            if (t.matches("\\d+\\.\\d+")) {
                if (t.matches("\\d{1,3}\\.\\d{3}")) throw ambiguo(t);
                return t;
            }
            return t.matches("\\d{1,3}(\\.\\d{3}){2,}") ? t.replace(".", "") : null;     // 1.500.000
        }
        return t;
    }

    private static AsistenciaException ambiguo(String t) {
        return new AsistenciaException("El valor \"" + t + "\" es ambiguo. Escríbalo sin puntos de miles "
                + "(por ejemplo 5000) y use coma para los decimales (por ejemplo 5000,50).");
    }
}
