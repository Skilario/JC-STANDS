package com.mycompany.jcstands;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class EmpleadoDAO {

    public List<Empleado> listar() throws SQLException {
        return listarPorEstado(true);
    }

    public List<Empleado> listarTodos() throws SQLException {
        return listarPorEstado(false);
    }

    private List<Empleado> listarPorEstado(boolean soloActivos) throws SQLException {
        String sql = soloActivos
                ? "SELECT id, nombre, valor_hora FROM empleados WHERE activo = 1 ORDER BY id"
                : "SELECT id, nombre, valor_hora FROM empleados ORDER BY id";
        List<Empleado> resultado = new ArrayList<>();

        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                resultado.add(new Empleado(
                        rs.getInt("id"),
                        rs.getString("nombre"),
                        Database.leerDinero(rs, "valor_hora")
                ));
            }
        }
        return resultado;
    }

    public Empleado insertar(String nombre, BigDecimal valorHora) throws SQLException {
        String sql = "INSERT INTO empleados(nombre, valor_hora) VALUES (?, ?)";

        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, nombre);
            ps.setDouble(2, valorHora.doubleValue());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No se pudo obtener el ID del empleado");
                }
                return new Empleado(keys.getInt(1), nombre, valorHora);
            }
        }
    }

    public void actualizarValorHora(int id, BigDecimal valorHora) throws SQLException {
        String sql = "UPDATE empleados SET valor_hora = ? WHERE id = ?";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDouble(1, valorHora.doubleValue());
            ps.setInt(2, id);
            if (ps.executeUpdate() == 0) throw new SQLException("No se encontró el empleado " + id);
        }
    }

    /**
     * Baja lógica. La propia sentencia SQL se niega a dar de baja a un empleado con una jornada
     * abierta (la condición se evalúa dentro de la base, no solo en memoria).
     *
     * @return false si no se dio de baja (ya estaba inactivo, no existe o tiene una jornada abierta).
     */
    public boolean desactivar(int id) throws SQLException {
        String sql = "UPDATE empleados SET activo = 0 WHERE id = ? AND activo = 1 "
                + "AND NOT EXISTS (SELECT 1 FROM jornadas j WHERE j.empleado_id = empleados.id AND j.salida IS NULL)";
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    /** true si el empleado tiene una jornada abierta según la base de datos. */
    public boolean tieneJornadaAbierta(int id) throws SQLException {
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM jornadas WHERE empleado_id = ? AND salida IS NULL LIMIT 1")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }
}
