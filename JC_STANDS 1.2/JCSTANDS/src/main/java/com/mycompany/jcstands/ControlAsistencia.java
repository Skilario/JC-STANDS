package com.mycompany.jcstands;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.*;

/**
 * Lógica de negocio de JC STANDS.
 *
 * Reglas que se aplican de forma consistente en resúmenes, pagos y CSV:
 *  - Una jornada pertenece al día, semana y mes de su FECHA DE ENTRADA. Una jornada que
 *    empieza el 19/09 a las 22:00 y termina el 20/09 a las 06:00 se cuenta entera el 19/09.
 *  - Las jornadas no tienen duración máxima (se registran las horas reales).
 *  - Cada jornada conserva el valor por hora vigente al registrar su entrada.
 */
public class ControlAsistencia {
    private static final int LARGO_MAXIMO_NOMBRE = 100;
    /** Margen para aceptar horarios "de ahora" tipeados a mano sin que cuenten como futuros. */
    private static final Duration TOLERANCIA_FUTURO = Duration.ofMinutes(1);

    private final List<Empleado> empleados = new ArrayList<>();
    private final List<Empleado> todosLosEmpleados = new ArrayList<>();
    private final List<Jornada> jornadas = new ArrayList<>();
    private final EmpleadoDAO empleadoDAO = new EmpleadoDAO();
    private final JornadaDAO jornadaDAO = new JornadaDAO();

    private static final DateTimeFormatter FORMATO_FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public ControlAsistencia() {
        try {
            Database.inicializar();
            cargarDesdeBase();
            // Sin límite de 12 horas
        } catch (SQLException e) {
            Log.error("No se pudo iniciar la base de datos", e);
            throw new IllegalStateException("No se pudo abrir la base de datos.\n\nUbicación: "
                    + Database.archivoBaseDatos() + "\n\nEl detalle técnico quedó registrado en el archivo de log:\n"
                    + AppPaths.archivoLog(), e);
        }
    }

    /**
     * Sin límite de 12 horas - método vacío.
     */
    public List<Jornada> cerrarJornadasQueSuperanLimite() {
        return new ArrayList<>();
    }

    private void cargarDesdeBase() throws SQLException {
        // Se arma todo primero y recién después se reemplaza el contenido, para no quedar a medias si algo falla.
        List<Empleado> todos = empleadoDAO.listarTodos();
        List<Empleado> activos = empleadoDAO.listar();
        List<Jornada> lista = jornadaDAO.listar(todos);
        empleados.clear(); todosLosEmpleados.clear(); jornadas.clear();
        todosLosEmpleados.addAll(todos);
        empleados.addAll(activos);
        jornadas.addAll(lista);
    }

    /** Vuelve a leer todo desde la base (la base es la fuente de verdad). Solo se usa ante una inconsistencia. */
    private void resincronizar() {
        try {
            cargarDesdeBase();
            Log.info("Datos en memoria resincronizados desde la base de datos.");
        } catch (SQLException e) {
            Log.error("No se pudieron resincronizar los datos desde la base", e);
        }
    }

    /** Sin límite de 12 horas - método vacío. */
    private void avisarJornadasMayoresAlLimite() {
        // Sin límite de 12 horas
    }

    public List<Empleado> getEmpleados() { return Collections.unmodifiableList(empleados); }
    public List<Empleado> getTodosLosEmpleados() { return Collections.unmodifiableList(todosLosEmpleados); }
    public List<Jornada> getJornadas() { return Collections.unmodifiableList(jornadas); }

    public List<Jornada> getJornadasAbiertas() {
        List<Jornada> abiertas = new ArrayList<>();
        for (Jornada j : jornadas) if (!j.estaCerrada()) abiertas.add(j);
        return abiertas;
    }

    public Jornada obtenerJornadaPorId(int id) {
        for (Jornada j : jornadas) if (j.getId() == id) return j;
        return null;
    }

    // ------------------------------------------------------------------
    // Empleados
    // ------------------------------------------------------------------

