package comun;

import java.util.regex.Pattern;

/**
 * Reglas de validación de los datos de la cuenta.
 * El cliente las usa para avisar campo por campo mientras se registra; el servidor las vuelve a aplicar
 * porque nunca se debe confiar en lo que manda el cliente.
 * Cada método devuelve el motivo del error, o null si el dato es válido.
 */
public final class Validador {
    // Empieza por letra para que nunca se confunda con una opción del menú (0, 1, 2, 3...)
    private static final Pattern USUARIO = Pattern.compile("^[A-Za-z][A-Za-z0-9_.-]{2,29}$");
    private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int LARGO_PIN = 4;
    private static final Pattern PIN = Pattern.compile("^[0-9]{" + LARGO_PIN + "}$");

    private Validador() {}

    public static String validarRegistro(String usuario, String nombre, String correo, String password) {
        String problema = validarUsuario(usuario);
        if (problema == null) {
            problema = validarNombre(nombre);
        }
        if (problema == null) {
            problema = validarCorreo(correo);
        }
        if (problema == null) {
            problema = validarPassword(password);
        }
        return problema;
    }

    public static String validarUsuario(String usuario) {
        if (usuario == null || !USUARIO.matcher(usuario).matches()) {
            return "El usuario debe empezar por una letra y tener de 3 a 30 caracteres "
                    + "(letras, números, punto, guion o guion bajo). No puede ser un número como las opciones del menú.";
        }
        return null;
    }

    public static String validarNombre(String nombre) {
        if (nombre == null || nombre.isBlank() || nombre.length() > 80) {
            return "El nombre es obligatorio (máximo 80 caracteres).";
        }
        // Solo un espacio entre palabras: nada de espacios al inicio/final, dobles ni tabuladores
        if (!nombre.equals(nombre.trim()) || nombre.contains("  ")
                || nombre.chars().anyMatch(c -> Character.isWhitespace(c) && c != ' ')) {
            return "El nombre solo puede llevar un espacio entre palabras.";
        }
        if (nombre.chars().noneMatch(Character::isLetter)) {
            return "El nombre debe contener letras, no puede ser solo números como las opciones del menú.";
        }
        return null;
    }

    public static String validarCorreo(String correo) {
        if (correo == null || correo.length() > 120 || !CORREO.matcher(correo).matches()) {
            return "El correo electrónico no es válido.";
        }
        return null;
    }

    /** La contraseña es un PIN: exactamente 4 dígitos, sin letras ni otros caracteres. */
    public static String validarPassword(String password) {
        if (password == null || !PIN.matcher(password).matches()) {
            return "La contraseña debe ser un PIN de exactamente " + LARGO_PIN + " números.";
        }
        return null;
    }
}
