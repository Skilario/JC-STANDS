package com.mycompany.jcstands;

/**
 * Ignora clics repetidos sobre un mismo botón dentro de una ventana corta de tiempo
 * (doble clic accidental). Es solo una primera barrera: la protección real contra
 * duplicados está en la lógica y en la base de datos. Se usa desde el hilo de Swing (EDT).
 */
public final class AntiRebote {
    private final long ventanaNanos;
    private long ultimo;
    private boolean usado = false;

    public AntiRebote(long milisegundos) {
        this.ventanaNanos = milisegundos * 1_000_000L;
    }

    /** @return true si la acción debe ejecutarse; false si es un clic repetido y hay que ignorarlo. */
    public boolean permitir() {
        long ahora = System.nanoTime();
        if (usado && ahora - ultimo < ventanaNanos) return false;
        usado = true;
        ultimo = ahora;
        return true;
    }
}