    /** Quita espacios al principio y al final y reemplaza espacios repetidos por uno solo. */
    public static String normalizarNombre(String nombre) {
        return nombre == null ? "" : nombre.strip().replaceAll("\\s+", " ");
    }

    private static String claveNombre(String nombre) {
        return normalizarNombre(nombre).toLowerCase(Locale.ROOT);
    }

    /** true si ya hay un empleado ACTIVO con ese nombre (sin distinguir mayúsculas ni espacios de más). */
    public boolean existeNombreActivo(String nombre) {
        String clave = claveNombre(nombre);
        for (Empleado e : empleados) if (claveNombre(e.getNombre()).equals(clave)) return true;
        return false;
    }

    public Empleado agregarEmpleado(String nombre, BigDecimal valorHora) throws AsistenciaException {
        String limpio = normalizarNombre(nombre);
        if (limpio.isEmpty()) throw new AsistenciaException("Ingrese un nombre");
        if (limpio.length() > LARGO_MAXIMO_NOMBRE) {
            throw new AsistenciaException("El nombre es demasiado largo (máximo " + LARGO_MAXIMO_NOMBRE + " caracteres)");
        }
        if (limpio.matches(".*\\d.*")) throw new AsistenciaException("El nombre no puede contener números");
        if (existeNombreActivo(limpio)) throw new AsistenciaException("Ya existe un empleado con ese nombre");
        BigDecimal valor = Dinero.validarValorHora(valorHora);
        try {
            Empleado empleado = empleadoDAO.insertar(limpio, valor);
            empleados.add(empleado);
            todosLosEmpleados.add(empleado);
            Log.info("Empleado agregado: " + empleado.getId() + " - " + limpio);
            return empleado;
        } catch (SQLException e) {
            if (AsistenciaException.esViolacionUnica(e)) {
                resincronizar();
                throw new AsistenciaException("Ya existe un empleado con ese nombre");
            }
            throw AsistenciaException.porErrorDeBase("guardar el empleado", e);
        }
    }

    public void modificarValorHora(Empleado empleado, BigDecimal nuevoValor) throws AsistenciaException {
        if (empleado == null) throw new AsistenciaException("No se seleccionó ningún empleado.");
        BigDecimal valor = Dinero.validarValorHora(nuevoValor);
        try {
            empleadoDAO.actualizarValorHora(empleado.getId(), valor);
            for (Empleado e : empleados) {
                if (e.getId() == empleado.getId()) e.setValorHora(valor);
            }
            for (Empleado e : todosLosEmpleados) {
                if (e.getId() == empleado.getId()) e.setValorHora(valor);
            }
            empleado.setValorHora(valor);
            Log.info("Valor por hora modificado: empleado " + empleado.getId() + " ahora $ " + valor);
        } catch (SQLException e) {
            throw AsistenciaException.porErrorDeBase("actualizar el valor por hora", e);
        }
    }

    public void eliminarEmpleado(Empleado empleado) throws AsistenciaException {
        if (empleado == null) throw new AsistenciaException("No se seleccionó ningún empleado.");
        if (estaTrabajando(empleado)) throw new AsistenciaException("No se puede borrar un empleado que está trabajando.");
        try {
            if (!empleadoDAO.desactivar(empleado.getId())) {
                if (empleadoDAO.tieneJornadaAbierta(empleado.getId())) {
                    resincronizar();
                    throw new AsistenciaException("No se puede borrar un empleado que está trabajando.");
                }
                // Ya estaba dado de baja (por ejemplo, operación repetida): el resultado buscado ya se cumple.
                Log.info("Baja repetida ignorada: el empleado " + empleado.getId() + " ya estaba inactivo.");
            } else {
                Log.info("Empleado dado de baja (baja lógica): " + empleado.getId() + " - " + empleado.getNombre());
            }
            empleados.removeIf(e -> e.getId() == empleado.getId());
        } catch (SQLException e) {
            throw AsistenciaException.porErrorDeBase("borrar el empleado", e);
        }
    }

