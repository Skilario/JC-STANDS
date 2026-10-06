package com.mycompany.jcstands;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.time.Duration;

public class PanelResumenHoras extends JPanel {
    private final JLabel tituloEmpleado = new JLabel("Seleccione un empleado");
    private final JLabel horasDia = new JLabel();
    private final JLabel horasSemana = new JLabel();
    private final JLabel horasMes = new JLabel();

    public PanelResumenHoras() {
        setBorder(new TitledBorder("Resumen de horas"));
        setLayout(new GridLayout(4, 1, 18, 6));

        tituloEmpleado.setFont(tituloEmpleado.getFont().deriveFont(Font.BOLD, 16f));
        horasDia.setFont(horasDia.getFont().deriveFont(15f));
        horasSemana.setFont(horasSemana.getFont().deriveFont(15f));
        horasMes.setFont(horasMes.getFont().deriveFont(15f));
        add(tituloEmpleado);
        add(horasDia);
        add(horasSemana);
        add(horasMes);

        setVisible(false);
    }

    public void actualizar(Empleado empleado, ResumenHoras r) {
        if (empleado == null || r == null) {
            setVisible(false);
            return;
        }
        tituloEmpleado.setText("Empleado: " + empleado.getNombre());
        horasDia.setText("Hoy: " + formatearDuracion(r.getHorasDia()));
        horasSemana.setText("Semana: " + formatearDuracion(r.getHorasSemana()));
        horasMes.setText("Mes: " + formatearDuracion(r.getHorasMes()));
        setVisible(true);
    }

    private String formatearDuracion(Duration d) {
        long minutos = d.toMinutes();
        return (minutos / 60) + " h " + (minutos % 60) + " min";
    }
}
