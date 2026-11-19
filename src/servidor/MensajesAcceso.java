package servidor;

/**
 * Lo que se le dice a alguien que no pudo entrar. Explica el motivo concreto para que sepa qué corregir: si el
 * usuario no existe, si el PIN no es el suyo o si ya hay una sesión abierta con esa cuenta.
 *
 * Decir que un usuario no existe no revela nada nuevo: al crear una cuenta ya se avisa si el nombre está en uso. Los
 * intentos fallidos siguen bloqueando la cuenta un rato, así que adivinar el PIN sigue siendo lento.
 */
final class MensajesAcceso {
    private MensajesAcceso() {}

    static String usuarioInexistente(String usuario) {
        return "El usuario \"" + usuario + "\" no existe. Revisa cómo lo escribiste o crea una cuenta.";
    }

    static String pinIncorrecto() {
        return "El PIN es incorrecto. Revisa los 4 números e inténtalo de nuevo.";
    }

    /** Por qué falló un intento con ese [usuario], según si la cuenta existe. */
    static String fallo(boolean existe, String usuario) {
        return existe ? pinIncorrecto() : usuarioInexistente(usuario);
    }

    static String yaConectado(String usuario) {
        return "El usuario \"" + usuario + "\" ya está conectado en otro dispositivo. Cierra esa sesión para entrar "
                + "aquí.";
    }
}
