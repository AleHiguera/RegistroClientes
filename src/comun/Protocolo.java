package comun;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Formato de los mensajes entre cliente y servidor.
 * Cada mensaje es: cantidad de campos (int) + cada campo con writeUTF.
 * El primer campo es el comando (cliente) o el estado OK/ERROR (servidor).
 */
public final class Protocolo {
    public static final int PUERTO_POR_DEFECTO = 8080;
    public static final String[] VERSIONES_TLS = {"TLSv1.3", "TLSv1.2"};

    // Comandos del cliente
    public static final String REGISTRAR = "REGISTRAR";         // usuario, nombre, correo, password
    public static final String LOGIN = "LOGIN";                 // usuario, password
    public static final String PERFIL = "PERFIL";               // (requiere sesión)
    public static final String LOGOUT = "LOGOUT";
    public static final String BORRAR_CUENTA = "BORRAR_CUENTA"; // password (requiere sesión)

    // Respuestas del servidor
    public static final String OK = "OK";
    public static final String ERROR = "ERROR";

    private static final int MAX_CAMPOS = 10;

    private Protocolo() {}

    public static void enviar(DataOutputStream salida, String... campos) throws IOException {
        salida.writeInt(campos.length);
        for (String campo : campos) {
            salida.writeUTF(campo == null ? "" : campo);
        }
        salida.flush();
    }

    public static String[] recibir(DataInputStream entrada) throws IOException {
        int cantidad = entrada.readInt();
        if (cantidad < 1 || cantidad > MAX_CAMPOS) {
            throw new IOException("Mensaje con formato inválido");
        }
        String[] campos = new String[cantidad];
        for (int i = 0; i < cantidad; i++) {
            campos[i] = entrada.readUTF();
        }
        return campos;
    }
}
