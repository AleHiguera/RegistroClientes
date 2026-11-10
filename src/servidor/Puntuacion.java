package servidor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Puntos de una partida multijugador y quién la gana. Cada zona reparte sus lugares por ronda: quienes la completan
 * en la misma ronda empatan, y un empate no gasta lugares. Del cuarto lugar en adelante no hay puntos.
 */
final class Puntuacion {
    private Puntuacion() {}

    /** Puntos que hay que alcanzar para ganar, según cuántos jugadores empezaron. Es la misma en todos los mapas. */
    static int metaPara(int jugadores) {
        return switch (jugadores) {
            case 0, 1, 2 -> 26;
            case 3 -> 24;
            case 4 -> 22;
            default -> 20;
        };
    }

    /** El total de cada jugador que completó alguna zona, a partir de en qué ronda la completó. */
    static Map<String, Integer> puntos(int mapa, Map<Integer, Map<String, Integer>> rondasPorZona) {
        Map<String, Integer> totales = new HashMap<>();
        rondasPorZona.forEach((zona, rondas) -> {
            if (zona < 1 || zona > PuntosPorMapa.cantidadDeZonas(mapa)) {
                return;
            }
            rondas.forEach((jugador, ronda) -> {
                int lugar = (int) rondas.values().stream().filter(otra -> otra < ronda).distinct().count() + 1;
                totales.merge(jugador, PuntosPorMapa.puntos(mapa, zona, lugar), Integer::sum);
            });
        });
        return totales;
    }

    /**
     * Quienes tienen más puntos; varios si empatan. Se busca entre quienes siguen en la partida y quienes tienen
     * puntos aunque hayan salido, en ese orden.
     */
    static List<String> ganadores(Map<String, Integer> puntos, Collection<String> jugadores) {
        Set<String> todos = new LinkedHashSet<>(jugadores);
        todos.addAll(puntos.keySet());
        int maximo = todos.stream().mapToInt(jugador -> puntos.getOrDefault(jugador, 0)).max().orElse(0);
        List<String> ganadores = new ArrayList<>();
        for (String jugador : todos) {
            if (puntos.getOrDefault(jugador, 0) == maximo) {
                ganadores.add(jugador);
            }
        }
        return List.copyOf(ganadores);
    }
}
