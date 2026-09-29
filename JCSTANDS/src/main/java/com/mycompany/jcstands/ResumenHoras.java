package com.mycompany.jcstands;

import java.math.BigDecimal;
import java.time.Duration;

public class ResumenHoras {
    private final Duration horasDia, horasSemana, horasMes, horasTotal;
    private final BigDecimal dineroDia, dineroSemana, dineroMes, dineroTotal;

    public ResumenHoras(Duration horasDia, Duration horasSemana, Duration horasMes, Duration horasTotal,
                         BigDecimal dineroDia, BigDecimal dineroSemana, BigDecimal dineroMes, BigDecimal dineroTotal) {
        this.horasDia = horasDia;
        this.horasSemana = horasSemana;
        this.horasMes = horasMes;
        this.horasTotal = horasTotal;
        this.dineroDia = dineroDia;
        this.dineroSemana = dineroSemana;
        this.dineroMes = dineroMes;
        this.dineroTotal = dineroTotal;
    }

    public Duration getHorasDia(){ return horasDia; }
    public Duration getHorasSemana(){ return horasSemana; }
    public Duration getHorasMes(){ return horasMes; }
    public Duration getHorasTotal(){ return horasTotal; }
    public BigDecimal getDineroDia(){ return dineroDia; }
    public BigDecimal getDineroSemana(){ return dineroSemana; }
    public BigDecimal getDineroMes(){ return dineroMes; }
    public BigDecimal getDineroTotal(){ return dineroTotal; }
}
