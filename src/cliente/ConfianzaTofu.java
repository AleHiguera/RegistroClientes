package cliente;

import comun.Huella;

import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Properties;

/**
 * Decide si se confía en el certificado del servidor ("confiar en el primer uso", como SSH).
 * La primera vez se le pregunta al usuario y se guarda la huella; las siguientes veces se exige
 * que coincida, así que si alguien se hace pasar por el servidor la conexión se rechaza.
 */
public class ConfianzaTofu implements X509TrustManager {
    private static final Path ARCHIVO = Path.of("servidores_conocidos.properties");

    public interface Confirmador {
        boolean confiarEnServidorNuevo(String servidor, String huella);
    }

    private final String servidor;
    private final Confirmador confirmador;

    public ConfianzaTofu(String servidor, Confirmador confirmador) {
        this.servidor = servidor;
        this.confirmador = confirmador;
    }

    @Override
    public void checkServerTrusted(X509Certificate[] cadena, String tipoAutenticacion) throws CertificateException {
        if (cadena == null || cadena.length == 0) {
            throw new CertificateException("El servidor no envió certificado.");
        }
        cadena[0].checkValidity();
        String huella = Huella.sha256(cadena[0]);

        synchronized (ConfianzaTofu.class) {
            Properties conocidos = cargar();
            String guardada = conocidos.getProperty(servidor);
            if (guardada != null) {
                if (guardada.equals(huella)) {
                    return;
                }
                throw new CertificateException("¡El certificado de " + servidor + " cambió!\n"
                        + "Podría ser alguien haciéndose pasar por el servidor.\n"
                        + "Si regeneraste el certificado a propósito, borra su línea en " + ARCHIVO + ".");
            }

            if (!confirmador.confiarEnServidorNuevo(servidor, huella)) {
                throw new CertificateException("Conexión cancelada: no se confió en el servidor.");
            }
            conocidos.setProperty(servidor, huella);
            guardar(conocidos);
        }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] cadena, String tipoAutenticacion) throws CertificateException {
        throw new CertificateException("No se usa en el cliente.");
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }

    private static Properties cargar() throws CertificateException {
        Properties p = new Properties();
        if (Files.exists(ARCHIVO)) {
            try (InputStream in = Files.newInputStream(ARCHIVO)) {
                p.load(in);
            } catch (IOException e) {
                throw new CertificateException("No se pudo leer " + ARCHIVO, e);
            }
        }
        return p;
    }

    private static void guardar(Properties p) throws CertificateException {
        try (OutputStream out = Files.newOutputStream(ARCHIVO)) {
            p.store(out, "Huellas SHA-256 de los servidores en los que confias");
        } catch (IOException e) {
            throw new CertificateException("No se pudo guardar " + ARCHIVO, e);
        }
    }
}
