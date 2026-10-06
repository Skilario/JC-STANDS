package com.mycompany.jcstands;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.time.YearMonth;
import java.util.List;

public class PanelExportarReporte extends JPanel {
    private final JFrame ventana;
    private final ControlAsistencia control;
    private final JButton botonMes = new JButton("Exportar CSV del mes");
    private final JButton botonHistorial = new JButton("Exportar historial completo CSV");
    private final JLabel mensaje = new JLabel("Los CSV se generan ordenados por empleado (agrupados) y por fecha.");
    private final AntiRebote antiRebote = new AntiRebote(600);

    public PanelExportarReporte(JFrame ventana, ControlAsistencia control) {
        this.ventana = ventana;
        this.control = control;
        setBorder(BorderFactory.createTitledBorder("Exportación de reportes CSV"));
        setPreferredSize(new Dimension(1000, 70));
        add(botonMes); add(botonHistorial); add(mensaje);

        botonMes.addActionListener(e -> {
            if (!antiRebote.permitir()) return;
            YearMonth mes = ClockApp.mesActual();
            File destino = elegirArchivo("reporte_pagos_" + mes + ".csv");
            if (destino == null) return;
            List<Jornada> copia = control.copiaJornadas();
            exportar(destino, "CSV mensual guardado.", () -> control.exportarReporteMensual(destino, mes, copia));
        });

        botonHistorial.addActionListener(e -> {
            if (!antiRebote.permitir()) return;
            File destino = elegirArchivo("historial_completo.csv");
            if (destino == null) return;
            List<Jornada> copia = control.copiaJornadas();
            exportar(destino, "CSV completo guardado.", () -> control.exportarHistorialCompleto(destino, copia));
        });
    }

    private File elegirArchivo(String nombreSugerido) {
        JFileChooser s = new JFileChooser();
        s.setSelectedFile(new File(nombreSugerido));
        return s.showSaveDialog(ventana) == JFileChooser.APPROVE_OPTION ? s.getSelectedFile() : null;
    }

    private interface Escritura { void ejecutar() throws IOException; }

    /** Genera y escribe el CSV fuera del hilo de Swing; la interfaz solo se toca en done() (EDT). */
    private void exportar(File destino, String textoOk, Escritura escritura) {
        botonMes.setEnabled(false);
        botonHistorial.setEnabled(false);
        mensaje.setText("Guardando CSV...");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                escritura.ejecutar();
                return null;
            }

            @Override
            protected void done() {
                botonMes.setEnabled(true);
                botonHistorial.setEnabled(true);
                try {
                    get();
                    Log.info("CSV exportado: " + destino);
                    mensaje.setText(textoOk);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    mensaje.setText("La exportación fue interrumpida.");
                } catch (java.util.concurrent.ExecutionException ex) {
                    Log.error("No se pudo exportar el CSV a " + destino, ex.getCause());
                    mensaje.setText("No se pudo guardar el CSV.");
                    JOptionPane.showMessageDialog(ventana,
                            "No se pudo guardar el archivo:\n" + destino
                                    + "\n\nVerifique que no esté abierto en Excel u otro programa, "
                                    + "que la carpeta permita escribir y que haya espacio en el disco. "
                                    + "Después intente nuevamente.",
                            "No se pudo exportar", JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }
}
