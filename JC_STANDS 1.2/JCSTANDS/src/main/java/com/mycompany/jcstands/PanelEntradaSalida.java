package com.mycompany.jcstands;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class PanelEntradaSalida extends JPanel {
    private final DateTimeFormatter formato = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private final JComboBox<Empleado> comboEmpleado = new JComboBox<>();
    private boolean actualizandoCombo = false;
    private final Consumer<Empleado> alSeleccionarEmpleado;

    /**
     * @param empleadosMarcados empleados tildados en la columna SELECCIONAR de la tabla;
     *                          "Registrar Salida a Todos" actúa solamente sobre ellos.
     * @param alDesmarcar       se llama con los empleados cuya selección ya se usó, para que
     *                          la tabla les saque el tilde.
     */
    public PanelEntradaSalida(ControlAsistencia control, Runnable alActualizar, Consumer<Empleado> alSeleccionarEmpleado,
            Supplier<List<Empleado>> empleadosMarcados, Consumer<List<Empleado>> alDesmarcar) {
        this.alSeleccionarEmpleado = alSeleccionarEmpleado;
        setBorder(new TitledBorder("Entrada / Salida (carga manual)"));
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        // --- Fila 1: empleado y fecha/hora ---
        JPanel filaDatos = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 5));
        filaDatos.add(new JLabel("Empleado:"));
        comboEmpleado.setPreferredSize(new Dimension(380, 28));
        comboEmpleado.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Empleado empleado) {
                    Jornada ultima = control.obtenerUltimaJornada(empleado);
                    String estado;
                    if (ultima == null) {
                        estado = "sin registros";
                    } else if (!ultima.estaCerrada()) {
                        estado = "trabajando desde " + ultima.getEntrada().format(formato);
                    } else {
                        estado = "última salida " + ultima.getSalida().format(formato);
                    }
                    setText(empleado.getId() + " - " + empleado.getNombre() + "  (" + estado + ")");
                }
                return this;
            }
        });
        filaDatos.add(comboEmpleado);

        filaDatos.setAlignmentX(Component.CENTER_ALIGNMENT);
        add(filaDatos);

        // --- Fila 2: botones (Borrar Empleado va siempre último) ---
        JButton botonEntrada = new JButton("Registrar Entrada manual");
        JButton botonEntradaTodos = new JButton("Registrar Entrada a Todos");
        JButton botonSalida = new JButton("Registrar Salida manual");
        JButton botonSalidaTodos = new JButton("Registrar Salida a Todos");
        JButton botonBorrar = new JButton("Borrar Empleado");
        botonEntradaTodos.setToolTipText("Registra la entrada, a la hora actual, de los empleados tildados en la "
                + "tabla de abajo (columna SELECCIONAR).");
        botonSalidaTodos.setToolTipText("Registra la salida, a la hora actual, de los empleados tildados en la "
                + "tabla de abajo (columna SELECCIONAR) que estén trabajando.");
        botonEntrada.setToolTipText("Registra la entrada del empleado elegido a la hora actual. "
                + "Si hace falta, corrija el horario haciendo doble clic en la tabla.");
        botonSalida.setToolTipText("Registra la salida del empleado elegido a la hora actual. "
                + "Si hace falta, corrija el horario haciendo doble clic en la tabla.");
        pintar(botonEntrada, new Color(0x16A34A));
        pintar(botonEntradaTodos, new Color(0x16A34A));
        pintar(botonSalida, new Color(0xDC2626));
        pintar(botonSalidaTodos, new Color(0xDC2626));
        botonBorrar.setForeground(new Color(0xB91C1C));
        igualarTamanios(botonEntrada, botonEntradaTodos, botonSalida, botonSalidaTodos, botonBorrar);

        JPanel filaBotones = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 2));
        filaBotones.add(botonEntrada);
        filaBotones.add(botonEntradaTodos);
        filaBotones.add(botonSalida);
        filaBotones.add(botonSalidaTodos);
        filaBotones.add(botonBorrar);
        filaBotones.setAlignmentX(Component.CENTER_ALIGNMENT);
        add(filaBotones);

        JLabel mensaje = new JLabel(" ", SwingConstants.CENTER);
        mensaje.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        mensaje.setAlignmentX(Component.CENTER_ALIGNMENT);
        add(mensaje);

        AntiRebote antiEntrada = new AntiRebote(600), antiSalida = new AntiRebote(600),
                antiEntradaTodos = new AntiRebote(600), antiSalidaTodos = new AntiRebote(600), antiBorrar = new AntiRebote(600);

        comboEmpleado.addActionListener(e -> {
            if (actualizandoCombo) return;
            Empleado empleado = (Empleado) comboEmpleado.getSelectedItem();
            if (empleado != null) alSeleccionarEmpleado.accept(empleado);
        });

        refrescarEmpleados(control);

        botonEntrada.addActionListener(e -> {
            if (!antiEntrada.permitir()) return;
            Empleado empleado = (Empleado) comboEmpleado.getSelectedItem();
            if (empleado == null) {
                mensaje.setText("Seleccione un empleado.");
                return;
            }
            try {
                Jornada jornada = control.registrarEntradaManual(empleado, ClockApp.ahora().withNano(0));
                mensaje.setText(empleado.getNombre() + " entró: " + jornada.getEntrada().format(formato));
                alActualizar.run();
            } catch (AsistenciaException ex) {
                mensaje.setText(ex.getMessage());
                alActualizar.run();
            }
        });

        botonSalida.addActionListener(e -> {
            if (!antiSalida.permitir()) return;
            Empleado empleado = (Empleado) comboEmpleado.getSelectedItem();
            if (empleado == null) {
                mensaje.setText("Seleccione un empleado.");
                return;
            }
            try {
                Jornada jornada = control.registrarSalidaManual(empleado, ClockApp.ahora().withNano(0));
                mensaje.setText(empleado.getNombre() + " salió: " + jornada.getSalida().format(formato));
                alActualizar.run();
            } catch (AsistenciaException ex) {
                mensaje.setText(ex.getMessage());
                alActualizar.run();
            }
        });

        botonEntradaTodos.addActionListener(e -> {
            if (!antiEntradaTodos.permitir()) return;
            List<Empleado> marcados = empleadosMarcados.get();
            if (marcados.isEmpty()) {
                mensaje.setText("Tilde en la tabla a los empleados a los que quiere registrar la entrada.");
                return;
            }
            try {
                List<Jornada> nuevas = control.registrarEntradaA(marcados);
                mensaje.setText(nuevas.isEmpty()
                        ? "Los empleados seleccionados ya están trabajando."
                        : "Entraron " + nuevas.size() + " empleado(s).");
                // La selección ya se usó: se limpian los tildes.
                alDesmarcar.accept(marcados);
                alActualizar.run();
            } catch (AsistenciaException ex) {
                // Todo o nada: no se registró ninguna entrada. Los tildes quedan para poder reintentar.
                mensaje.setText("No se registró ninguna entrada. " + ex.getMessage());
                alActualizar.run();
            }
        });

        botonSalidaTodos.addActionListener(e -> {
            if (!antiSalidaTodos.permitir()) return;
            List<Empleado> marcados = empleadosMarcados.get();
            if (marcados.isEmpty()) {
                mensaje.setText("Tilde en la tabla a los empleados a los que quiere registrar la salida.");
                return;
            }
            List<Empleado> seleccionados = new ArrayList<>();
            for (Empleado empleado : marcados) {
                if (control.estaTrabajando(empleado)) seleccionados.add(empleado);
            }
            if (seleccionados.isEmpty()) {
                mensaje.setText("Ninguno de los empleados seleccionados está trabajando.");
                return;
            }
            LocalDateTime fechaHora = ClockApp.ahora().withNano(0);
            try {
                // Todo o nada: una sola transacción para todos los seleccionados.
                List<Jornada> cerradas = control.registrarSalidaA(seleccionados, fechaHora);
                mensaje.setText(cerradas.size() + " empleado(s) marcaron salida.");
                // La selección ya se usó: se limpian los tildes para no arrastrarlos al día siguiente.
                alDesmarcar.accept(new ArrayList<>(marcados));
                alActualizar.run();
            } catch (AsistenciaException ex) {
                // Si falló, no se cerró ninguna jornada y quedan tildados para poder reintentar.
                mensaje.setText("No se registró ninguna salida. " + ex.getMessage());
                alActualizar.run();
                JOptionPane.showMessageDialog(this, ex.getMessage(), "No se pudieron registrar las salidas", JOptionPane.WARNING_MESSAGE);
            }
        });

        botonBorrar.addActionListener(e -> {
            if (!antiBorrar.permitir()) return;
            Empleado empleado = (Empleado) comboEmpleado.getSelectedItem();
            if (empleado == null) {
                mensaje.setText("Seleccione un empleado.");
                return;
            }
            int respuesta = JOptionPane.showConfirmDialog(this,
                    "¿Seguro que quiere borrar a " + empleado.getNombre() + "?",
                    "Confirmar borrado", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (respuesta != JOptionPane.YES_OPTION) return;
            try {
                control.eliminarEmpleado(empleado);
                refrescarEmpleados(control);
                alActualizar.run();
                mensaje.setText("Empleado eliminado.");
            } catch (AsistenciaException ex) {
                mensaje.setText(ex.getMessage());
                alActualizar.run();
                JOptionPane.showMessageDialog(this, ex.getMessage(), "No se puede borrar", JOptionPane.WARNING_MESSAGE);
            }
        });
    }

    /** Deja a todos los botones del mismo ancho y alto, para que la fila se vea pareja. */
    /** Botón de color con texto blanco, para distinguir entrada (verde) y salida (rojo). */
    private static void pintar(JButton boton, Color color) {
        boton.setBackground(color);
        boton.setForeground(Color.WHITE);
        boton.setFont(boton.getFont().deriveFont(Font.BOLD));
    }

    private static void igualarTamanios(JButton... botones) {
        int ancho = 0;
        for (JButton boton : botones) ancho = Math.max(ancho, boton.getPreferredSize().width + 16);
        for (JButton boton : botones) boton.setPreferredSize(new Dimension(ancho, 36));
    }

    /** Evita que el panel se estire hacia abajo cuando la ventana es más alta que el contenido. */
    @Override
    public Dimension getMaximumSize() {
        return new Dimension(super.getMaximumSize().width, getPreferredSize().height);
    }

    public void refrescarEmpleados(ControlAsistencia control) {
        Empleado seleccionado = (Empleado) comboEmpleado.getSelectedItem();
        int idSeleccionado = seleccionado != null ? seleccionado.getId() : -1;

        actualizandoCombo = true;
        try {
            DefaultComboBoxModel<Empleado> modelo = new DefaultComboBoxModel<>();
            for (Empleado empleado : control.getEmpleados()) modelo.addElement(empleado);
            comboEmpleado.setModel(modelo);

            if (idSeleccionado >= 0) {
                for (int i = 0; i < comboEmpleado.getItemCount(); i++) {
                    if (comboEmpleado.getItemAt(i).getId() == idSeleccionado) {
                        comboEmpleado.setSelectedIndex(i);
                        break;
                    }
                }
            }
        } finally {
            actualizandoCombo = false;
        }

        comboEmpleado.repaint();
        Empleado actual = (Empleado) comboEmpleado.getSelectedItem();
        if (actual != null) alSeleccionarEmpleado.accept(actual);
    }

    public void seleccionarEmpleado(Empleado empleado) {
        if (empleado == null) return;
        for (int i = 0; i < comboEmpleado.getItemCount(); i++) {
            Empleado item = comboEmpleado.getItemAt(i);
            if (item.getId() == empleado.getId()) {
                actualizandoCombo = true;
                try {
                    comboEmpleado.setSelectedIndex(i);
                } finally {
                    actualizandoCombo = false;
                }
                alSeleccionarEmpleado.accept(item);
                return;
            }
        }
    }
}
