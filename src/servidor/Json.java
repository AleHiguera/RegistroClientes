package servidor;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/** Escribe los JSON que responde la API web. */
final class Json {
    private Json() {}

    static String texto(String texto) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : String.valueOf(texto).toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    /** Respuesta {"ok": ..., "mensaje": ..., clave: valor...} con los pares extra que se indiquen. */
    static String respuesta(boolean ok, String mensaje, String... clavesYValores) {
        StringBuilder json = new StringBuilder("{\"ok\":").append(ok)
                .append(",\"mensaje\":").append(texto(mensaje));
        for (int i = 0; i + 1 < clavesYValores.length; i += 2) {
            json.append(',').append(texto(clavesYValores[i]))
                    .append(':').append(texto(clavesYValores[i + 1]));
        }
        return json.append('}').toString();
    }

    /** Respuesta correcta con una sala: la lista de jugadores y los números no son textos sueltos. */
    static String sala(String mensaje, SalasMultijugador.Vista sala) {
        return "{\"ok\":true"
                + ",\"mensaje\":" + texto(mensaje)
                + ",\"codigo\":" + texto(sala.codigo())
                + ",\"mapa\":" + sala.mapa()
                + ",\"capacidad\":" + sala.capacidad()
                + ",\"anfitrion\":" + texto(sala.anfitrion())
                + ",\"jugadores\":" + lista(sala.jugadores())
                + ",\"listos\":" + lista(sala.listos())
                + ",\"comenzada\":" + sala.comenzada()
                + ",\"finalizados\":" + lista(sala.finalizados())
                + ",\"configurada\":" + sala.configurada()
                + ",\"miTablero\":" + numeros(sala.miTablero())
                + ",\"ronda\":" + sala.ronda()
                + ",\"dados\":" + numeros(sala.dados())
                + ",\"jugaron\":" + lista(sala.jugaron())
                + ",\"zonas\":" + zonas(sala.zonas())
                + ",\"meta\":" + sala.meta()
                + ",\"terminada\":" + sala.terminada()
                + ",\"ganadores\":" + lista(sala.ganadores())
                + ",\"porAbandono\":" + sala.porAbandono()
                + "}";
    }

    private static String zonas(Map<Integer, Map<String, Integer>> zonas) {
        StringJoiner json = new StringJoiner(",", "{", "}");
        zonas.forEach((zona, rondas) -> {
            StringJoiner porJugador = new StringJoiner(",", "{", "}");
            rondas.forEach((jugador, ronda) -> porJugador.add(texto(jugador) + ":" + ronda));
            json.add("\"" + zona + "\":" + porJugador);
        });
        return json.toString();
    }

    private static String numeros(List<Integer> numeros) {
        return numeros.stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]"));
    }

    private static String lista(List<String> textos) {
        StringJoiner lista = new StringJoiner(",", "[", "]");
        textos.forEach(elemento -> lista.add(texto(elemento)));
        return lista.toString();
    }
}
