package com.mycompany.jcstands;

import java.math.BigDecimal;

public class Empleado {
    private final int id;
    private final String nombre;
    private BigDecimal valorHora;

    public Empleado(int id, String nombre, BigDecimal valorHora) {
        this.id = id;
        this.nombre = nombre;
        this.valorHora = valorHora;
    }

    public int getId() { return id; }
    public String getNombre() { return nombre; }
    public BigDecimal getValorHora() { return valorHora; }

    public void setValorHora(BigDecimal valorHora) {
        this.valorHora = valorHora;
    }

    @Override
    public String toString() {
        return id + " - " + nombre;
    }
}
