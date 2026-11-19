package servidor;

import static servidor.Pruebas.caso;
import static servidor.Pruebas.cierto;
import static servidor.Pruebas.iguales;

final class PruebasMensajesAcceso {
    private PruebasMensajesAcceso() {}

    static void ejecutar() {
        caso("Si la cuenta no existe se dice cuál usuario no existe", () ->
                iguales("El usuario \"lucia\" no existe. Revisa cómo lo escribiste o crea una cuenta.",
                        MensajesAcceso.fallo(false, "lucia")));

        caso("Si la cuenta existe, el problema es el PIN", () ->
                iguales("El PIN es incorrecto. Revisa los 4 números e inténtalo de nuevo.",
                        MensajesAcceso.fallo(true, "lucia")));

        caso("El PIN incorrecto no repite el usuario", () ->
                cierto(!MensajesAcceso.fallo(true, "lucia").contains("lucia"), MensajesAcceso.fallo(true, "lucia")));

        caso("Ya conectado explica que hay otra sesión abierta", () -> {
            String mensaje = SesionesActivas.mensajeOcupado("lucia");
            iguales(MensajesAcceso.yaConectado("lucia"), mensaje);
            cierto(mensaje.contains("\"lucia\" ya está conectado en otro dispositivo"), mensaje);
        });
    }
}
