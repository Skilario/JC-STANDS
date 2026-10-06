package com.mycompany.jcstands;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Utilidades de formato de números para mostrar en la interfaz.
 */
public final class Formato {
    private static final Locale LOCALE_AR = new Locale("es", "AR");

    private Formato() {}

    /**
     * Formatea un monto de dinero con separador de miles (punto) y dos
     * decimales (coma), estilo argentino. Ejemplo: 208502.08 -> "208.502,08"
     */
    public static String dinero(BigDecimal valor) {
        NumberFormat nf = NumberFormat.getNumberInstance(LOCALE_AR);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        nf.setRoundingMode(java.math.RoundingMode.HALF_UP);
        return nf.format(valor);
    }

    public static String dinero(double valor) {
        return dinero(BigDecimal.valueOf(valor));
    }
}
