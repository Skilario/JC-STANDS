package com.mycompany.jcstands;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

/**
 * Única fuente de fecha/hora de la aplicación. En producción usa la hora real del sistema.
 * Para pruebas se puede reemplazar el reloj (usarReloj) sin tocar la fecha de Windows.
 */
public final class ClockApp {
    private static volatile Clock reloj = Clock.systemDefaultZone();

    private ClockApp() {}

    public static LocalDateTime ahora() { return LocalDateTime.now(reloj); }
    public static LocalDate hoy() { return LocalDate.now(reloj); }
    public static YearMonth mesActual() { return YearMonth.now(reloj); }

    /** Solo para pruebas: reemplaza el reloj (por ejemplo, Clock.fixed(...)). */
    public static void usarReloj(Clock nuevo) { reloj = nuevo == null ? Clock.systemDefaultZone() : nuevo; }

    /** Vuelve a la hora real del sistema. */
    public static void usarRelojReal() { reloj = Clock.systemDefaultZone(); }
}
