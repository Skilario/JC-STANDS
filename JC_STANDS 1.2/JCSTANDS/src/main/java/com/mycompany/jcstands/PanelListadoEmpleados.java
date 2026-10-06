package com.mycompany.jcstands;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class PanelListadoEmpleados extends JPanel {
    private static final int COLUMNA_SELECCION = 0;
    private static final int COLUMNA_ENTRADA = 2;
    private static final int COLUMNA_SALIDA = 3;

    private final DefaultTableModel modeloTabla;
    private final JTable tabla;
    private final DateTimeFormatter formato = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private List<Empleado> empleadosActuales = List.of();
    // ID real de la jornada que se muestra en cada fila (la última del empleado), fijado al dibujar la tabla.
    private List<Integer> idsJornadaPorFila = List.of();
    private final JButton botonModificar;
    private Empleado empleadoSeleccionado;

    private ControlAsistencia control;
    private Runnable alCorregirHorario;
    private boolean actualizandoTabla = false;

    // Empleados tildados en la columna SELECCIONAR (se guardan por id para que
    // el tilde sobreviva a las actualizaciones automáticas de la tabla).
    private final Set<Integer> idsMarcados = new LinkedHashSet<>();

    public PanelListadoEmpleados(Consumer<Empleado> alSeleccionar, Consumer<Empleado> alModificarValor) {
        setBorder(BorderFactory.createTitledBorder("Empleados"));
        modeloTabla = new DefaultTableModel(
                new Object[]{"SELECCIONAR", "EMPLEADO", "ENTRADA", "SALIDA", "VALOR X HORA", "ESTADO"}, 0) {
            @Override
            public Class<?> getColumnClass(int columna) {
                return columna == COLUMNA_SELECCION ? Boolean.class : Object.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                // ENTRADA y SALIDA de la última jornada se pueden corregir a mano,
                // directamente sobre la fila del empleado.
                return column == COLUMNA_ENTRADA || column == COLUMNA_SALIDA;
            }
        };
        tabla = new JTable(modeloTabla) {
            @Override
            public void changeSelection(int fila, int columna, boolean alternar, boolean extender) {
                // Tildar/destildar no debe cambiar el empleado seleccionado en el combo.
                if (convertColumnIndexToModel(columna) == COLUMNA_SELECCION) return;
                super.changeSelection(fila, columna, alternar, extender);
            }
        };
        tabla.setFillsViewportHeight(true);
        tabla.setRowHeight(34);
        tabla.setFont(tabla.getFont().deriveFont(15f));
        tabla.getTableHeader().setFont(tabla.getTableHeader().getFont().deriveFont(Font.BOLD, 14f));
        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        configurarColumnaSeleccion();

        botonModificar = new JButton("Modificar valor por hora");
        botonModificar.setEnabled(false);

        tabla.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int fila = tabla.getSelectedRow();
            if (fila >= 0 && fila < empleadosActuales.size()) {
                empleadoSeleccionado = empleadosActuales.get(fila);
                botonModificar.setEnabled(true);
                alSeleccionar.accept(empleadoSeleccionado);
            } else {
                empleadoSeleccionado = null;
                botonModificar.setEnabled(false);
            }
        });

        modeloTabla.addTableModelListener(this::onEdicionCelda);

        botonModificar.addActionListener(e -> {
            if (empleadoSeleccionado != null) alModificarValor.accept(empleadoSeleccionado);
        });

        JPanel inferior = new JPanel(new BorderLayout());
        JLabel ayuda = new JLabel("Doble clic en ENTRADA o SALIDA para corregir el horario manualmente (formato dd/MM/yyyy HH:mm:ss). "
                + "Deje SALIDA vacía si la jornada sigue en curso.");
        ayuda.setFont(ayuda.getFont().deriveFont(Font.ITALIC, 11.5f));
        ayuda.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        botones.add(botonModificar);
        inferior.add(ayuda, BorderLayout.WEST);
        inferior.add(botones, BorderLayout.EAST);

        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(1000, 225));
        add(new JScrollPane(tabla), BorderLayout.CENTER);
        add(inferior, BorderLayout.SOUTH);
    }

    /**
     * @param control          referencia usada para leer/corregir jornadas al editar una celda.
     * @param alCorregirHorario se ejecuta después de guardar una corrección hecha desde la tabla,
     *                           para refrescar el resto de la ventana (resumen, pagos, historial, etc).
     */
    public void refrescar(List<Empleado> empleados, ControlAsistencia control) {
        this.control = control;
        Integer idSeleccionado = empleadoSeleccionado != null ? empleadoSeleccionado.getId() : null;
        empleadosActuales = List.copyOf(empleados);

        // Si se borró un empleado, se descarta su tilde.
        Set<Integer> idsVigentes = new LinkedHashSet<>();
        for (Empleado empleado : empleadosActuales) idsVigentes.add(empleado.getId());
        idsMarcados.retainAll(idsVigentes);

        List<Integer> idsJornadas = new ArrayList<>();
        actualizandoTabla = true;
        try {
            modeloTabla.setRowCount(0);
            for (Empleado empleado : empleados) {
                Jornada ultima = control.obtenerUltimaJornada(empleado);
                idsJornadas.add(ultima != null ? ultima.getId() : -1);
                String entrada = "";
                String salida = "";
                if (ultima != null) {
                    entrada = ultima.getEntrada().format(formato);
                    salida = ultima.getSalida() != null ? ultima.getSalida().format(formato) : "EN CURSO";
                }
                modeloTabla.addRow(new Object[]{
                        idsMarcados.contains(empleado.getId()),
                        empleado.getNombre(),
                        entrada,
                        salida,
                        Formato.dinero(empleado.getValorHora()),
                        control.estaTrabajando(empleado) ? "TRABAJANDO" : "FUERA"
                });
            }
        } finally {
            actualizandoTabla = false;
        }
        idsJornadaPorFila = idsJornadas;

        tabla.getTableHeader().repaint();

        int nuevaFilaSeleccionada = -1;
        if (idSeleccionado != null) {
            for (int i = 0; i < empleadosActuales.size(); i++) {
                if (empleadosActuales.get(i).getId() == idSeleccionado) { nuevaFilaSeleccionada = i; break; }
            }
        }

        if (nuevaFilaSeleccionada >= 0) {
            tabla.setRowSelectionInterval(nuevaFilaSeleccionada, nuevaFilaSeleccionada);
            empleadoSeleccionado = empleadosActuales.get(nuevaFilaSeleccionada);
            botonModificar.setEnabled(true);
        } else {
            empleadoSeleccionado = null;
            botonModificar.setEnabled(false);
        }
    }

    // ------------------------------------------------------------------
    // Columna SELECCIONAR (tildes por empleado + "seleccionar todos")
    // ------------------------------------------------------------------

    private void configurarColumnaSeleccion() {
        JTableHeader cabecera = tabla.getTableHeader();
        cabecera.setReorderingAllowed(false);
        cabecera.setToolTipText("Clic para seleccionar o deseleccionar a todos los empleados");

        TableColumn columna = tabla.getColumnModel().getColumn(COLUMNA_SELECCION);
        RendererCabeceraSeleccion renderer = new RendererCabeceraSeleccion();
        columna.setHeaderRenderer(renderer);
        // Ancho justo para "☐ SELECCIONAR", sin espacio de sobra a los costados.
        renderer.getTableCellRendererComponent(tabla, null, false, false, -1, COLUMNA_SELECCION);
        int ancho = renderer.getPreferredSize().width + 6;
        columna.setMinWidth(ancho);
        columna.setMaxWidth(ancho);
        columna.setPreferredWidth(ancho);
        columna.setResizable(false);

        // Clic en el encabezado: marca o desmarca a todos.
        cabecera.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int vista = cabecera.columnAtPoint(e.getPoint());
                if (vista >= 0 && tabla.convertColumnIndexToModel(vista) == COLUMNA_SELECCION) {
                    alternarTodos();
                }
            }
        });

        // Clic en la casilla de una fila: marca o desmarca a ese empleado.
        // Se resuelve al presionar (y no vía editor de celda) para que la
        // actualización automática de la tabla no se "coma" el clic.
        tabla.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                int fila = tabla.rowAtPoint(e.getPoint());
                int vista = tabla.columnAtPoint(e.getPoint());
                if (fila >= 0 && vista >= 0 && tabla.convertColumnIndexToModel(vista) == COLUMNA_SELECCION) {
                    alternarFila(fila);
                }
            }
        });
    }

    private boolean todosMarcados() {
        if (empleadosActuales.isEmpty()) return false;
        for (Empleado empleado : empleadosActuales) {
            if (!idsMarcados.contains(empleado.getId())) return false;
        }
        return true;
    }

    private void alternarTodos() {
        boolean marcar = !todosMarcados();
        idsMarcados.clear();
        if (marcar) {
            for (Empleado empleado : empleadosActuales) idsMarcados.add(empleado.getId());
        }
        actualizandoTabla = true;
        try {
            for (int fila = 0; fila < modeloTabla.getRowCount(); fila++) {
                modeloTabla.setValueAt(marcar, fila, COLUMNA_SELECCION);
            }
        } finally {
            actualizandoTabla = false;
        }
        tabla.getTableHeader().repaint();
    }

    private void alternarFila(int fila) {
        if (fila < 0 || fila >= empleadosActuales.size()) return;
        int id = empleadosActuales.get(fila).getId();
        boolean marcar = idsMarcados.add(id);
        if (!marcar) idsMarcados.remove(id);
        actualizandoTabla = true;
        try {
            modeloTabla.setValueAt(marcar, fila, COLUMNA_SELECCION);
        } finally {
            actualizandoTabla = false;
        }
        tabla.getTableHeader().repaint();
    }

    /** Empleados que están tildados en la columna SELECCIONAR. */
    public List<Empleado> getEmpleadosMarcados() {
        List<Empleado> marcados = new ArrayList<>();
        for (Empleado empleado : empleadosActuales) {
            if (idsMarcados.contains(empleado.getId())) marcados.add(empleado);
        }
        return marcados;
    }

    /** Destilda a los empleados indicados (por ejemplo, después de registrarles la salida). */
    public void desmarcar(Collection<Empleado> empleados) {
        for (Empleado empleado : empleados) idsMarcados.remove(empleado.getId());
        actualizandoTabla = true;
        try {
            for (int fila = 0; fila < empleadosActuales.size() && fila < modeloTabla.getRowCount(); fila++) {
                modeloTabla.setValueAt(idsMarcados.contains(empleadosActuales.get(fila).getId()), fila, COLUMNA_SELECCION);
            }
        } finally {
            actualizandoTabla = false;
        }
        tabla.getTableHeader().repaint();
    }

    /** Encabezado de la columna SELECCIONAR: casilla "seleccionar todos" + título. */
    private class RendererCabeceraSeleccion extends JCheckBox implements TableCellRenderer {
        RendererCabeceraSeleccion() {
            super("SELECCIONAR");
            setHorizontalAlignment(SwingConstants.CENTER);
            setFocusPainted(false);
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object valor, boolean seleccionada,
                boolean foco, int fila, int columna) {
            JTableHeader cabecera = t.getTableHeader();
            setFont(cabecera.getFont());
            setForeground(cabecera.getForeground());
            setBackground(cabecera.getBackground());
            Border bordeCelda = UIManager.getBorder("TableHeader.cellBorder");
            Border margen = BorderFactory.createEmptyBorder(0, 2, 0, 2);
            setBorder(bordeCelda != null ? BorderFactory.createCompoundBorder(bordeCelda, margen) : margen);
            setSelected(todosMarcados());
            return this;
        }
    }

    /** Debe llamarse una sola vez, típicamente al armar la ventana principal. */
    public void alCorregirHorario(Runnable callback) {
        this.alCorregirHorario = callback;
    }

    private void onEdicionCelda(TableModelEvent evento) {
        if (actualizandoTabla) return;
        if (evento.getType() != TableModelEvent.UPDATE) return;
        int columna = evento.getColumn();
        if (columna != COLUMNA_ENTRADA && columna != COLUMNA_SALIDA) return;
        int fila = evento.getFirstRow();
        if (control == null || fila < 0 || fila >= empleadosActuales.size()) return;

        Empleado empleado = empleadosActuales.get(fila);
        // Se corrige la jornada por su ID real (el que tenía la fila cuando se dibujó), no por posición.
        int idJornada = fila < idsJornadaPorFila.size() ? idsJornadaPorFila.get(fila) : -1;
        Jornada ultima = idJornada >= 0 ? control.obtenerJornadaPorId(idJornada) : null;
        if (ultima == null) {
            JOptionPane.showMessageDialog(this,
                    empleado.getNombre() + " todavía no tiene ninguna jornada registrada.",
                    "Sin registros", JOptionPane.WARNING_MESSAGE);
            refrescar(empleadosActuales, control);
            return;
        }

        String texto = String.valueOf(modeloTabla.getValueAt(fila, columna)).trim();
        try {
            if (columna == COLUMNA_ENTRADA) {
                if (texto.isEmpty()) throw new AsistenciaException("La entrada no puede quedar vacía.");
                LocalDateTime nuevaEntrada = LocalDateTime.parse(texto, formato);
                control.corregirJornada(ultima, nuevaEntrada, ultima.getSalida());
            } else {
                LocalDateTime nuevaSalida = null;
                if (!texto.isEmpty() && !texto.equalsIgnoreCase("EN CURSO")) {
                    nuevaSalida = LocalDateTime.parse(texto, formato);
                }
                control.corregirJornada(ultima, ultima.getEntrada(), nuevaSalida);
            }
            if (alCorregirHorario != null) alCorregirHorario.run();
            else refrescar(empleadosActuales, control);
        } catch (java.time.format.DateTimeParseException ex) {
            JOptionPane.showMessageDialog(this,
                    "Formato inválido. Use dd/MM/yyyy HH:mm:ss.\nEn SALIDA también puede dejarlo vacío o escribir EN CURSO si sigue trabajando.",
                    "Dato inválido", JOptionPane.WARNING_MESSAGE);
            refrescar(empleadosActuales, control);
        } catch (AsistenciaException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "No se pudo corregir", JOptionPane.WARNING_MESSAGE);
            refrescar(empleadosActuales, control);
        }
    }
}
