package servidor;

import static servidor.Pruebas.caso;
import static servidor.Pruebas.cierto;
import static servidor.Pruebas.iguales;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class PruebasPuntuacion {
    /**
     * Los mismos casos que test/ganadores_compartidos_test.dart de brilliantdart: la app arma la clasificación con
     * su propio código y debe dar los mismos ganadores. Si se cambia uno, hay que cambiar el otro.
     * Cada caso es: mapa | zona:jugador=ronda,...;zona:... | jugadores | ganadores
     */
    static final List<String> CASOS_COMPARTIDOS = List.of(
            "1|1:ana=1,luis=2;3:luis=1,eva=1|ana,luis,eva|luis",
            "1|1:ana=1,luis=1|ana,luis,eva|ana,luis",
            "1|1:salio=1|ana|salio",
            "1||ana,luis|ana,luis",
            "3|3:eva=2,ana=5;6:ana=1|ana,eva|ana",
            "4|2:a=1,b=1,c=2;4:c=1|a,b,c|c",
            "1|1:a=1,b=2,c=3,d=4;9:d=1|a,b,c,d|a");

    private PruebasPuntuacion() {}

    private static Map<Integer, Map<String, Integer>> zonas(String texto) {
        Map<Integer, Map<String, Integer>> zonas = new TreeMap<>();
        if (texto.isEmpty()) {
            return zonas;
        }
        for (String zona : texto.split(";")) {
            String[] partes = zona.split(":");
            Map<String, Integer> rondas = new LinkedHashMap<>();
            for (String par : partes[1].split(",")) {
                String[] jugadorYRonda = par.split("=");
                rondas.put(jugadorYRonda[0], Integer.valueOf(jugadorYRonda[1]));
            }
            zonas.put(Integer.valueOf(partes[0]), rondas);
        }
        return zonas;
    }

    static void ejecutar() {
        caso("La meta depende de cuántos jugadores empezaron", () -> {
            iguales(26, Puntuacion.metaPara(2));
            iguales(24, Puntuacion.metaPara(3));
            iguales(22, Puntuacion.metaPara(4));
            iguales(20, Puntuacion.metaPara(5));
            iguales(20, Puntuacion.metaPara(6));
        });

        caso("Cada mapa tiene sus zonas con sus puntos", () -> {
            iguales(9, PuntosPorMapa.cantidadDeZonas(1));
            iguales(10, PuntosPorMapa.cantidadDeZonas(2));
            iguales(8, PuntosPorMapa.cantidadDeZonas(3));
            iguales(8, PuntosPorMapa.cantidadDeZonas(4));
            iguales(0, PuntosPorMapa.cantidadDeZonas(5));
            // Mapa 1, zona 1: la amarilla. Mapa 3, zona 3: la gris.
            iguales(List.of(8, 6, 4, 0), List.of(PuntosPorMapa.puntos(1, 1, 1), PuntosPorMapa.puntos(1, 1, 2),
                    PuntosPorMapa.puntos(1, 1, 3), PuntosPorMapa.puntos(1, 1, 4)));
            iguales(8, PuntosPorMapa.puntos(3, 3, 1));
        });

        caso("Los puntos siguen el orden de las rondas", () ->
                iguales(Map.of("a", 8, "b", 6, "c", 4, "d", 0),
                        Puntuacion.puntos(1, Map.of(1, Map.of("a", 2, "b", 4, "c", 6, "d", 9)))));

        caso("Los empatados reciben los puntos completos y no gastan lugares", () ->
                iguales(Map.of("a", 8, "b", 8, "c", 6),
                        Puntuacion.puntos(1, Map.of(1, Map.of("a", 3, "b", 3, "c", 5)))));

        caso("Cada jugador suma sus zonas", () ->
                iguales(Map.of("a", 8 + 7, "b", 6),
                        Puntuacion.puntos(1, Map.of(1, Map.of("a", 1, "b", 2), 3, Map.of("a", 4)))));

        caso("Una zona que el mapa no tiene no da puntos", () ->
                iguales(Map.of(), Puntuacion.puntos(1, Map.of(99, Map.of("a", 1)))));

        caso("Gana quien tiene más puntos", () ->
                iguales(List.of("b"), Puntuacion.ganadores(Map.of("a", 20, "b", 27), List.of("a", "b"))));

        caso("Si empatan en puntos, ganan todos los empatados", () ->
                iguales(List.of("a", "c"),
                        Puntuacion.ganadores(Map.of("a", 27, "b", 3, "c", 27), List.of("a", "b", "c"))));

        caso("Quien salió también puede tener más puntos", () ->
                iguales(List.of("salio"), Puntuacion.ganadores(Map.of("salio", 9, "a", 2), List.of("a"))));

        caso("Ejemplo A: con 3 jugadores, 24 puntos alcanzan la meta y ganan", () -> {
            var puntos = Map.of("jugador1", 24, "jugador2", 21, "jugador3", 17);
            iguales(24, Puntuacion.metaPara(3));
            cierto(puntos.get("jugador1") >= Puntuacion.metaPara(3), "24 no alcanzó la meta");
            cierto(puntos.get("jugador2") < Puntuacion.metaPara(3), "21 alcanzó la meta");
            iguales(List.of("jugador1"),
                    Puntuacion.ganadores(puntos, List.of("jugador1", "jugador2", "jugador3")));
        });

        caso("Ejemplo B: con 5 jugadores, 20 puntos alcanzan la meta y ganan", () -> {
            var puntos = Map.of("jugador1", 19, "jugador2", 13, "jugador3", 20, "jugador4", 16, "jugador5", 10);
            iguales(20, Puntuacion.metaPara(5));
            cierto(puntos.get("jugador1") < Puntuacion.metaPara(5), "19 alcanzó la meta");
            iguales(List.of("jugador3"), Puntuacion.ganadores(puntos,
                    List.of("jugador1", "jugador2", "jugador3", "jugador4", "jugador5")));
        });

        for (String texto : CASOS_COMPARTIDOS) {
            caso("Coincide con la app: " + texto, () -> {
                // split con -1 conserva la parte vacía de "sin zonas".
                String[] partes = texto.split("\\|", -1);
                int mapa = Integer.parseInt(partes[0]);
                var puntos = Puntuacion.puntos(mapa, zonas(partes[1]));
                iguales(List.of(partes[3].split(",")),
                        Puntuacion.ganadores(puntos, List.of(partes[2].split(","))));
            });
        }

        caso("Se lee bien un caso compartido", () ->
                iguales(Map.of(1, Map.of("ana", 1, "luis", 2), 3, Map.of("luis", 1)),
                        zonas("1:ana=1,luis=2;3:luis=1")));

        caso("Si nadie tiene puntos, empatan todos los que siguen", () -> {
            var ganadores = Puntuacion.ganadores(Map.of(), List.of("a", "b"));
            iguales(List.of("a", "b"), ganadores);
            cierto(!ganadores.isEmpty(), "no hubo ganadores");
        });
    }
}