    public boolean estaTrabajando(Empleado empleado) {
        return obtenerJornadaAbierta(empleado) != null;
    }

    public Jornada obtenerJornadaAbierta(Empleado empleado) {
        if (empleado == null) return null;
        for (Jornada j : jornadas) {
            if (j.getEmpleado().getId() == empleado.getId() && !j.estaCerrada()) return j;
        }
        return null;
    }

    public Jornada obtenerUltimaJornada(Empleado empleado) {
        Jornada ultima = null;
        for (Jornada jornada : jornadas) {
            if (jornada.getEmpleado().getId() != empleado.getId()) continue;
            boolean esMasReciente = ultima == null
                    || jornada.getEntrada().isAfter(ultima.getEntrada())
                    || (jornada.getEntrada().isEqual(ultima.getEntrada()) && jornada.getId() > ultima.getId());
            if (esMasReciente) ultima = jornada;
        }
        return ultima;
    }

    // ------------------------------------------------------------------
    // Jornadas
    // ------------------------------------------------------------------

    public Jornada registrarEntradaManual(Empleado empleado, LocalDateTime entrada) throws AsistenciaException {
        if (empleado == null) throw new AsistenciaException("Seleccione un empleado.");
        if (entrada == null) throw new AsistenciaException("Indique una fecha y hora de entrada.");
        if (estaTrabajando(empleado)) throw new AsistenciaException("Este empleado ya tiene una jornada abierta.");
        try {
            Jornada jornada = jornadaDAO.insertar(empleado, entrada);
            jornadas.add(jornada);
            Log.info("Entrada registrada: " + empleado.getNombre() + " " + entrada);
            return jornada;
        } catch (SQLException e) {
            if (AsistenciaException.esViolacionUnica(e)) {
                // La base ya tenía una jornada abierta que la memoria no conocía.
                resincronizar();
                throw new AsistenciaException("Este empleado ya tiene una jornada abierta.");
            }
            throw AsistenciaException.porErrorDeBase("guardar la entrada", e);
        }
    }

    /** Sin límite de 12 horas - retorna la salida tal cual. */
    private static LocalDateTime limitarAlMaximo(Jornada jornada, LocalDateTime salida) {
        return salida;
    }

    public Jornada registrarSalidaManual(Empleado empleado, LocalDateTime salida) throws AsistenciaException {
        if (empleado == null) throw new AsistenciaException("Seleccione un empleado.");
        Jornada abierta = obtenerJornadaAbierta(empleado);
        if (abierta == null) throw new AsistenciaException("Este empleado no está trabajando.");
        if (salida == null || salida.isBefore(abierta.getEntrada())) throw new AsistenciaException("La salida no puede ser anterior a la entrada.");
        LocalDateTime efectiva = limitarAlMaximo(abierta, salida);
        try {
            jornadaDAO.registrarSalida(abierta.getId(), efectiva);
            abierta.setSalida(efectiva);
            Log.info("Salida registrada: " + empleado.getNombre() + " " + efectiva);
            return abierta;
        } catch (SQLException e) {
            AsistenciaException error = AsistenciaException.porErrorDeBase("guardar la salida", e);
            resincronizar();
            if (!estaTrabajando(empleado)) throw new AsistenciaException("Este empleado no está trabajando.");
            throw error;
        }
    }

