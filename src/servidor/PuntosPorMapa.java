package servidor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Puntos de 1.º, 2.º y 3.º lugar de cada zona de cada mapa, numeradas como las numera la app (desde 1).
 *
 * Es una copia de los mapas de la app, guardada con el mismo texto que genera la prueba
 * test/puntos_servidor_test.dart de brilliantdart. Si cambian los puntos de un mapa, esa prueba falla y muestra el
 * texto nuevo, que se copia aquí tal cual.
 */
final class PuntosPorMapa {
    static final String TABLA =
            "1:8,6,4|4,3,2|7,5,3|6,4,2|4,3,2|6,4,2|6,4,2|6,4,2|7,5,3;"
            + "2:6,4,2|3,2,1|4,3,2|6,4,2|7,5,3|6,4,2|6,4,2|4,3,2|7,5,3|6,4,2;"
            + "3:7,5,3|7,5,3|8,6,4|6,4,2|6,4,2|6,5,4|7,5,3|7,5,3;"
            + "4:6,4,3|8,6,4|6,4,2|8,6,4|3,2,1|6,4,2|7,5,3|6,4,3";

    // mapa → zona (desde 0) → puntos por lugar
    private static final Map<Integer, List<List<Integer>>> ZONAS = leer(TABLA);

    private PuntosPorMapa() {}

    private static Map<Integer, List<List<Integer>>> leer(String tabla) {
        Map<Integer, List<List<Integer>>> mapas = new HashMap<>();
        for (String mapa : tabla.split(";")) {
            String[] partes = mapa.split(":");
            List<List<Integer>> zonas = new ArrayList<>();
            for (String zona : partes[1].split("\\|")) {
                zonas.add(Arrays.stream(zona.split(",")).map(Integer::valueOf).toList());
            }
            mapas.put(Integer.valueOf(partes[0]), List.copyOf(zonas));
        }
        return Map.copyOf(mapas);
    }

    static int cantidadDeZonas(int mapa) {
        return ZONAS.getOrDefault(mapa, List.of()).size();
    }

    /** Puntos de un lugar ya ganado en esa zona. Del cuarto lugar en adelante, 0. */
    static int puntos(int mapa, int zona, int lugar) {
        List<Integer> lugares = ZONAS.get(mapa).get(zona - 1);
        return lugar >= 1 && lugar <= lugares.size() ? lugares.get(lugar - 1) : 0;
    }
}
