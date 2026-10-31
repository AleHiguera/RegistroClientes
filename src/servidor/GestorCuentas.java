package servidor;

import comun.Validador;
import org.mindrot.jbcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Alta, autenticación y baja de cuentas. Las contraseñas se guardan con BCrypt (hash + sal). */
public final class GestorCuentas {
    private static final int COSTO_BCRYPT = 12;
    private static final int MAX_INTENTOS = 5;
    private static final long BLOQUEO_MS = 5 * 60 * 1000L;
    private static final int SQLITE_CONSTRAINT = 19;

    // Se compara contra este hash cuando el usuario no existe, para que la respuesta tarde lo mismo
    // y no se pueda averiguar qué usuarios existen midiendo el tiempo.
    private static final String HASH_FICTICIO = BCrypt.hashpw("usuario-inexistente", BCrypt.gensalt(COSTO_BCRYPT));

    private static final Map<String, Intentos> INTENTOS_FALLIDOS = new ConcurrentHashMap<>();

    public record Resultado(boolean exito, String mensaje, String usuario) {
        static Resultado ok(String mensaje, String usuario) {
            return new Resultado(true, mensaje, usuario);
        }

        static Resultado error(String mensaje) {
            return new Resultado(false, mensaje, null);
        }
    }

    public record Perfil(String usuario, String nombre, String correo, String fechaRegistro) {}

    private record Credencial(String usuario, String hash) {}

    private GestorCuentas() {}

    public static Resultado registrar(String usuario, String nombre, String correo, String password) {
        usuario = usuario.trim();
        nombre = nombre.trim();
        correo = correo.trim();

        String problema = Validador.validarRegistro(usuario, nombre, correo, password);
        if (problema != null) {
            return Resultado.error(problema);
        }

        String hash = BCrypt.hashpw(password, BCrypt.gensalt(COSTO_BCRYPT));
        String sql = "INSERT INTO clientes (usuario, nombre, correo, password_hash) VALUES (?, ?, ?, ?)";
        try (Connection conn = BaseDatos.conectar();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, usuario);
            pstmt.setString(2, nombre);
            pstmt.setString(3, correo);
            pstmt.setString(4, hash);
            pstmt.executeUpdate();
            return Resultado.ok("Cuenta creada correctamente.", usuario);

        } catch (SQLException e) {
            if (e.getErrorCode() == SQLITE_CONSTRAINT) {
                return Resultado.error("El usuario o el correo ya están registrados.");
            }
            System.err.println("[DB] Error al registrar: " + e.getMessage());
            return Resultado.error("Error interno del servidor.");
        }
    }

    public static Resultado iniciarSesion(String usuario, String password) {
        return comprobarCredenciales(usuario, password);
    }

    /** Pide de nuevo la contraseña antes de borrar, para que nadie borre la cuenta con una sesión olvidada abierta. */
    public static Resultado borrarCuenta(String usuario, String password) {
        Resultado verificacion = comprobarCredenciales(usuario, password);
        if (!verificacion.exito()) {
            return verificacion;
        }

        try (Connection conn = BaseDatos.conectar();
             PreparedStatement pstmt = conn.prepareStatement("DELETE FROM clientes WHERE usuario = ?")) {

            pstmt.setString(1, verificacion.usuario());
            if (pstmt.executeUpdate() == 0) {
                return Resultado.error("La cuenta ya no existe.");
            }
            return Resultado.ok("Tu cuenta se borró correctamente.", verificacion.usuario());

        } catch (SQLException e) {
            System.err.println("[DB] Error al borrar cuenta: " + e.getMessage());
            return Resultado.error("Error interno del servidor.");
        }
    }

    public static Perfil obtenerPerfil(String usuario) {
        String sql = "SELECT usuario, nombre, correo, fecha_registro FROM clientes WHERE usuario = ?";
        try (Connection conn = BaseDatos.conectar();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, usuario);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new Perfil(rs.getString("usuario"), rs.getString("nombre"),
                        rs.getString("correo"), rs.getString("fecha_registro"));
            }
            return null;
        } catch (SQLException e) {
            System.err.println("[DB] Error al leer perfil: " + e.getMessage());
            return null;
        }
    }

    private static Resultado comprobarCredenciales(String usuario, String password) {
        String clave = usuario.trim().toLowerCase(Locale.ROOT);

        Intentos intentos = INTENTOS_FALLIDOS.get(clave);
        if (intentos != null && intentos.estaBloqueado()) {
            return Resultado.error("Demasiados intentos fallidos. Espera " + intentos.minutosRestantes()
                    + " min e inténtalo de nuevo.");
        }

        Credencial credencial;
        try {
            credencial = buscarCredencial(usuario.trim());
        } catch (SQLException e) {
            System.err.println("[DB] Error al verificar credenciales: " + e.getMessage());
            return Resultado.error("Error interno del servidor.");
        }

        boolean coincide = BCrypt.checkpw(password, credencial != null ? credencial.hash() : HASH_FICTICIO);
        if (credencial != null && coincide) {
            INTENTOS_FALLIDOS.remove(clave);
            return Resultado.ok("Sesión iniciada.", credencial.usuario());
        }

        INTENTOS_FALLIDOS.computeIfAbsent(clave, k -> new Intentos()).registrarFallo();
        // Mismo mensaje exista o no el usuario, para no dar pistas
        return Resultado.error("Usuario o contraseña incorrectos.");
    }

    private static Credencial buscarCredencial(String usuario) throws SQLException {
        try (Connection conn = BaseDatos.conectar();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT usuario, password_hash FROM clientes WHERE usuario = ?")) {

            pstmt.setString(1, usuario);
            ResultSet rs = pstmt.executeQuery();
            return rs.next() ? new Credencial(rs.getString("usuario"), rs.getString("password_hash")) : null;
        }
    }

    /** Cuenta los fallos de un usuario y lo bloquea un rato al llegar al máximo (frena ataques de fuerza bruta). */
    private static final class Intentos {
        private int fallos;
        private long bloqueadoHasta;

        synchronized void registrarFallo() {
            if (++fallos >= MAX_INTENTOS) {
                bloqueadoHasta = System.currentTimeMillis() + BLOQUEO_MS;
                fallos = 0;
            }
        }

        synchronized boolean estaBloqueado() {
            return System.currentTimeMillis() < bloqueadoHasta;
        }

        synchronized long minutosRestantes() {
            long restante = bloqueadoHasta - System.currentTimeMillis();
            return Math.max(1, (restante + 59_999) / 60_000);
        }
    }
}
