package servidor;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Salas de espera del modo multijugador. Viven en memoria: se pierden si el servidor se reinicia.
 * El anfitrión crea la sala y comparte su código; si el anfitrión sale, la sala se cierra para todos.
 */
public final class SalasMultijugador {
    public static final int MIN_JUGADORES = 2;
    public static final int MAX_JUGADORES = 6;
    public static final int CANTIDAD_MAPAS = 4;

    // Holgado a propósito: en el celular la app se detiene mientras el anfitrión comparte el código en otra app.
    static final long SIN_CONTACTO_MAX_MS = 5 * 60 * 1000L;

    // Todos los métodos son synchronized: una sala llena no debe aceptar a dos jugadores a la vez.
    private static final Map<String, Sala> SALAS = new HashMap<>();

    public record Vista(String codigo, int mapa, int capacidad, String anfitrion, List<String> jugadores) {}

    public record Resultado(boolean exito, String mensaje, Vista sala) {
        static Resultado ok(String mensaje, Vista sala) {
            return new Resultado(true, mensaje, sala);
        }

        static Resultado error(String mensaje) {
            return new Resultado(false, mensaje, null);
        }
    }

    private static final class Sala {
        private final String codigo;
        private final int mapa;
        private final int capacidad;
        private final String anfitrion;
        // usuario → momento de su última consulta, en orden de llegada
        private final Map<String, Long> jugadores = new LinkedHashMap<>();

        Sala(String codigo, int mapa, int capacidad, String anfitrion) {
            this.codigo = codigo;
            this.mapa = mapa;
            this.capacidad = capacidad;
            this.anfitrion = anfitrion;
            jugadores.put(anfitrion, System.currentTimeMillis());
        }

        Vista vista() {
            return new Vista(codigo, mapa, capacidad, anfitrion, List.copyOf(jugadores.keySet()));
        }
    }

    private SalasMultijugador() {}

    public static synchronized Resultado crear(String usuario, int mapa, int capacidad) {
        if (mapa < 1 || mapa > CANTIDAD_MAPAS) {
            return Resultado.error("Ese mapa no existe.");
        }
        if (capacidad < MIN_JUGADORES || capacidad > MAX_JUGADORES) {
            return Resultado.error("La partida debe ser de " + MIN_JUGADORES + " a " + MAX_JUGADORES + " jugadores.");
        }
        quitarAusentes(System.currentTimeMillis());
        sacarDeSalas(usuario);

        Sala sala = new Sala(CodigoSala.nuevo(SALAS::containsKey), mapa, capacidad, usuario);
        SALAS.put(sala.codigo, sala);
        return Resultado.ok("Partida creada.", sala.vista());
    }

    public static synchronized Resultado unirse(String usuario, String codigo) {
        quitarAusentes(System.currentTimeMillis());
        Sala sala = SALAS.get(CodigoSala.normalizar(codigo));
        if (sala == null) {
            return Resultado.error("No existe ninguna partida con ese código.");
        }
        if (!sala.jugadores.containsKey(usuario)) {
            if (sala.jugadores.size() >= sala.capacidad) {
                return Resultado.error("La partida ya está llena.");
            }
            sacarDeSalas(usuario);
        }
        sala.jugadores.put(usuario, System.currentTimeMillis());
        return Resultado.ok("Te uniste a la partida.", sala.vista());
    }

    /** Devuelve la sala tal como está ahora; cada consulta cuenta como señal de que el jugador sigue ahí. */
    public static synchronized Resultado consultar(String usuario, String codigo) {
        quitarAusentes(System.currentTimeMillis());
        Sala sala = SALAS.get(CodigoSala.normalizar(codigo));
        if (sala == null || !sala.jugadores.containsKey(usuario)) {
            return Resultado.error("Esta partida ya no está disponible.");
        }
        sala.jugadores.put(usuario, System.currentTimeMillis());
        return Resultado.ok("Sala actualizada.", sala.vista());
    }

    public static synchronized Resultado salir(String usuario, String codigo) {
        Sala sala = SALAS.get(CodigoSala.normalizar(codigo));
        if (sala != null) {
            sacar(usuario, sala);
        }
        return Resultado.ok("Saliste de la partida.", null);
    }

    /** Quien cerró la app sin salir deja de consultar: su lugar se libera para que no quede ocupado para siempre. */
    static synchronized void quitarAusentes(long ahora) {
        long limite = ahora - SIN_CONTACTO_MAX_MS;
        for (Sala sala : List.copyOf(SALAS.values())) {
            for (Map.Entry<String, Long> jugador : Map.copyOf(sala.jugadores).entrySet()) {
                if (jugador.getValue() < limite) {
                    sacar(jugador.getKey(), sala);
                }
            }
        }
    }

    /** Un jugador está en una sola sala a la vez. */
    private static void sacarDeSalas(String usuario) {
        List.copyOf(SALAS.values()).forEach(sala -> sacar(usuario, sala));
    }

    private static void sacar(String usuario, Sala sala) {
        if (sala.anfitrion.equals(usuario)) {
            SALAS.remove(sala.codigo);
        } else {
            sala.jugadores.remove(usuario);
        }
    }
}
