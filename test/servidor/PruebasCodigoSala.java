package servidor;

import static servidor.Pruebas.caso;
import static servidor.Pruebas.cierto;
import static servidor.Pruebas.iguales;

import java.util.concurrent.atomic.AtomicInteger;

final class PruebasCodigoSala {
    private PruebasCodigoSala() {}

    static void ejecutar() {
        caso("El código tiene 6 caracteres sin letras que se confundan", () -> {
            for (int i = 0; i < 1000; i++) {
                String codigo = CodigoSala.nuevo(c -> false);
                iguales(CodigoSala.LARGO, codigo.length());
                cierto(codigo.chars().allMatch(c -> CodigoSala.LETRAS.indexOf(c) >= 0), "caracter inválido en " + codigo);
                cierto(codigo.chars().noneMatch(c -> "01OI".indexOf(c) >= 0), "caracter confuso en " + codigo);
            }
        });

        caso("Si el código ya está en uso se genera otro", () -> {
            AtomicInteger intentos = new AtomicInteger();
            CodigoSala.nuevo(c -> intentos.incrementAndGet() < 3);
            iguales(3, intentos.get());
        });

        caso("Normalizar quita espacios y pasa a mayúsculas", () ->
                iguales("ABC234", CodigoSala.normalizar("  abc234 ")));
    }
}
