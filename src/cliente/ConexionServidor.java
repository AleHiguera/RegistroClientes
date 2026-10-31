package cliente;

import comun.Protocolo;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.security.cert.CertificateException;

/** Conexión cifrada (TLS) con el servidor. */
public class ConexionServidor implements Closeable {
    private static final int TIEMPO_CONEXION_MS = 5000;
    private static final int TIEMPO_RESPUESTA_MS = 20000;

    private final SSLSocket socket;
    private final DataInputStream entrada;
    private final DataOutputStream salida;

    public ConexionServidor(String host, int puerto, ConfianzaTofu.Confirmador confirmador)
            throws IOException, GeneralSecurityException {
        SSLContext contexto = SSLContext.getInstance("TLS");
        contexto.init(null, new TrustManager[]{new ConfianzaTofu(host + ":" + puerto, confirmador)}, null);
        socket = (SSLSocket) contexto.getSocketFactory().createSocket();

        try {
            socket.setEnabledProtocols(Protocolo.VERSIONES_TLS);
            socket.connect(new InetSocketAddress(host, puerto), TIEMPO_CONEXION_MS);
            socket.setSoTimeout(TIEMPO_RESPUESTA_MS);
            socket.startHandshake(); // aquí se verifica el certificado
            entrada = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            salida = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        } catch (SSLHandshakeException e) {
            socket.close();
            throw new IOException(motivoDelFallo(e), e);
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }

    /** Envía un comando y espera la respuesta del servidor. */
    public synchronized String[] solicitar(String... campos) throws IOException {
        Protocolo.enviar(salida, campos);
        return Protocolo.recibir(entrada);
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }

    private static String motivoDelFallo(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof CertificateException) {
                return t.getMessage();
            }
        }
        return "Fallo en la conexión segura: " + e.getMessage();
    }
}