    public void corregirJornada(Jornada jornada, LocalDateTime entrada, LocalDateTime salida) throws AsistenciaException {
        if (jornada == null || entrada == null) throw new AsistenciaException("Datos de jornada inválidos.");
        Jornada vigente = obtenerJornadaPorId(jornada.getId());
        if (vigente == null) throw new AsistenciaException("La jornada ya no existe.");

        LocalDateTime ahora = ClockApp.ahora();
        if (entrada.isAfter(ahora.plus(TOLERANCIA_FUTURO))) {
            throw new AsistenciaException("La entrada no puede estar en el futuro.");
        }
        if (salida != null) {
            if (salida.isBefore(entrada)) throw new AsistenciaException("La salida no puede ser anterior a la entrada.");
            if (salida.isAfter(ahora.plus(TOLERANCIA_FUTURO))) throw new AsistenciaException("La salida no puede estar en el futuro.");
        }

        try {
            jornadaDAO.actualizarHorarios(vigente.getId(), entrada, salida);
            vigente.setEntrada(entrada);
            vigente.setSalida(salida);
            if (vigente != jornada) { jornada.setEntrada(entrada); jornada.setSalida(salida); }
            Log.info("Jornada " + vigente.getId() + " corregida: " + entrada + " a " + (salida == null ? "en curso" : salida));
        } catch (SQLException e) {
            if (AsistenciaException.esViolacionUnica(e)) {
                Log.warn("Corrección rechazada por la base: el empleado ya tiene otra jornada abierta (jornada " + vigente.getId() + ").");
                throw new AsistenciaException("Este empleado ya tiene otra jornada abierta; no se puede dejar esta también en curso.");
            }
            throw AsistenciaException.porErrorDeBase("corregir la jornada", e);
        }
    }

    public List<Jornada> registrarEntradaATodos() throws AsistenciaException {
        return registrarEntradaA(empleados);
    }

    /**
     * Registra la entrada (a la hora actual) solamente de los empleados indicados que todavía no estén trabajando.
     * Es todo o nada: se hace en una única transacción; si alguna falla, no se registra ninguna
     * y la memoria queda exactamente como estaba.
     */
    public List<Jornada> registrarEntradaA(Collection<Empleado> seleccionados) throws AsistenciaException {
        LocalDateTime ahora = ClockApp.ahora().withNano(0);
        List<Empleado> aRegistrar = new ArrayList<>();
        Set<Integer> vistos = new HashSet<>();
        for (Empleado empleado : seleccionados) {
            if (empleado == null || !vistos.add(empleado.getId())) continue;
            if (estaTrabajando(empleado)) continue;
            aRegistrar.add(empleado);
        }
        if (aRegistrar.isEmpty()) return new ArrayList<>();

        List<Jornada> nuevas = new ArrayList<>();
        try {
            enTransaccion(c -> {
                for (Empleado empleado : aRegistrar) nuevas.add(jornadaDAO.insertar(c, empleado, ahora));
            });
        } catch (SQLException e) {
            if (AsistenciaException.esViolacionUnica(e)) {
                resincronizar();
                throw new AsistenciaException("Alguno de los empleados ya tenía una jornada abierta. No se registró ninguna entrada; revise la lista e intente de nuevo.");
            }
            throw AsistenciaException.porErrorDeBase("registrar las entradas (no se registró ninguna)", e);
        }
        jornadas.addAll(nuevas);          // la memoria se actualiza solo después de confirmar la transacción
        Log.info("Entrada registrada a " + nuevas.size() + " empleado(s) " + ahora);
        return nuevas;
    }

    /**
     * Registra la salida de los empleados indicados que estén trabajando (los que no, se ignoran).
     * Es todo o nada: una única transacción. Si alguna falla, no se cierra ninguna jornada.
     */
    public List<Jornada> registrarSalidaA(Collection<Empleado> seleccionados, LocalDateTime salida) throws AsistenciaException {
        if (salida == null) throw new AsistenciaException("Indique una fecha y hora de salida.");
        List<Jornada> aCerrar = new ArrayList<>();
        List<LocalDateTime> salidasEfectivas = new ArrayList<>();
        Set<Integer> vistos = new HashSet<>();
        for (Empleado empleado : seleccionados) {
            if (empleado == null || !vistos.add(empleado.getId())) continue;
            Jornada abierta = obtenerJornadaAbierta(empleado);
            if (abierta == null) continue;
            if (salida.isBefore(abierta.getEntrada())) {
                throw new AsistenciaException(empleado.getNombre() + ": la salida no puede ser anterior a la entrada.");
            }
            aCerrar.add(abierta);
            salidasEfectivas.add(limitarAlMaximo(abierta, salida));
        }
        if (aCerrar.isEmpty()) return new ArrayList<>();

        try {
            enTransaccion(c -> {
                for (int i = 0; i < aCerrar.size(); i++) jornadaDAO.registrarSalida(c, aCerrar.get(i).getId(), salidasEfectivas.get(i));
            });
        } catch (SQLException e) {
            AsistenciaException error = AsistenciaException.porErrorDeBase("registrar las salidas (no se registró ninguna)", e);
            resincronizar();
            throw error;
        }
        for (int i = 0; i < aCerrar.size(); i++) aCerrar.get(i).setSalida(salidasEfectivas.get(i));   // recién después del commit
        Log.info("Salida registrada a " + aCerrar.size() + " empleado(s) " + salida);
        return aCerrar;
    }

