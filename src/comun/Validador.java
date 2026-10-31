package comun;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Reglas de validación de los datos de la cuenta.
 * El cliente las usa para avisar antes de enviar; el servidor las vuelve a aplicar
 * porque nunca se debe confiar en lo que manda el cliente.
 */
public final class Validador {
    private static final Pattern USUARIO = Pattern.compile("^[A-Za-z0-9_.-]{3,30}$");
    private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MIN_PASSWORD = 8;
    private static final int MAX_BYTES_PASSWORD = 72; // BCrypt ignora lo que pase de 72 bytes

    private Validador() {}

    /** Devuelve el motivo del error, o null si todo es válido. */
    public static String validarRegistro(String usuario, String nombre, String correo, String password) {
        if (usuario == null || !USUARIO.matcher(usuario).matches()) {
            return "El usuario debe tener de 3 a 30 caracteres (letras, números, punto, guion o guion bajo).";
        }
        if (nombre == null || nombre.isBlank() || nombre.length() > 80) {
            return "El nombre es obligatorio (máximo 80 caracteres).";
        }
        if (correo == null || correo.length() > 120 || !CORREO.matcher(correo).matches()) {
            return "El correo electrónico no es válido.";
        }
        return validarPassword(password);
    }

    public static String validarPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD) {
            return "La contraseña debe tener al menos " + MIN_PASSWORD + " caracteres.";
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES_PASSWORD) {
            return "La contraseña es demasiado larga.";
        }
        if (password.chars().noneMatch(Character::isLetter) || password.chars().noneMatch(Character::isDigit)) {
            return "La contraseña debe incluir letras y números.";
        }
        return null;
    }
}
