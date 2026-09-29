PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS empleados (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nombre TEXT NOT NULL,
    valor_hora REAL NOT NULL CHECK(valor_hora > 0),
    activo INTEGER NOT NULL DEFAULT 1,
    fecha_alta TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS jornadas (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    empleado_id INTEGER NOT NULL,
    entrada TEXT NOT NULL,
    salida TEXT,
    valor_hora REAL NOT NULL CHECK(valor_hora > 0),
    FOREIGN KEY (empleado_id) REFERENCES empleados(id) ON DELETE RESTRICT
);
