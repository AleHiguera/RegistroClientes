package servidor;

import comun.Protocolo;

import javax.net.ssl.SSLException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;

/** Atiende a un cliente conectado. La sesión vive mientras dure la conexión. */
public class AtencionCliente implements Runnable {
    private static final int INACTIVIDAD_MAX_MS = 15 * 60 * 1000;

    private final Socket socket;
    private final String origen;
    private String usuarioAutenticado;

    public AtencionCliente(Socket socket) {
        this.socket = socket;
        this.origen = socket.getRemoteSocketAddress().toString();
    }

    @Override
    public void run() {
        System.out.println("[+] Conexión desde " + origen);
        try (socket;
             DataInputStream entrada = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
             DataOutputStream salida = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {

            socket.setSoTimeout(INACTIVIDAD_MAX_MS);
            while (true) {
                Protocolo.enviar(salida, procesar(Protocolo.recibir(entrada)));
            }
        } catch (EOFException e) {
            // El cliente cerró la conexión
        } catch (SocketTimeoutException e) {
            System.out.println("[-] " + origen + " expulsado por inactividad");
        } catch (SSLException e) {
            System.out.println("[!] Fallo TLS con " + origen + ": " + e.getMessage());
        } catch (IOException e) {
            System.out.println("[!] Error con " + origen + ": " + e.getMessage());
        }
        System.out.println("[-] " + origen + " desconectado");
    }

    private String[] procesar(String[] mensaje) {
        return switch (mensaje[0]) {
            case Protocolo.REGISTRAR -> registrar(mensaje);
            case Protocolo.LOGIN -> iniciarSesion(mensaje);
            case Protocolo.PERFIL -> perfil();
            case Protocolo.LOGOUT -> cerrarSesion();
            case Protocolo.BORRAR_CUENTA -> borrarCuenta(mensaje);
            default -> error("Comando desconocido.");
        };
    }

    private String[] registrar(String[] m) {
        if (m.length != 5) {
            return error("Datos incompletos.");
        }
        GestorCuentas.Resultado r = GestorCuentas.registrar(m[1], m[2], m[3], m[4]);
        if (r.exito()) {
            System.out.println("[*] Nueva cuenta: " + r.usuario());
        }
        return responder(r);
    }

    private String[] iniciarSesion(String[] m) {
        if (m.length != 3) {
            return error("Datos incompletos.");
        }
        GestorCuentas.Resultado r = GestorCuentas.iniciarSesion(m[1], m[2]);
        if (r.exito()) {
            usuarioAutenticado = r.usuario();
            System.out.println("[*] " + usuarioAutenticado + " inició sesión desde " + origen);
        } else {
            System.out.println("[!] Inicio de sesión fallido para '" + m[1] + "' desde " + origen);
        }
        return responder(r);
    }

    private String[] perfil() {
        if (usuarioAutenticado == null) {
            return error("Debes iniciar sesión.");
        }
        GestorCuentas.Perfil p = GestorCuentas.obtenerPerfil(usuarioAutenticado);
        if (p == null) {
            return error("No se encontró la cuenta.");
        }
        return new String[]{Protocolo.OK, p.usuario(), p.nombre(), p.correo(), p.fechaRegistro()};
    }

    private String[] cerrarSesion() {
        if (usuarioAutenticado != null) {
            System.out.println("[*] " + usuarioAutenticado + " cerró sesión");
            usuarioAutenticado = null;
        }
        return new String[]{Protocolo.OK, "Sesión cerrada."};
    }

    private String[] borrarCuenta(String[] m) {
        if (usuarioAutenticado == null) {
            return error("Debes iniciar sesión.");
        }
        if (m.length != 2) {
            return error("Datos incompletos.");
        }
        GestorCuentas.Resultado r = GestorCuentas.borrarCuenta(usuarioAutenticado, m[1]);
        if (r.exito()) {
            System.out.println("[*] Cuenta borrada: " + usuarioAutenticado);
            usuarioAutenticado = null;
        }
        return responder(r);
    }

    private static String[] responder(GestorCuentas.Resultado r) {
        return new String[]{r.exito() ? Protocolo.OK : Protocolo.ERROR, r.mensaje()};
    }

    private static String[] error(String mensaje) {
        return new String[]{Protocolo.ERROR, mensaje};
    }
}
