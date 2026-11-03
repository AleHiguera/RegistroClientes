package comun;

import java.util.regex.Pattern;

/**
 * Reglas de validación de los datos de la cuenta.
 * El cliente las usa para avisar antes de enviar; el servidor las vuelve a aplicar
 * porque nunca se debe confiar en lo que manda el cliente.
 */
public final class Validador {
    private static final Pattern USUARIO = Pattern.compile("^[A-Za-z0-9_.-]{3,30}$");
    private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int LARGO_PIN = 4;
    private static final Pattern PIN = Pattern.compile("^[0-9]{" + LARGO_PIN + "}$");

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

    /** La contraseña es un PIN: exactamente 4 dígitos, sin letras ni otros caracteres. */
    public static String validarPassword(String password) {
        if (password == null || !PIN.matcher(password).matches()) {
            return "La contraseña debe ser un PIN de exactamente " + LARGO_PIN + " números.";
        }
        return null;
    }
}
