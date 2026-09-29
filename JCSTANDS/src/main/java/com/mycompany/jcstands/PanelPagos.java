package com.mycompany.jcstands;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.*;
import java.util.Locale;
import java.time.format.DateTimeFormatter;

public class PanelPagos extends JPanel {
    private final ControlAsistencia control;

    public PanelPagos(ControlAsistencia control) {
        this.control = control;
        setBorder(BorderFactory.createTitledBorder("Pagos a realizar"));
        setLayout(new FlowLayout(FlowLayout.LEFT, 10, 12));
        setPreferredSize(new Dimension(1000, 75));

        JButton historial = new JButton("Historial de pagos");
        historial.setPreferredSize(new Dimension(180, 32));
        add(historial);

        historial.addActionListener(e -> mostrarSelectorPeriodos());
    }

    public void refrescar() {
        // Los pagos se consultan al abrir el historial, así la pantalla principal queda limpia.
    }

    private void mostrarSelectorPeriodos() {
        JDialog d = new JDialog(SwingUtilities.getWindowAncestor(this), "Historial de pagos", Dialog.ModalityType.APPLICATION_MODAL);
        d.setLayout(new BorderLayout(10, 10));

        JLabel titulo = new JLabel("Seleccione qué pagos desea consultar:", SwingConstants.CENTER);
        titulo.setBorder(BorderFactory.createEmptyBorder(12, 10, 4, 10));
        d.add(titulo, BorderLayout.NORTH);

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 12));
        JButton dia = new JButton("Pago por día");
        JButton semana = new JButton("Pago por semana");
        JButton mes = new JButton("Pago por mes");
        botones.add(dia);
        botones.add(semana);
        botones.add(mes);
        d.add(botones, BorderLayout.CENTER);

        JButton cerrar = new JButton("Cerrar");
        cerrar.addActionListener(e -> d.dispose());
        JPanel pie = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        pie.add(cerrar);
        d.add(pie, BorderLayout.SOUTH);

        dia.addActionListener(e -> mostrarPagosDelDia(d));
        semana.addActionListener(e -> mostrarPagosDeLaSemana(d));
        mes.addActionListener(e -> mostrarPagosDelMes(d));

        d.setSize(520, 190);
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }

    private void mostrarPagosDelDia(Window padre) {
        LocalDate hoy = ClockApp.hoy();
        DateTimeFormatter fecha = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String diaSemana = hoy.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, new Locale("es", "AR"));
        DefaultTableModel m = modelo("Empleado", "Horas del día", "Pago del día");
        for (Empleado e : control.getTodosLosEmpleados()) {
            ResumenHoras r = control.calcularResumen(e);
            if (!r.getHorasDia().isZero() || r.getDineroDia().signum() != 0) {
                m.addRow(new Object[]{e.getNombre(), horas(r.getHorasDia()), dinero(r.getDineroDia())});
            }
        }
        mostrarTabla(padre, "Pago por día", "Día: " + diaSemana + " " + fecha.format(hoy), m);
    }

    private void mostrarPagosDeLaSemana(Window padre) {
        LocalDate hoy = ClockApp.hoy();
        LocalDate lunes = hoy.with(DayOfWeek.MONDAY);
        LocalDate domingo = lunes.plusDays(6);
        DefaultTableModel m = modelo("Empleado", "Horas de la semana", "Pago de la semana");
        for (Empleado e : control.getTodosLosEmpleados()) {
            ResumenHoras r = control.calcularResumenSemana(e, lunes);
            if (!r.getHorasSemana().isZero() || r.getDineroSemana().signum() != 0) {
                m.addRow(new Object[]{e.getNombre(), horas(r.getHorasSemana()), dinero(r.getDineroSemana())});
            }
        }
        mostrarTabla(padre, "Pago por semana", "Semana: " + lunes.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + " al " + domingo.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), m);
    }

    private void mostrarPagosDelMes(Window padre) {
        YearMonth mes = ClockApp.mesActual();
        DefaultTableModel m = modelo("Empleado", "Horas del mes", "Pago del mes");
        for (Empleado e : control.getTodosLosEmpleados()) {
            ResumenHoras r = control.calcularResumenMes(e, mes);
            if (!r.getHorasMes().isZero() || r.getDineroMes().signum() != 0) {
                m.addRow(new Object[]{e.getNombre(), horas(r.getHorasMes()), dinero(r.getDineroMes())});
            }
        }
                String nombreMes = mes.getMonth().getDisplayName(java.time.format.TextStyle.FULL, new Locale("es", "AR"));
        mostrarTabla(padre, "Pago por mes", "Mes: " + nombreMes + " " + mes.getYear(), m);
    }

    private DefaultTableModel modelo(String... columnas) {
        return new DefaultTableModel(columnas, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
    }

    private void mostrarTabla(Window padre, String titulo, String periodo, DefaultTableModel modelo) {
        JDialog d = new JDialog(padre, titulo, Dialog.ModalityType.APPLICATION_MODAL);
        d.setLayout(new BorderLayout(8, 8));

        JPanel encabezado = new JPanel(new BorderLayout());
        JLabel periodoLabel = new JLabel(periodo, SwingConstants.CENTER);
        periodoLabel.setFont(periodoLabel.getFont().deriveFont(Font.BOLD, 14f));
        periodoLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 8, 10));
        encabezado.add(periodoLabel, BorderLayout.CENTER);
        d.add(encabezado, BorderLayout.NORTH);

        JTable tabla = new JTable(modelo);
        tabla.setRowHeight(30);
        tabla.setFont(tabla.getFont().deriveFont(14f));
        tabla.getTableHeader().setFont(tabla.getTableHeader().getFont().deriveFont(Font.BOLD, 14f));
        d.add(new JScrollPane(tabla), BorderLayout.CENTER);
        JButton cerrar = new JButton("Cerrar");
        cerrar.addActionListener(e -> d.dispose());
        JPanel pie = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        pie.add(cerrar);
        d.add(pie, BorderLayout.SOUTH);
        d.setSize(650, 420);
        d.setLocationRelativeTo(padre);
        d.setVisible(true);
    }

    private String horas(Duration d) {
        long minutos = d.toMinutes();
        return (minutos / 60) + " h " + (minutos % 60) + " min";
    }

    private String dinero(java.math.BigDecimal v) {
        return "$ " + Formato.dinero(v);
    }
}
