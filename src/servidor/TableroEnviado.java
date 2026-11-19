package servidor;

/**
 * El tablero de un jugador tal como lo manda la app: 49 cifras, fila por fila (7 filas de 7 columnas), con el
 * número de cada celda del 1 al 6 o 0 si está vacía. El servidor solo lo guarda para mostrarlo al final de la
 * partida; las reglas de cada zona las aplica la app.
 */
final class TableroEnviado {
    static final int CELDAS = 7 * 7;

    private TableroEnviado() {}

    static boolean esValido(String tablero) {
        return tablero != null && tablero.length() == CELDAS && tablero.chars().allMatch(c -> c >= '0' && c <= '6');
    }
}
