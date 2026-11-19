package servidor;

import static servidor.Pruebas.caso;
import static servidor.Pruebas.cierto;

final class PruebasTableroEnviado {
    private PruebasTableroEnviado() {}

    static void ejecutar() {
        caso("Un tablero de 49 cifras del 0 al 6 es válido", () ->
                cierto(TableroEnviado.esValido("0123456".repeat(7)), "lo rechazó"));

        caso("Sin tablero no hay nada que guardar", () ->
                cierto(!TableroEnviado.esValido(null), "aceptó null"));

        caso("Con otro largo no es válido", () -> {
            cierto(!TableroEnviado.esValido("0".repeat(48)), "aceptó 48");
            cierto(!TableroEnviado.esValido("0".repeat(50)), "aceptó 50");
            cierto(!TableroEnviado.esValido(""), "aceptó vacío");
        });

        caso("Con números fuera del 0 al 6 o con letras no es válido", () -> {
            cierto(!TableroEnviado.esValido("7" + "0".repeat(48)), "aceptó un 7");
            cierto(!TableroEnviado.esValido("a" + "0".repeat(48)), "aceptó una letra");
            cierto(!TableroEnviado.esValido("-" + "0".repeat(48)), "aceptó un signo");
        });
    }
}
