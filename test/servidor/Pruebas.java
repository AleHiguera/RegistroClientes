package servidor;

import java.util.Objects;

/** Ejecuta todas las pruebas del servidor sin librerías externas. Termina con error si alguna falla. */
public final class Pruebas {
    private static int correctas;
    private static int fallidas;

    @FunctionalInterface
    interface Caso {
        void ejecutar() throws Exception;
    }

    private Pruebas() {}

    public static void main(String[] args) {
        PruebasCodigoSala.ejecutar();
        PruebasSalasMultijugador.ejecutar();
        PruebasJson.ejecutar();
        PruebasPuntuacion.ejecutar();

        System.out.println("\n" + correctas + " correctas, " + fallidas + " fallidas");
        if (fallidas > 0) {
            System.exit(1);
        }
    }

    static void caso(String nombre, Caso caso) {
        try {
            caso.ejecutar();
            correctas++;
            System.out.println("[OK] " + nombre);
        } catch (Exception | AssertionError e) {
            fallidas++;
            System.out.println("[FALLA] " + nombre + ": " + e);
        }
    }

    static void iguales(Object esperado, Object obtenido) {
        if (!Objects.equals(esperado, obtenido)) {
            throw new AssertionError("se esperaba <" + esperado + "> pero llegó <" + obtenido + ">");
        }
    }

    static void cierto(boolean condicion, String explicacion) {
        if (!condicion) {
            throw new AssertionError(explicacion);
        }
    }
}
