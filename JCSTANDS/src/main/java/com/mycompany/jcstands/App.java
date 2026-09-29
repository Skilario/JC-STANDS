package com.mycompany.jcstands;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.util.List;

public class App {
    private static volatile boolean mostrandoErrorInesperado = false;

    private void crearVentana(ControlAsistencia control, String avisoImportacion) {
        JFrame ventana = new JFrame("JC STANDS");
        ventana.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        ventana.setMinimumSize(new Dimension(900, 650));
        ventana.setSize(1500, 900);
        ventana.setLocationRelativeTo(null);

        JPanel panelPrincipal = new JPanel();
        panelPrincipal.setLayout(new BoxLayout(panelPrincipal, BoxLayout.Y_AXIS));
        panelPrincipal.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        PanelResumenHoras panelResumen = new PanelResumenHoras();
        final PanelListadoEmpleados[] refListado = new PanelListadoEmpleados[1];
        final PanelEntradaSalida[] refEntradaSalida = new PanelEntradaSalida[1];
        final PanelPagos[] refPagos = new PanelPagos[1];
        final Empleado[] empleadoSeleccionado = new Empleado[1];

        Runnable actualizarResumen = () -> {
            Empleado empleado = empleadoSeleccionado[0];
            if (empleado == null) {
                panelResumen.actualizar(null, null);
            } else {
                panelResumen.actualizar(empleado, control.calcularResumen(empleado));
            }
        };

        Runnable refrescarTodo = () -> {
            if (refEntradaSalida[0] != null) refEntradaSalida[0].refrescarEmpleados(control);
            if (refListado[0] != null) refListado[0].refrescar(control.getEmpleados(), control);
            if (refPagos[0] != null) refPagos[0].refrescar();
            actualizarResumen.run();
        };

        PanelEntradaSalida entrada = new PanelEntradaSalida(control, refrescarTodo, empleado -> {
            empleadoSeleccionado[0] = empleado;
            actualizarResumen.run();
        }, () -> refListado[0] != null ? refListado[0].getEmpleadosMarcados() : List.of(),
            conSalida -> { if (refListado[0] != null) refListado[0].desmarcar(conSalida); });
        refEntradaSalida[0] = entrada;

        PanelListadoEmpleados listado = new PanelListadoEmpleados(empleado -> {
            empleadoSeleccionado[0] = empleado;
            entrada.seleccionarEmpleado(empleado);
            actualizarResumen.run();
        }, empleado -> {
            String actualPlano = String.format(java.util.Locale.US, "%.2f", empleado.getValorHora());
            String actualLegible = Formato.dinero(empleado.getValorHora());
            String texto = JOptionPane.showInputDialog(ventana,
                    "Nuevo valor por hora para " + empleado.getNombre() + "\nValor actual: $ " + actualLegible,
                    actualPlano);
            if (texto == null) return;
            try {
                java.math.BigDecimal nuevoValor = Dinero.parsearValorHora(texto);
                control.modificarValorHora(empleado, nuevoValor);
                refrescarTodo.run();
                JOptionPane.showMessageDialog(ventana,
                        "Valor por hora actualizado a $ " + Formato.dinero(nuevoValor)
                                + ".\nLas jornadas anteriores conservan su valor original.",
                        "Valor actualizado", JOptionPane.INFORMATION_MESSAGE);
            } catch (AsistenciaException ex) {
                JOptionPane.showMessageDialog(ventana, ex.getMessage(), "No se pudo actualizar", JOptionPane.WARNING_MESSAGE);
            }
        });
        listado.alCorregirHorario(refrescarTodo);
        refListado[0] = listado;

        PanelPagos pagos = new PanelPagos(control);
        refPagos[0] = pagos;

        panelPrincipal.add(new PanelAltaEmpleado(ventana, control, refrescarTodo));
        panelPrincipal.add(Box.createVerticalStrut(6));
        panelPrincipal.add(entrada);
        panelPrincipal.add(Box.createVerticalStrut(6));
        panelPrincipal.add(listado);
        panelPrincipal.add(Box.createVerticalStrut(6));
        panelPrincipal.add(panelResumen);
        panelPrincipal.add(Box.createVerticalStrut(6));
        panelPrincipal.add(pagos);
        panelPrincipal.add(Box.createVerticalStrut(6));
        panelPrincipal.add(new PanelExportarReporte(ventana, control));

        JScrollPane scroll = new JScrollPane(panelPrincipal);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        ventana.setContentPane(scroll);

        refrescarTodo.run();
        ventana.setVisible(true);

        final LocalDateTime[] ultimoMinuto = {ClockApp.ahora().truncatedTo(java.time.temporal.ChronoUnit.MINUTES)};
        final java.time.LocalDate[] ultimoDia = {ClockApp.hoy()};
        new javax.swing.Timer(1000, e -> {
            LocalDateTime minuto = ClockApp.ahora().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
            if (minuto.equals(ultimoMinuto[0])) return;
            ultimoMinuto[0] = minuto;
            List<Jornada> cerradas = control.cerrarJornadasQueSuperanLimite();
            if (!cerradas.isEmpty()) {
                refrescarTodo.run();
            } else {
                actualizarResumen.run();
            }
            if (!ClockApp.hoy().equals(ultimoDia[0])) {
                ultimoDia[0] = ClockApp.hoy();
                iniciarBackupDiario();
            }
        }).start();

        iniciarBackupDiario();

        if (avisoImportacion != null) {
            JOptionPane.showMessageDialog(ventana, avisoImportacion, "Base de datos importada", JOptionPane.INFORMATION_MESSAGE);
        }

        List<Jornada> abiertas = control.getJornadasAbiertas();
        if (!abiertas.isEmpty()) {
            StringBuilder t = new StringBuilder("Se recuperaron jornadas abiertas:\n\n");
            for (Jornada j : abiertas) {
                t.append("• ").append(j.getEmpleado().getNombre()).append(" - ").append(j.getEntrada()).append("\n");
            }
            t.append("\nEl tiempo se calcula desde la hora guardada. Registre la salida cuando corresponda.");
            JOptionPane.showMessageDialog(ventana, t.toString(), "Jornadas abiertas recuperadas", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private static void iniciarBackupDiario() {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                Respaldo.crearDiarioSiHaceFalta();
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (java.util.concurrent.ExecutionException ex) {
                    Log.error("No se pudo crear el backup diario", ex.getCause());
                }
            }
        }.execute();
    }

    private static void errorFatal(String titulo, String mensaje) {
        Log.warn("Error fatal al iniciar: " + titulo + " - " + mensaje.replace('\n', ' '));
        try {
            SwingUtilities.invokeAndWait(() ->
                    JOptionPane.showMessageDialog(null, mensaje, titulo, JOptionPane.ERROR_MESSAGE));
        } catch (Exception ex) {
            System.err.println(titulo + ": " + mensaje);
        }
        System.exit(1);
    }

    private static void instalarManejoDeErrores() {
        Thread.setDefaultUncaughtExceptionHandler((hilo, error) -> {
            Log.error("Excepción no controlada en el hilo " + hilo.getName(), error);
            if (mostrandoErrorInesperado || java.awt.GraphicsEnvironment.isHeadless()) return;
            mostrandoErrorInesperado = true;
            SwingUtilities.invokeLater(() -> {
                try {
                    JOptionPane.showMessageDialog(null,
                            "Ocurrió un error inesperado. La aplicación sigue abierta.\n"
                                    + "Si los datos en pantalla no parecen correctos, cierre y vuelva a abrir el programa.\n\n"
                                    + "El detalle técnico quedó registrado en:\n" + AppPaths.archivoLog(),
                            "Error inesperado", JOptionPane.ERROR_MESSAGE);
                } finally {
                    mostrandoErrorInesperado = false;
                }
            });
        });
    }

    public static void main(String[] args) {
        instalarManejoDeErrores();
        try {
            AppPaths.asegurarCarpetas();
        } catch (java.io.IOException ex) {
            Log.error("No se pudo crear la carpeta de datos " + AppPaths.carpetaBase(), ex);
            errorFatal("No se puede iniciar", "No se pudo crear la carpeta de datos:\n" + AppPaths.carpetaBase()
                    + "\n\nVerifique los permisos de esa carpeta.");
            return;
        }
        Log.info("Iniciando JC STANDS. Base de datos: " + AppPaths.archivoBaseDatos());

        if (!InstanciaUnica.adquirir()) {
            errorFatal("JC STANDS ya está abierto",
                    "JC STANDS ya está abierto en este equipo.\nUse la ventana que ya está abierta.");
            return;
        }

        final ControlAsistencia control;
        final String aviso;
        try {
            aviso = Database.importarBaseAnteriorSiCorresponde();
            
            // ---> AQUÍ FALTABA ESTA LÍNEA CRUCIAL PARA CREAR LAS TABLAS <---
            Database.inicializar(); 
            
            control = new ControlAsistencia();
        } catch (IllegalStateException ex) {
            errorFatal("Error de base de datos", ex.getMessage());
            return;
        } catch (java.io.IOException | java.sql.SQLException | RuntimeException ex) {
            Log.error("No se pudo preparar la base de datos", ex);
            errorFatal("Error de base de datos", "No se pudo abrir la base de datos.\n\nUbicación: "
                    + AppPaths.archivoBaseDatos() + "\n\nEl detalle técnico quedó registrado en el archivo de log:\n"
                    + AppPaths.archivoLog());
            return;
        }
        SwingUtilities.invokeLater(() -> new App().crearVentana(control, aviso));
    }
}