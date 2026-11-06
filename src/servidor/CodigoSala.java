package servidor;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.function.Predicate;

/** Códigos de acceso de las salas: fáciles de dictar y de escribir en el celular. */
final class CodigoSala {
    static final String LETRAS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // sin 0/O ni 1/I, que se confunden
    static final int LARGO = 6;

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private CodigoSala() {}

    /** Genera un código que no esté en uso. */
    static String nuevo(Predicate<String> enUso) {
        String codigo;
        do {
            StringBuilder letras = new StringBuilder(LARGO);
            for (int i = 0; i < LARGO; i++) {
                letras.append(LETRAS.charAt(ALEATORIO.nextInt(LETRAS.length())));
            }
            codigo = letras.toString();
        } while (enUso.test(codigo));
        return codigo;
    }

    /** El código tal como se guarda, sin importar cómo lo escribió el jugador. */
    static String normalizar(String codigo) {
        return codigo.trim().toUpperCase(Locale.ROOT);
    }
}
