package servidor;

import static servidor.Pruebas.caso;
import static servidor.Pruebas.cierto;
import static servidor.Pruebas.iguales;

import java.util.List;
import java.util.Map;

final class PruebasPuntuacion {
    private PruebasPuntuacion() {}

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

        caso("Si nadie tiene puntos, empatan todos los que siguen", () -> {
            var ganadores = Puntuacion.ganadores(Map.of(), List.of("a", "b"));
            iguales(List.of("a", "b"), ganadores);
            cierto(!ganadores.isEmpty(), "no hubo ganadores");
        });
    }
}
