package servidor;

import java.util.StringJoiner;

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
        StringJoiner jugadores = new StringJoiner(",", "[", "]");
        sala.jugadores().forEach(jugador -> jugadores.add(texto(jugador)));
        return "{\"ok\":true"
                + ",\"mensaje\":" + texto(mensaje)
                + ",\"codigo\":" + texto(sala.codigo())
                + ",\"mapa\":" + sala.mapa()
                + ",\"capacidad\":" + sala.capacidad()
                + ",\"anfitrion\":" + texto(sala.anfitrion())
                + ",\"jugadores\":" + jugadores
                + "}";
    }
}
