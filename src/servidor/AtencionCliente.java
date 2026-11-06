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
import java.util.function.Function;

/** Atiende a un cliente conectado. La sesión vive mientras dure la conexión. */
public class AtencionCliente implements Runnable, SesionesActivas.Sesion {
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
        } finally {
            liberarSesion();
        }
        System.out.println("[-] " + origen + " desconectado");
    }

    @Override
    public boolean caducada() {
        return socket.isClosed();
    }

    private void liberarSesion() {
        if (usuarioAutenticado != null) {
            SesionesActivas.liberar(usuarioAutenticado, this);
            usuarioAutenticado = null;
        }
    }

    private String[] procesar(String[] mensaje) {
        return switch (mensaje[0]) {
            case Protocolo.COMPROBAR_USUARIO -> comprobar(mensaje, GestorCuentas::comprobarUsuario);
            case Protocolo.COMPROBAR_CORREO -> comprobar(mensaje, GestorCuentas::comprobarCorreo);
            case Protocolo.REGISTRAR -> registrar(mensaje);
            case Protocolo.LOGIN -> iniciarSesion(mensaje);
            case Protocolo.PERFIL -> perfil();
            case Protocolo.LOGOUT -> cerrarSesion();
            case Protocolo.BORRAR_CUENTA -> borrarCuenta(mensaje);
            default -> error("Comando desconocido.");
        };
    }

    /** Durante el registro el cliente pregunta si el usuario o el correo siguen libres. */
    private String[] comprobar(String[] m, Function<String, GestorCuentas.Resultado> comprobacion) {
        if (m.length != 2) {
            return error("Datos incompletos.");
        }
        return responder(comprobacion.apply(m[1]));
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
        if (usuarioAutenticado != null) {
            return error("Ya tienes una sesión iniciada.");
        }
        GestorCuentas.Resultado r = GestorCuentas.iniciarSesion(m[1], m[2]);
        if (!r.exito()) {
            System.out.println("[!] Inicio de sesión fallido para '" + m[1] + "' desde " + origen);
            return responder(r);
        }
        // Se comprueba después de la contraseña, para no revelar quién está conectado a cualquiera
        if (!SesionesActivas.ocupar(r.usuario(), this)) {
            System.out.println("[!] " + r.usuario() + " ya está conectado, se rechazó el acceso desde " + origen);
            return error(SesionesActivas.mensajeOcupado(r.usuario()));
        }
        usuarioAutenticado = r.usuario();
        System.out.println("[*] " + usuarioAutenticado + " inició sesión desde " + origen);
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
            liberarSesion();
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
            liberarSesion();
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
