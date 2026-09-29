package com.mycompany.jcstands;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class PanelAltaEmpleado extends JPanel {
    private static final Color COLOR_OK = new Color(0, 110, 0);
    private static final Color COLOR_ERROR = new Color(170, 0, 0);

    private final JLabel mensaje = new JLabel(" ", SwingConstants.CENTER);
    private final AntiRebote antiRebote = new AntiRebote(600);

    public PanelAltaEmpleado(JFrame ventana, ControlAsistencia control, Runnable alActualizar) {
        setBorder(new TitledBorder("Agregar empleado"));
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        JTextField campoNombre = new JTextField(22);
        campoNombre.setPreferredSize(new Dimension(campoNombre.getPreferredSize().width, 28));
        JButton boton = new JButton("Agregar Empleado");
        boton.setPreferredSize(new Dimension(boton.getPreferredSize().width + 16, 30));

        JPanel fila = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4));
        fila.add(new JLabel("Nombre:"));
        fila.add(campoNombre);
        fila.add(boton);
        fila.setAlignmentX(Component.CENTER_ALIGNMENT);
        add(fila);

        mensaje.setBorder(BorderFactory.createEmptyBorder(0, 8, 2, 8));
        mensaje.setAlignmentX(Component.CENTER_ALIGNMENT);
        add(mensaje);

        // Enter en el campo de nombre equivale a apretar el botón.
        campoNombre.addActionListener(e -> boton.doClick());

        boton.addActionListener(e -> {
            if (!antiRebote.permitir()) return;   // doble clic accidental
            String nombre = ControlAsistencia.normalizarNombre(campoNombre.getText());
            if (nombre.isEmpty()) {
                mostrarError("Ingrese un nombre");
                return;
            }
            if (nombre.matches(".*\\d.*")) {
                mostrarError("El nombre no puede contener números");
                return;
            }

            if (control.existeNombreActivo(nombre)) {
                mostrarError("Ya existe un empleado con ese nombre");
                return;
            }

            String textoValor = JOptionPane.showInputDialog(
                    ventana, "Ingrese el valor por hora de " + nombre);
            if (textoValor == null) return;

            try {
                java.math.BigDecimal valorHora = Dinero.parsearValorHora(textoValor);
                control.agregarEmpleado(nombre, valorHora);
                alActualizar.run();
                mostrarOk(nombre + " cargado correctamente");
                campoNombre.setText("");
                campoNombre.requestFocusInWindow();
            } catch (AsistenciaException ex) {
                mostrarError(ex.getMessage());
            }
        });
    }

    private void mostrarOk(String texto) {
        mensaje.setForeground(COLOR_OK);
        mensaje.setText(texto);
    }

    private void mostrarError(String texto) {
        mensaje.setForeground(COLOR_ERROR);
        mensaje.setText(texto);
    }

    /** Evita que el panel se estire hacia abajo cuando la ventana es más alta que el contenido. */
    @Override
    public Dimension getMaximumSize() {
        return new Dimension(super.getMaximumSize().width, getPreferredSize().height);
    }
}
