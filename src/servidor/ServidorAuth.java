package servidor;

import comun.Huella;
import comun.Protocolo;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Collections;

public class ServidorAuth {
    private static final Path KEYSTORE = Path.of("servidor.p12");
    private static final String ALIAS = "servidor";
    // Solo protege el archivo del certificado en la máquina del servidor
    private static final char[] KEYSTORE_PASS = "registro-clientes".toCharArray();

    public static void main(String[] args) {
        int puerto = args.length > 0 ? Integer.parseInt(args[0]) : Protocolo.PUERTO_POR_DEFECTO;

        try {
            BaseDatos.inicializar();
            KeyStore keystore = cargarOCrearKeystore();
            SSLContext tls = crearContextoTls(keystore);

            try (SSLServerSocket servidorSocket =
                         (SSLServerSocket) tls.getServerSocketFactory().createServerSocket(puerto)) {
                servidorSocket.setEnabledProtocols(Protocolo.VERSIONES_TLS);

                System.out.println("Servidor TLS iniciado en el puerto " + puerto);
                System.out.println("Huella SHA-256 del certificado (compárala en el cliente):");
                System.out.println("   " + Huella.sha256(keystore.getCertificate(ALIAS)));
                mostrarDirecciones(puerto);

                while (true) {
                    Socket socket = servidorSocket.accept();
                    new Thread(new AtencionCliente(socket)).start();
                }
            }
        } catch (Exception e) {
            System.err.println("Error al iniciar o ejecutar el servidor: " + e.getMessage());
        }
    }

    /** Igual que la base de datos: si no hay certificado (repositorio recién clonado), se genera uno. */
    private static KeyStore cargarOCrearKeystore() throws Exception {
        if (!Files.exists(KEYSTORE)) {
            System.out.println("Certificado no encontrado, generando " + KEYSTORE + "...");
            generarCertificado();
        }
        KeyStore keystore = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(KEYSTORE)) {
            keystore.load(in, KEYSTORE_PASS);
        }
        return keystore;
    }

    private static void generarCertificado() throws IOException, InterruptedException {
        // keytool viene incluido con Java
        String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        Process proceso = new ProcessBuilder(keytool,
                "-genkeypair", "-alias", ALIAS,
                "-keyalg", "RSA", "-keysize", "2048", "-validity", "3650",
                "-dname", "CN=RegistroClientes",
                "-storetype", "PKCS12", "-keystore", KEYSTORE.toString(),
                "-storepass", new String(KEYSTORE_PASS))
                .inheritIO()
                .start();
        if (proceso.waitFor() != 0) {
            throw new IOException("keytool no pudo generar el certificado");
        }
    }

    private static SSLContext crearContextoTls(KeyStore keystore) throws Exception {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keystore, KEYSTORE_PASS);
        SSLContext contexto = SSLContext.getInstance("TLS");
        contexto.init(kmf.getKeyManagers(), null, null);
        return contexto;
    }

    private static void mostrarDirecciones(int puerto) throws SocketException {
        System.out.println("Los clientes pueden conectarse a:");
        System.out.println("   localhost:" + puerto + " (esta misma computadora)");
        for (NetworkInterface red : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!red.isUp() || red.isLoopback()) {
                continue;
            }
            for (InetAddress direccion : Collections.list(red.getInetAddresses())) {
                if (direccion instanceof Inet4Address) {
                    System.out.println("   " + direccion.getHostAddress() + ":" + puerto + " (" + red.getDisplayName() + ")");
                }
            }
        }
    }
}
