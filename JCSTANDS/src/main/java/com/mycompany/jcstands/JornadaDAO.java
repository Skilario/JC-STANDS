package com.mycompany.jcstands;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class JornadaDAO {
    public List<Jornada> listar(List<Empleado> empleados) throws SQLException {
        String sql = "SELECT id, empleado_id, entrada, salida, valor_hora FROM jornadas ORDER BY entrada";
        List<Jornada> resultado = new ArrayList<>();
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Empleado empleado = buscarEmpleado(empleados, rs.getInt("empleado_id"));
                if (empleado == null) continue;
                int id = rs.getInt("id");
                try {
                    LocalDateTime entrada = LocalDateTime.parse(rs.getString("entrada"));
                    Jornada j = new Jornada(id, empleado, entrada, Database.leerDinero(rs, "valor_hora"));
                    String salida = rs.getString("salida");
                    if (salida != null) j.setSalida(LocalDateTime.parse(salida));
                    resultado.add(j);
                } catch (DateTimeParseException e) {
                    throw new SQLException("La jornada " + id + " tiene una fecha con formato inválido.", e);
                }
            }
        }
        return resultado;
    }

    public Jornada insertar(Empleado empleado, LocalDateTime entrada) throws SQLException {
        try (Connection c = Database.getConnection()) {
            return insertar(c, empleado, entrada);
        }
    }

    /** Inserta una jornada abierta usando una conexión ya abierta (para participar de una transacción). */
    public Jornada insertar(Connection c, Empleado empleado, LocalDateTime entrada) throws SQLException {
        try {
            return insertar(c, empleado, entrada, null);
        } catch (AsistenciaException e) {
            throw new IllegalStateException(e);   // no ocurre: sin salida no se valida solapamiento
        }
    }

    /**
     * Inserta una jornada. Si trae entrada y salida, antes verifica (en la misma conexión/transacción)
     * que el rango no se superponga con otra jornada del mismo empleado.
     */
    public Jornada insertar(Connection c, Empleado empleado, LocalDateTime entrada, LocalDateTime salida)
            throws SQLException, AsistenciaException {
        if (salida != null) validarSinSolapamiento(c, empleado.getId(), 0, entrada, salida);
        String sql = "INSERT INTO jornadas(empleado_id, entrada, salida, valor_hora) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            BigDecimal valorHora = empleado.getValorHora();
            ps.setInt(1, empleado.getId());
            ps.setString(2, entrada.toString());
            if (salida == null) ps.setNull(3, Types.VARCHAR); else ps.setString(3, salida.toString());
            ps.setDouble(4, valorHora.doubleValue());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("No se pudo obtener el ID de la jornada");
                Jornada j = new Jornada(keys.getInt(1), empleado, entrada, valorHora);
                if (salida != null) j.setSalida(salida);
                return j;
            }
        }
    }

    public void registrarSalida(int jornadaId, LocalDateTime salida) throws SQLException {
        try (Connection c = Database.getConnection()) {
            registrarSalida(c, jornadaId, salida);
        }
    }

    /**
     * Cierra una jornada usando una conexión ya abierta. Solo actúa sobre jornadas que siguen abiertas:
     * una segunda ejecución (doble clic, operación repetida) no pisa la salida ya registrada.
     */
    public void registrarSalida(Connection c, int jornadaId, LocalDateTime salida) throws SQLException {
        String sql = "UPDATE jornadas SET salida = ? WHERE id = ? AND salida IS NULL";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, salida.toString()); ps.setInt(2, jornadaId);
            if (ps.executeUpdate() == 0) throw new SQLException("La jornada " + jornadaId + " no existe o ya estaba cerrada.");
        }
    }

    /**
     * Corrige entrada y salida de una jornada. Antes de modificar, y dentro de la misma transacción,
     * verifica que el nuevo rango no se superponga con otra jornada del mismo empleado
     * (salida == null se interpreta como "en curso": el rango no tiene fin).
     *
     * @throws AsistenciaException si el horario se superpone con un turno existente
     */
    public void actualizarHorarios(int jornadaId, LocalDateTime entrada, LocalDateTime salida)
            throws SQLException, AsistenciaException {
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                int empleadoId;
                try (PreparedStatement ps = c.prepareStatement("SELECT empleado_id FROM jornadas WHERE id = ?")) {
                    ps.setInt(1, jornadaId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("No se encontró la jornada " + jornadaId);
                        empleadoId = rs.getInt(1);
                    }
                }
                validarSinSolapamiento(c, empleadoId, jornadaId, entrada, salida);

                String sql = "UPDATE jornadas SET entrada = ?, salida = ? WHERE id = ?";
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setString(1, entrada.toString());
                    if (salida == null) ps.setNull(2, Types.VARCHAR); else ps.setString(2, salida.toString());
                    ps.setInt(3, jornadaId);
                    if (ps.executeUpdate() == 0) throw new SQLException("No se encontró la jornada " + jornadaId);
                }
                c.commit();
            } catch (SQLException | AsistenciaException | RuntimeException e) {
                try { c.rollback(); } catch (SQLException r) { Log.error("No se pudo revertir la transacción", r); }
                throw e;
            }
        }
    }

    /**
     * Dos rangos se superponen cuando: nueva_entrada &lt; salida_existente AND nueva_salida &gt; entrada_existente.
     * Una jornada existente sin salida (en curso) no tiene fin, y una nueva sin salida tampoco.
     * Rangos que solo se tocan en un extremo (una termina 16:00 y la otra empieza 16:00) NO se superponen.
     * Se compara el texto ISO-8601 guardado, que ordena igual que la fecha y hora.
     *
     * @param jornadaIdExcluida jornada que se está modificando (no se compara consigo misma); 0 si es nueva
     */
    private void validarSinSolapamiento(Connection c, int empleadoId, int jornadaIdExcluida,
                                        LocalDateTime entrada, LocalDateTime salida)
            throws SQLException, AsistenciaException {
        String sql = "SELECT 1 FROM jornadas "
                + "WHERE empleado_id = ? AND id <> ? "
                + "AND (salida IS NULL OR ? < salida) "
                + "AND (? IS NULL OR ? > entrada) "
                + "LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            String nuevaEntrada = entrada.toString();
            String nuevaSalida = salida == null ? null : salida.toString();
            ps.setInt(1, empleadoId);
            ps.setInt(2, jornadaIdExcluida);
            ps.setString(3, nuevaEntrada);
            if (nuevaSalida == null) { ps.setNull(4, Types.VARCHAR); ps.setNull(5, Types.VARCHAR); }
            else { ps.setString(4, nuevaSalida); ps.setString(5, nuevaSalida); }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Log.warn("Horario rechazado por superposición: empleado " + empleadoId + ", "
                            + entrada + " a " + (salida == null ? "en curso" : salida));
                    throw new AsistenciaException("El horario ingresado se superpone con un turno existente de este empleado");
                }
            }
        }
    }

    private Empleado buscarEmpleado(List<Empleado> empleados, int id) {
        for (Empleado e : empleados) if (e.getId() == id) return e;
        return null;
    }
}
