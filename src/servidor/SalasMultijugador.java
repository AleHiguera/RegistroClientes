package servidor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Salas de espera del modo multijugador. Viven en memoria: se pierden si el servidor se reinicia.
 * El anfitrión crea la sala y comparte su código; si el anfitrión sale, la sala se cierra para todos.
 * Cada jugador se marca como listo; cuando lo están todos (y son al menos dos) la partida comienza sola.
 * Desde entonces ya no entra nadie nuevo: solo vuelven quienes ya estaban dentro.
 * Al comenzar, cada jugador coloca los números iniciales de su propio tablero y lo finaliza; los dados no
 * se lanzan hasta que todos lo hayan hecho. Los tableros se guardan por usuario: nadie toca el de otro.
 */
public final class SalasMultijugador {
    public static final int MIN_JUGADORES = 2;
    public static final int MAX_JUGADORES = 6;
    public static final int CANTIDAD_MAPAS = 4;

    // Holgado a propósito: en el celular la app se detiene mientras el anfitrión comparte el código en otra app.
    static final long SIN_CONTACTO_MAX_MS = 5 * 60 * 1000L;

    // Todos los métodos son synchronized: una sala llena no debe aceptar a dos jugadores a la vez.
    private static final Map<String, Sala> SALAS = new HashMap<>();

    private static final List<Integer> NUMEROS_INICIALES = List.of(1, 2, 3, 4, 5, 6);

    /**
     * Lo que ve un jugador de la sala. [listos] y [finalizados] van en el mismo orden que [jugadores].
     * [miTablero] son los números iniciales de quien consulta (vacío si no ha finalizado): los ajenos no se envían.
     */
    public record Vista(String codigo, int mapa, int capacidad, String anfitrion, List<String> jugadores,
                        List<String> listos, boolean comenzada, List<String> finalizados, boolean configurada,
                        List<Integer> miTablero) {}

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
        private final Set<String> listos = new HashSet<>();
        private boolean comenzada;
        // usuario → sus números iniciales, en el orden de las celdas iniciales del mapa
        private final Map<String, List<Integer>> tableros = new HashMap<>();

        Sala(String codigo, int mapa, int capacidad, String anfitrion) {
            this.codigo = codigo;
            this.mapa = mapa;
            this.capacidad = capacidad;
            this.anfitrion = anfitrion;
            jugadores.put(anfitrion, System.currentTimeMillis());
        }

        Vista vista(String usuario) {
            List<String> enOrden = List.copyOf(jugadores.keySet());
            return new Vista(codigo, mapa, capacidad, anfitrion, enOrden,
                    enOrden.stream().filter(listos::contains).toList(), comenzada,
                    enOrden.stream().filter(tableros::containsKey).toList(),
                    // Se calcula: quien falta y se va deja de frenar a los demás.
                    comenzada && tableros.keySet().containsAll(jugadores.keySet()),
                    tableros.getOrDefault(usuario, List.of()));
        }

        /** La partida empieza en cuanto no falta nadie por confirmar. */
        void comenzarSiTodosListos() {
            if (!comenzada && jugadores.size() >= MIN_JUGADORES && listos.containsAll(jugadores.keySet())) {
                comenzada = true;
            }
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
        return Resultado.ok("Partida creada.", sala.vista(usuario));
    }

    public static synchronized Resultado unirse(String usuario, String codigo) {
        quitarAusentes(System.currentTimeMillis());
        Sala sala = SALAS.get(CodigoSala.normalizar(codigo));
        if (sala == null) {
            return Resultado.error("No existe ninguna partida con ese código.");
        }
        if (!sala.jugadores.containsKey(usuario)) {
            if (sala.comenzada) {
                return Resultado.error("La partida ya comenzó.");
            }
            if (sala.jugadores.size() >= sala.capacidad) {
                return Resultado.error("La partida ya está llena.");
            }
            sacarDeSalas(usuario);
        }
        sala.jugadores.put(usuario, System.currentTimeMillis());
        return Resultado.ok("Te uniste a la partida.", sala.vista(usuario));
    }

    /** Marca o desmarca al jugador como listo; si con eso ya están todos, la partida comienza. */
    public static synchronized Resultado marcarListo(String usuario, String codigo, boolean listo) {
        quitarAusentes(System.currentTimeMillis());
        Sala sala = SALAS.get(CodigoSala.normalizar(codigo));
        if (sala == null || !sala.jugadores.containsKey(usuario)) {
            return Resultado.error("Esta partida ya no está disponible.");
        }
        sala.jugadores.put(usuario, System.currentTimeMillis());
        if (sala.comenzada) {
            return Resultado.ok("La partida ya comenzó.", sala.vista(usuario));
        }
        if (listo) {
            sala.listos.add(usuario);
        } else {
            sala.listos.remove(usuario);
        }
        sala.comenzarSiTodosListos();
        return Resultado.ok(sala.comenzada ? "La partida comenzó." : "Estado actualizado.", sala.vista(usuario));
    }

    /**
     * Guarda los números iniciales de quien llama y da su tablero por terminado. El tablero siempre es el del
     * propio [usuario], y una vez finalizado ya no cambia: un reintento se responde sin tocar nada.
     * Aquí solo se revisa que sean del 1 al 6 sin repetir; las reglas de cada zona del mapa las aplica la app.
     */
    public static synchronized Resultado finalizarTablero(String usuario, String codigo, String numeros) {
        quitarAusentes(System.currentTimeMillis());
        Sala sala = SALAS.get(CodigoSala.normalizar(codigo));
        if (sala == null || !sala.jugadores.containsKey(usuario)) {
            return Resultado.error("Esta partida ya no está disponible.");
        }
        sala.jugadores.put(usuario, System.currentTimeMillis());
        if (!sala.comenzada) {
            return Resultado.error("La partida todavía no comienza.");
        }
        if (sala.tableros.containsKey(usuario)) {
            return Resultado.ok("Tu tablero ya estaba finalizado.", sala.vista(usuario));
        }
        List<Integer> leidos = leerNumeros(numeros);
        if (leidos == null) {
            return Resultado.error("Los números iniciales deben ser del 1 al 6, sin repetir.");
        }
        sala.tableros.put(usuario, leidos);
        return Resultado.ok("Tablero finalizado.", sala.vista(usuario));
    }

    /** Los números separados por comas, o null si no son exactamente del 1 al 6 sin repetir. */
    private static List<Integer> leerNumeros(String numeros) {
        try {
            List<Integer> leidos = Arrays.stream(numeros.split(",", -1)).map(Integer::valueOf).toList();
            return leidos.stream().sorted().toList().equals(NUMEROS_INICIALES) ? leidos : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Devuelve la sala tal como está ahora; cada consulta cuenta como señal de que el jugador sigue ahí. */
    public static synchronized Resultado consultar(String usuario, String codigo) {
        quitarAusentes(System.currentTimeMillis());
        Sala sala = SALAS.get(CodigoSala.normalizar(codigo));
        if (sala == null || !sala.jugadores.containsKey(usuario)) {
            return Resultado.error("Esta partida ya no está disponible.");
        }
        sala.jugadores.put(usuario, System.currentTimeMillis());
        return Resultado.ok("Sala actualizada.", sala.vista(usuario));
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
        } else if (sala.jugadores.remove(usuario) != null) {
            sala.listos.remove(usuario);
            sala.tableros.remove(usuario);
            // Si el que se fue era el único que faltaba por confirmar, ya están todos.
            sala.comenzarSiTodosListos();
        }
    }
}
