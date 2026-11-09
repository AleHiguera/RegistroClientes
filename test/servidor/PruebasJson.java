package servidor;

import static servidor.Pruebas.caso;
import static servidor.Pruebas.iguales;

import java.util.List;

final class PruebasJson {
    private PruebasJson() {}

    static void ejecutar() {
        caso("Los textos escapan comillas, barras y controles", () ->
                iguales("\"a\\\"b\\\\c\\n\\u0001\"", Json.texto("a\"b\\c\n\u0001")));

        caso("La respuesta incluye los pares extra", () ->
                iguales("{\"ok\":true,\"mensaje\":\"Hola\",\"token\":\"abc\"}", Json.respuesta(true, "Hola", "token", "abc")));

        caso("La sala lleva números y una lista de jugadores", () -> {
            var sala = new SalasMultijugador.Vista("ABC234", 2, 3, "ana", List.of("ana", "lu\"is"),
                    List.of("lu\"is"), true, List.of("ana"), true, List.of(3, 1, 2, 6, 5, 4),
                    2, List.of(5, 2), List.of("ana"));
            iguales("{\"ok\":true,\"mensaje\":\"Sala.\",\"codigo\":\"ABC234\",\"mapa\":2,\"capacidad\":3,"
                            + "\"anfitrion\":\"ana\",\"jugadores\":[\"ana\",\"lu\\\"is\"],"
                            + "\"listos\":[\"lu\\\"is\"],\"comenzada\":true,"
                            + "\"finalizados\":[\"ana\"],\"configurada\":true,\"miTablero\":[3,1,2,6,5,4],"
                            + "\"ronda\":2,\"dados\":[5,2],\"jugaron\":[\"ana\"]}",
                    Json.sala("Sala.", sala));
        });
    }
}
