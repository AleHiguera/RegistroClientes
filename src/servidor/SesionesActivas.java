package servidor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Usuarios conectados en este momento, por la consola o por la web. Solo se permite una sesión por usuario. */
public final class SesionesActivas {
    public interface Sesion {
        /** Una sesión caducada conserva su lugar hasta que otra lo ocupa. */
        boolean caducada();
    }

    private static final Map<String, Sesion> ABIERTAS = new ConcurrentHashMap<>();

    private SesionesActivas() {}

    /** Reserva el usuario para esta sesión. Devuelve false si ya está conectado en otra parte. */
    public static boolean ocupar(String usuario, Sesion sesion) {
        return ABIERTAS.compute(usuario,
                (clave, actual) -> actual == null || actual.caducada() ? sesion : actual) == sesion;
    }

    public static boolean ocupa(String usuario, Sesion sesion) {
        return ABIERTAS.get(usuario) == sesion;
    }

    public static void liberar(String usuario, Sesion sesion) {
        ABIERTAS.remove(usuario, sesion);
    }

    public static String mensajeOcupado(String usuario) {
        return "El usuario \"" + usuario + "\" ya está conectado, intenta con otro.";
    }
}
