package com.mycompany.jcstands;

import java.math.BigDecimal;
import java.time.Duration;

public class ResumenEmpleadoMensual {
    private final Empleado empleado;
    private final int diasTrabajados;
    private final Duration horasTotales;
    private final BigDecimal dineroTotal;

    public ResumenEmpleadoMensual(Empleado empleado, int diasTrabajados, Duration horasTotales, BigDecimal dineroTotal) {
        this.empleado = empleado;
        this.diasTrabajados = diasTrabajados;
        this.horasTotales = horasTotales;
        this.dineroTotal = dineroTotal;
    }

    public Empleado getEmpleado(){ return empleado; }
    public int getDiasTrabajados(){ return diasTrabajados; }
    public Duration getHorasTotales(){ return horasTotales; }
    public BigDecimal getDineroTotal(){ return dineroTotal; }
}
