package com.mycompany.jcstands;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

public class Jornada {

    private final int id;
    private final Empleado empleado;
    private LocalDateTime entrada;
    private final BigDecimal valorHora;
    private LocalDateTime salida;

    public Jornada(int id, Empleado empleado, LocalDateTime entrada, BigDecimal valorHora) {
        this.id = id;
        this.empleado = empleado;
        this.entrada = entrada;
        this.valorHora = valorHora;
    }

    public Jornada(Empleado empleado, LocalDateTime entrada) {
        this(0, empleado, entrada, empleado.getValorHora());
    }

    public int getId() { return id; }
    public Empleado getEmpleado() { return empleado; }
    public LocalDateTime getEntrada() { return entrada; }
    public LocalDateTime getSalida() { return salida; }
    public void setEntrada(LocalDateTime entrada) { this.entrada = entrada; }
    public BigDecimal getValorHora() { return valorHora; }

    public void setSalida(LocalDateTime salida) { this.salida = salida; }
    public boolean estaCerrada() { return salida != null; }

    public Duration getDuracion() {
        return getDuracionHasta(ClockApp.ahora());
    }

    public Duration getDuracionHasta(LocalDateTime ahora) {
        LocalDateTime fin = estaCerrada() ? salida : ahora;
        if (fin == null || !fin.isAfter(entrada)) return Duration.ZERO;
        Duration duracion = Duration.between(entrada, fin);
        return duracion;
    }

    public double getHorasTrabajadas() {
        return getDuracion().toSeconds() / 3600.0;
    }

    public double getHorasTrabajadasHasta(LocalDateTime ahora) {
        return getDuracionHasta(ahora).toSeconds() / 3600.0;
    }

    /** Dinero ganado en esta jornada, con el valor por hora que tenía cuando se registró la entrada. */
    public BigDecimal getDineroGenerado() {
        return getDineroGeneradoHasta(ClockApp.ahora());
    }

    public BigDecimal getDineroGeneradoHasta(LocalDateTime ahora) {
        return Dinero.porDuracion(valorHora, getDuracionHasta(ahora));
    }
}
