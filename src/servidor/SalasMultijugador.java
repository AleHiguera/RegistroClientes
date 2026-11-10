package servidor;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Salas de espera del modo multijugador. Viven en memoria: se pierden si el servidor se reinicia.
 * El anfitrión crea la sala y comparte su código; si sale antes de comenzar, la sala se cierra para todos.
 * Ya comenzada, el anfitrión es un jugador más: la partida sigue hasta que no quede nadie.
 * Cada jugador se marca como listo; cuando lo están todos (y son al menos dos) la partida comienza sola.
 * Desde entonces ya no entra nadie nuevo: solo vuelven quienes ya estaban dentro.
 * Al comenzar, cada jugador coloca los números iniciales de su propio tablero y lo finaliza; los dados no
 * se lanzan hasta que todos lo hayan hecho. Los tableros se guardan por usuario: nadie toca el de otro.
 * Los dados los lanza solo el servidor, un único resultado por ronda para toda la partida. La siguiente
 * ronda se lanza cuando todos jugaron la actual; quien llenó su tablero ya no hace esperar a nadie.
 * También se registra en qué ronda completa cada jugador cada zona; los puntos los calcula la app, que
 * conoce los mapas. El registro no se borra cuando alguien sale: su lugar queda ocupado y nadie sube.
 * Al terminar cada ronda se revisa si alguien llegó a la meta (según cuántos empezaron) o si ya nadie puede
 * jugar; entonces la partida termina y gana quien tenga más puntos, o todos los empatados.
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
    private static final SecureRandom DADO = new SecureRandom();

    /**
     * Lo que ve un jugador de la sala. [listos] y [finalizados] van en el mismo orden que [jugadores].
     * [miTablero] son los números iniciales de quien consulta (vacío si no ha finalizado): los ajenos no se envían.
     * [ronda] vale 0 hasta el primer lanzamiento, y entonces [dados] está vacío. [jugaron] son quienes ya no
     * deben nada en esta ronda, incluidos los que llenaron su tablero. [zonas] dice, por número de zona, en
     * qué ronda la completó cada jugador.
     */
    public record Vista(String codigo, int mapa, int capacidad, String anfitrion, List<String> jugadores,
                        List<String> listos, boolean comenzada, List<String> finalizados, boolean configurada,
                        List<Integer> miTablero, int ronda, List<Integer> dados, List<String> jugaron,
                        Map<Integer, Map<String, Integer>> zonas, int meta, boolean terminada,
                        List<String> ganadores) {}

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
        private int ronda;
        private List<Integer> dados = List.of();
        private final Set<String> jugaron = new HashSet<>();
        private final Set<String> tablerosLlenos = new HashSet<>();
        // número de zona → usuario → ronda en que la completó
        private final Map<Integer, Map<String, Integer>> zonas = new TreeMap<>();
        // La meta se fija con los que empezaron y no cambia aunque alguien salga.
        private int jugadoresAlComenzar;
        private boolean terminada;
        private List<String> ganadores = List.of();

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
                    tablerosConfigurados(),
                    tableros.getOrDefault(usuario, List.of()), ronda, dados,
                    enOrden.stream().filter(this::yaNoDebeNada).toList(), copiaDeZonas(),
                    comenzada ? meta() : 0, terminada, ganadores);
        }

        private Map<Integer, Map<String, Integer>> copiaDeZonas() {
            Map<Integer, Map<String, Integer>> copia = new TreeMap<>();
            zonas.forEach((zona, rondas) -> copia.put(zona, new LinkedHashMap<>(rondas)));
            return copia;
        }

        boolean tablerosConfigurados() {
            return comenzada && tableros.keySet().containsAll(jugadores.keySet());
        }

        boolean yaNoDebeNada(String jugador) {
            return jugaron.contains(jugador) || tablerosLlenos.contains(jugador);
        }

        int meta() {
            return Puntuacion.metaPara(jugadoresAlComenzar);
        }

        /**
         * Cuando no se espera a nadie: si alguien llegó a la meta o ya nadie puede jugar, la partida termina; si no,
         * se lanza la siguiente ronda. Se revisa al final de la ronda, nunca a mitad: como todos juegan con los
         * mismos dados, terminarla antes premiaría a quien confirma más rápido.
         */
        void avanzarRonda() {
            boolean rondaTerminada = ronda == 0 || jugadores.keySet().stream().allMatch(this::yaNoDebeNada);
            if (terminada || !tablerosConfigurados() || !rondaTerminada) {
                return;
            }
            if (ronda > 0) {
                Map<String, Integer> puntos = Puntuacion.puntos(mapa, zonas);
                boolean alguienLlego = puntos.values().stream().anyMatch(total -> total >= meta());
                boolean nadiePuedeJugar = tablerosLlenos.containsAll(jugadores.keySet());
                if (alguienLlego || nadiePuedeJugar) {
                    terminada = true;
                    ganadores = Puntuacion.ganadores(puntos, jugadores.keySet());
                    return;
                }
            }
            ronda++;
            dados = List.of(DADO.nextInt(6) + 1, DADO.nextInt(6) + 1);
            jugaron.clear();
        }

        /** La partida empieza en cuanto no falta nadie por confirmar. */
        void comenzarSiTodosListos() {
            if (!comenzada && jugadores.size() >= MIN_JUGADORES && listos.containsAll(jugadores.keySet())) {
                comenzada = true;
                jugadoresAlComenzar = jugadores.size();
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
        sala.avanzarRonda();
        return Resultado.ok("Tablero finalizado.", sala.vista(usuario));
    }

    /**
     * El jugador ya movió o pasó en [ronda]; con [tableroLleno] ya no jugará más rondas. Un aviso de otra ronda
     * (un reintento que llegó tarde) no cuenta, para que no se dé por jugada una ronda que ni siquiera vio.
     */
    public static synchronized Resultado terminarTurno(String usuario, String codigo, int ronda, boolean tableroLleno) {
        return terminarTurno(usuario, codigo, ronda, tableroLleno, null);
    }

    /**
     * Como el anterior, y además registra la zona que completó su movimiento, si completó una. Un jugador solo
     * termina una vez cada ronda, así que tampoco puede declarar más de una zona por ronda.
     */
    public static synchronized Resultado terminarTurno(String usuario, String codigo, int ronda, boolean tableroLleno,
                                                       Integer zona) {
        quitarAusentes(System.currentTimeMillis());
        Sala sala = SALAS.get(CodigoSala.normalizar(codigo));
        if (sala == null || !sala.jugadores.containsKey(usuario)) {
            return Resultado.error("Esta partida ya no está disponible.");
        }
        sala.jugadores.put(usuario, System.currentTimeMillis());
        if (ronda == 0 || ronda != sala.ronda) {
            return Resultado.ok("Esa ronda no está en juego.", sala.vista(usuario));
        }
        if (sala.terminada) {
            return Resultado.ok("La partida terminó.", sala.vista(usuario));
        }
        if (zona != null && (zona < 1 || zona > PuntosPorMapa.cantidadDeZonas(sala.mapa))) {
            return Resultado.error("Esa zona no existe.");
        }
        if (sala.jugaron.contains(usuario)) {
            return Resultado.ok("Tu turno ya estaba terminado.", sala.vista(usuario));
        }
        sala.jugaron.add(usuario);
        if (zona != null) {
            sala.zonas.computeIfAbsent(zona, nueva -> new LinkedHashMap<>()).putIfAbsent(usuario, ronda);
        }
        if (tableroLleno) {
            sala.tablerosLlenos.add(usuario);
        }
        sala.avanzarRonda();
        return Resultado.ok("Turno terminado.", sala.vista(usuario));
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
        if (sala.anfitrion.equals(usuario) && !sala.comenzada) {
            SALAS.remove(sala.codigo);
        } else if (sala.jugadores.remove(usuario) != null) {
            sala.listos.remove(usuario);
            sala.tableros.remove(usuario);
            sala.jugaron.remove(usuario);
            sala.tablerosLlenos.remove(usuario);
            if (sala.jugadores.isEmpty()) {
                SALAS.remove(sala.codigo);
                return;
            }
            // Si el que se fue era el único al que se esperaba, ya no hay por qué seguir esperando.
            sala.comenzarSiTodosListos();
            sala.avanzarRonda();
        }
    }
}
