package servidor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class BaseDatos {
    private static final String ARCHIVO = "clientes.db";
    private static final String URL = "jdbc:sqlite:" + ARCHIVO;

    private BaseDatos() {}

    public static Connection conectar() throws SQLException {
        return DriverManager.getConnection(URL);
    }

    /** Si la base de datos no existe la crea; si existe, solo se asegura de que esté la tabla. */
    public static void inicializar() throws SQLException {
        boolean existia = Files.exists(Path.of(ARCHIVO));

        try (Connection conn = conectar();
             Statement stmt = conn.createStatement()) {

            // Solo se guarda el hash de la contraseña, nunca la contraseña
            stmt.execute("CREATE TABLE IF NOT EXISTS clientes (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "usuario TEXT NOT NULL UNIQUE COLLATE NOCASE," +
                    "nombre TEXT NOT NULL," +
                    "correo TEXT NOT NULL UNIQUE COLLATE NOCASE," +
                    "password_hash TEXT NOT NULL," +
                    "fecha_registro TEXT NOT NULL DEFAULT (datetime('now', 'localtime')))");
        }

        System.out.println(existia
                ? "Base de datos encontrada: " + ARCHIVO
                : "Base de datos no encontrada, se creó: " + ARCHIVO);
    }
}