    private interface OperacionSql { void ejecutar(Connection c) throws SQLException; }

    private static void enTransaccion(OperacionSql operacion) throws SQLException {
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                operacion.ejecutar(c);
                c.commit();
            } catch (SQLException | RuntimeException e) {
                try { c.rollback(); } catch (SQLException r) { Log.error("No se pudo revertir la transacción", r); }
                throw e;
            }
        }
    }

    // ------------------------------------------------------------------
    // Resúmenes
    // ------------------------------------------------------------------

    public ResumenHoras calcularResumen() { return calcularResumen(null); }

    public ResumenHoras calcularResumen(Empleado filtro) {
        LocalDateTime ahora = ClockApp.ahora();
        LocalDate hoy = ahora.toLocalDate();
        LocalDate inicioSemana = hoy.with(DayOfWeek.MONDAY);
        LocalDate finSemana = inicioSemana.plusDays(6);
        Duration horasDia = Duration.ZERO, horasSemana = Duration.ZERO, horasMes = Duration.ZERO, horasTotal = Duration.ZERO;
        BigDecimal dineroDia = BigDecimal.ZERO, dineroSemana = BigDecimal.ZERO, dineroMes = BigDecimal.ZERO, dineroTotal = BigDecimal.ZERO;

        for (Jornada jornada : jornadas) {
            if (filtro != null && jornada.getEmpleado().getId() != filtro.getId()) continue;
            Duration duracion = jornada.getDuracionHasta(ahora);
            BigDecimal dinero = jornada.getDineroGeneradoHasta(ahora);
            LocalDate fecha = jornada.getEntrada().toLocalDate();
            horasTotal = horasTotal.plus(duracion); dineroTotal = dineroTotal.add(dinero);
            if (fecha.equals(hoy)) { horasDia = horasDia.plus(duracion); dineroDia = dineroDia.add(dinero); }
            if (!fecha.isBefore(inicioSemana) && !fecha.isAfter(finSemana)) { horasSemana = horasSemana.plus(duracion); dineroSemana = dineroSemana.add(dinero); }
            if (YearMonth.from(fecha).equals(YearMonth.from(hoy))) { horasMes = horasMes.plus(duracion); dineroMes = dineroMes.add(dinero); }
        }
        return new ResumenHoras(horasDia, horasSemana, horasMes, horasTotal, dineroDia, dineroSemana, dineroMes, dineroTotal);
    }

    public ResumenHoras calcularResumenSemana(Empleado empleado, LocalDate lunes) {
        LocalDateTime ahora = ClockApp.ahora();
        LocalDate domingo = lunes.plusDays(6);
        Duration horas = Duration.ZERO; BigDecimal dinero = BigDecimal.ZERO;
        for (Jornada j : jornadas) {
            if (j.getEmpleado().getId() != empleado.getId()) continue;
            LocalDate f = j.getEntrada().toLocalDate();
            if (!f.isBefore(lunes) && !f.isAfter(domingo)) {
                horas = horas.plus(j.getDuracionHasta(ahora));
                dinero = dinero.add(j.getDineroGeneradoHasta(ahora));
            }
        }
        return new ResumenHoras(Duration.ZERO, horas, Duration.ZERO, horas, BigDecimal.ZERO, dinero, BigDecimal.ZERO, dinero);
    }

    public ResumenHoras calcularResumenMes(Empleado empleado, YearMonth mes) {
        LocalDateTime ahora = ClockApp.ahora();
        Duration horas = Duration.ZERO; BigDecimal dinero = BigDecimal.ZERO;
        for (Jornada j : jornadas) {
            if (j.getEmpleado().getId() != empleado.getId()) continue;
            if (YearMonth.from(j.getEntrada()).equals(mes)) {
                horas = horas.plus(j.getDuracionHasta(ahora));
                dinero = dinero.add(j.getDineroGeneradoHasta(ahora));
            }
        }
        return new ResumenHoras(Duration.ZERO, Duration.ZERO, horas, horas, BigDecimal.ZERO, BigDecimal.ZERO, dinero, dinero);
    }

    public List<YearMonth> mesesConJornadas(Empleado empleado) {
        Set<YearMonth> meses = new TreeSet<>(Comparator.reverseOrder());
        for (Jornada j : jornadas) if (j.getEmpleado().getId() == empleado.getId()) meses.add(YearMonth.from(j.getEntrada()));
        return new ArrayList<>(meses);
    }

    // ------------------------------------------------------------------
    // Exportación CSV
    // ------------------------------------------------------------------

    /**
     * Orden usado en todos los CSV exportados: primero por nombre de empleado
     * (para que las jornadas de cada persona queden agrupadas y no mezcladas
     * con las de otros empleados) y, dentro de cada empleado, por fecha de
     * entrada de la más vieja a la más nueva.
     */
    private static final Comparator<Jornada> ORDEN_POR_EMPLEADO_Y_FECHA =
            Comparator.comparing((Jornada j) -> j.getEmpleado().getNombre(), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Jornada::getEntrada);

    /**
     * Copia de las jornadas actuales. Permite generar un CSV en otro hilo (SwingWorker)
     * sin leer la lista viva mientras la pantalla la modifica.
     */
    public List<Jornada> copiaJornadas() {
        List<Jornada> copia = new ArrayList<>(jornadas.size());
        for (Jornada j : jornadas) {
            Jornada c = new Jornada(j.getId(), j.getEmpleado(), j.getEntrada(), j.getValorHora());
            c.setSalida(j.getSalida());
            copia.add(c);
        }
        return copia;
    }

    public void exportarHistorialCompleto(File archivo) throws IOException {
        exportarHistorialCompleto(archivo, copiaJornadas());
    }

    public void exportarHistorialCompleto(File archivo, List<Jornada> jornadasCopia) throws IOException {
        LocalDateTime ahora = ClockApp.ahora();
        Locale localeAR = Locale.forLanguageTag("es-AR");
        WeekFields wf = WeekFields.ISO;

        // Totales por empleado y día / semana ISO / mes, calculados de una sola pasada.
        Map<String, BigDecimal> totalDia = new HashMap<>(), totalSemana = new HashMap<>(), totalMes = new HashMap<>();
        for (Jornada j : jornadasCopia) {
            LocalDate f = j.getEntrada().toLocalDate();
            BigDecimal dinero = j.getDineroGeneradoHasta(ahora);
            totalDia.merge(claveDia(j.getEmpleado(), f), dinero, BigDecimal::add);
            totalSemana.merge(claveSemana(j.getEmpleado(), f, wf), dinero, BigDecimal::add);
            totalMes.merge(claveMes(j.getEmpleado(), f), dinero, BigDecimal::add);
        }

        StringBuilder csv = new StringBuilder();
        csv.append("Empleado;Fecha;Entrada;Salida;Estado;Horas;ValorHora;GanadoJornada;GanadoDia;GanadoSemana;GanadoMes\n");
        for (Jornada j : jornadasCopia.stream().sorted(ORDEN_POR_EMPLEADO_Y_FECHA).toList()) {
            LocalDate fecha = j.getEntrada().toLocalDate();
            csv.append(String.format(localeAR, "%s;%s;%s;%s;%s;%.2f;%.2f;%.2f;%.2f;%.2f;%.2f%n",
                    escaparCSV(j.getEmpleado().getNombre()), fecha.format(FORMATO_FECHA),
                    j.getEntrada().format(FORMATO_FECHA_HORA),
                    j.estaCerrada() ? j.getSalida().format(FORMATO_FECHA_HORA) : "EN CURSO",
                    j.estaCerrada() ? "CERRADA" : "TRABAJANDO",
                    j.getHorasTrabajadasHasta(ahora), j.getValorHora(), j.getDineroGeneradoHasta(ahora),
                    totalDia.get(claveDia(j.getEmpleado(), fecha)),
                    totalSemana.get(claveSemana(j.getEmpleado(), fecha, wf)),
                    totalMes.get(claveMes(j.getEmpleado(), fecha))));
        }
        escribirCsv(archivo, csv.toString());
    }

    public void exportarReporteMensual(File archivo, YearMonth mes) throws IOException {
        exportarReporteMensual(archivo, mes, copiaJornadas());
    }

    public void exportarReporteMensual(File archivo, YearMonth mes, List<Jornada> jornadasCopia) throws IOException {
        LocalDateTime ahora = ClockApp.ahora();
        Locale localeAR = Locale.forLanguageTag("es-AR");
        StringBuilder csv = new StringBuilder();
        csv.append("Empleado;Fecha;Entrada;Salida;Horas;ValorHora;Dinero\n");
        for (Jornada j : jornadasCopia.stream()
                .filter(j -> YearMonth.from(j.getEntrada()).equals(mes))
                .sorted(ORDEN_POR_EMPLEADO_Y_FECHA)
                .toList()) {
            csv.append(String.format(localeAR, "%s;%s;%s;%s;%.2f;%.2f;%.2f%n",
                    escaparCSV(j.getEmpleado().getNombre()), j.getEntrada().format(FORMATO_FECHA),
                    j.getEntrada().format(FORMATO_FECHA_HORA),
                    j.estaCerrada() ? j.getSalida().format(FORMATO_FECHA_HORA) : "EN CURSO",
                    j.getHorasTrabajadasHasta(ahora), j.getValorHora(), j.getDineroGeneradoHasta(ahora)));
        }
        escribirCsv(archivo, csv.toString());
    }

    private static String claveDia(Empleado e, LocalDate f) { return e.getId() + "|" + f; }
    private static String claveSemana(Empleado e, LocalDate f, WeekFields wf) {
        return e.getId() + "|" + f.get(wf.weekBasedYear()) + "-" + f.get(wf.weekOfWeekBasedYear());
    }
    private static String claveMes(Empleado e, LocalDate f) { return e.getId() + "|" + YearMonth.from(f); }

    /**
     * Escribe el CSV en UTF-8 (con marca BOM para que Excel reconozca tildes y ñ) primero en un
     * archivo temporal y después lo mueve al destino: si el destino está bloqueado (por ejemplo,
     * abierto en Excel) o falla la escritura, el archivo anterior queda intacto.
     */
    private static void escribirCsv(File archivo, String contenido) throws IOException {
        Path destino = archivo.toPath().toAbsolutePath();
        Path temporal = destino.resolveSibling(destino.getFileName() + ".tmp");
        try {
            Files.write(temporal, ("\uFEFF" + contenido).getBytes(StandardCharsets.UTF_8));
            Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            try { Files.deleteIfExists(temporal); } catch (IOException ignorada) { /* se informa el error original */ }
            throw e;
        }
    }

    /** Campos con ; comillas o saltos de línea van entre comillas (comillas internas duplicadas). */
    private static String escaparCSV(String texto) {
        if (texto == null) return "";
        boolean necesita = texto.contains(";") || texto.contains("\"") || texto.contains("\n") || texto.contains("\r");
        return necesita ? "\"" + texto.replace("\"", "\"\"") + "\"" : texto;
    }
}
