package servidor;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import comun.Protocolo;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * API HTTPS para clientes que no pueden abrir un socket TLS directo, como la app Brilliant en el navegador.
 * Usa el mismo certificado y las mismas cuentas que el servidor de consola.
 *
 *   POST /api/login   (usuario, password como formulario)  → token de sesión
 *   POST /api/logout  (cabecera Authorization: Bearer token)
 *   GET  /            página para aceptar el certificado en el navegador
 */
public final class ServidorWeb {
    public static final int PUERTO_POR_DEFECTO = 8443;

    private static final int MAX_CUERPO = 4096;
    private static final long DURACION_MAX_MS = 12 * 60 * 60 * 1000L; // una partida puede durar bastante
    private static final SecureRandom ALEATORIO = new SecureRandom();

    // token → sesión. La sesión ya no está ligada a una conexión, así que se identifica con un token aleatorio.
    private static final Map<String, SesionWeb> SESIONES = new ConcurrentHashMap<>();

    private record SesionWeb(String usuario, long inicio) {
        boolean caducada() {
            return System.currentTimeMillis() - inicio > DURACION_MAX_MS;
        }
    }

    private interface Accion {
        void atender(HttpExchange intercambio) throws IOException;
    }

    private ServidorWeb() {}

    public static void iniciar(int puerto, SSLContext tls) throws IOException {
        HttpsServer servidor = HttpsServer.create(new InetSocketAddress(puerto), 0);
        servidor.setHttpsConfigurator(new HttpsConfigurator(tls) {
            @Override
            public void configure(HttpsParameters parametros) {
                SSLParameters ssl = tls.getDefaultSSLParameters();
                ssl.setProtocols(Protocolo.VERSIONES_TLS);
                parametros.setSSLParameters(ssl);
            }
        });
        servidor.createContext("/", ServidorWeb::bienvenida);
        servidor.createContext("/api/login", api(ServidorWeb::iniciarSesion));
        servidor.createContext("/api/logout", api(ServidorWeb::cerrarSesion));
        servidor.setExecutor(Executors.newFixedThreadPool(16));
        servidor.start();
    }

    /**
     * El certificado es autofirmado: el navegador lo rechaza hasta que el usuario abre esta página
     * y lo acepta (comparando la huella que imprime el servidor).
     */
    private static void bienvenida(HttpExchange intercambio) throws IOException {
        try (intercambio) {
            if (!intercambio.getRequestURI().getPath().equals("/")) {
                enviar(intercambio, 404, "text/plain", "No encontrado.");
                return;
            }
            enviar(intercambio, 200, "text/html", "<!doctype html><meta charset=\"utf-8\">"
                    + "<title>Registro de clientes</title>"
                    + "<h1>Servidor funcionando</h1>"
                    + "<p>El navegador ya confía en este servidor. Vuelve a la app e inicia sesión.</p>");
        }
    }

    /** Envuelve una acción de la API: CORS (la app se sirve desde otro origen), solo POST y errores. */
    private static HttpHandler api(Accion accion) {
        return intercambio -> {
            try (intercambio) {
                Headers cabeceras = intercambio.getResponseHeaders();
                cabeceras.set("Access-Control-Allow-Origin", "*");
                cabeceras.set("Access-Control-Allow-Methods", "POST, OPTIONS");
                cabeceras.set("Access-Control-Allow-Headers", "Content-Type, Authorization");

                switch (intercambio.getRequestMethod()) {
                    case "OPTIONS" -> intercambio.sendResponseHeaders(204, -1);
                    case "POST" -> accion.atender(intercambio);
                    default -> responder(intercambio, 405, false, "Método no permitido.");
                }
            } catch (IOException e) {
                System.out.println("[!] Error web con " + origen(intercambio) + ": " + e.getMessage());
            }
        };
    }

    private static void iniciarSesion(HttpExchange intercambio) throws IOException {
        Map<String, String> datos = leerFormulario(intercambio);
        if (datos == null) {
            return;
        }
        String usuario = datos.get("usuario");
        String password = datos.get("password");
        if (usuario == null || password == null) {
            responder(intercambio, 400, false, "Datos incompletos.");
            return;
        }

        GestorCuentas.Resultado r = GestorCuentas.iniciarSesion(usuario, password);
        if (!r.exito()) {
            System.out.println("[!] Inicio de sesión web fallido para '" + usuario + "' desde " + origen(intercambio));
            responder(intercambio, 401, false, r.mensaje());
            return;
        }

        SESIONES.values().removeIf(SesionWeb::caducada);
        String token = nuevoToken();
        SESIONES.put(token, new SesionWeb(r.usuario(), System.currentTimeMillis()));
        System.out.println("[*] " + r.usuario() + " inició sesión (web) desde " + origen(intercambio));

        GestorCuentas.Perfil perfil = GestorCuentas.obtenerPerfil(r.usuario());
        responder(intercambio, 200, true, r.mensaje(),
                "usuario", r.usuario(),
                "nombre", perfil != null ? perfil.nombre() : r.usuario(),
                "token", token);
    }

    private static void cerrarSesion(HttpExchange intercambio) throws IOException {
        String autorizacion = intercambio.getRequestHeaders().getFirst("Authorization");
        if (autorizacion != null && autorizacion.startsWith("Bearer ")) {
            SesionWeb sesion = SESIONES.remove(autorizacion.substring("Bearer ".length()));
            if (sesion != null) {
                System.out.println("[*] " + sesion.usuario() + " cerró sesión (web)");
            }
        }
        responder(intercambio, 200, true, "Sesión cerrada.");
    }

    /** Lee un cuerpo application/x-www-form-urlencoded. Si no es válido responde el error y devuelve null. */
    private static Map<String, String> leerFormulario(HttpExchange intercambio) throws IOException {
        byte[] cuerpo = intercambio.getRequestBody().readNBytes(MAX_CUERPO + 1);
        if (cuerpo.length > MAX_CUERPO) {
            responder(intercambio, 413, false, "Petición demasiado grande.");
            return null;
        }
        Map<String, String> datos = new HashMap<>();
        try {
            for (String par : new String(cuerpo, StandardCharsets.UTF_8).split("&")) {
                String[] partes = par.split("=", 2);
                if (partes.length == 2) {
                    datos.put(URLDecoder.decode(partes[0], StandardCharsets.UTF_8),
                            URLDecoder.decode(partes[1], StandardCharsets.UTF_8));
                }
            }
        } catch (IllegalArgumentException e) {
            responder(intercambio, 400, false, "Datos con formato inválido.");
            return null;
        }
        return datos;
    }

    /** Responde un JSON {"ok": ..., "mensaje": ..., clave: valor...} con los pares extra que se indiquen. */
    private static void responder(HttpExchange intercambio, int codigo, boolean ok, String mensaje,
                                  String... clavesYValores) throws IOException {
        StringBuilder json = new StringBuilder("{\"ok\":").append(ok)
                .append(",\"mensaje\":").append(textoJson(mensaje));
        for (int i = 0; i + 1 < clavesYValores.length; i += 2) {
            json.append(',').append(textoJson(clavesYValores[i]))
                    .append(':').append(textoJson(clavesYValores[i + 1]));
        }
        enviar(intercambio, codigo, "application/json", json.append('}').toString());
    }

    private static void enviar(HttpExchange intercambio, int codigo, String tipo, String contenido) throws IOException {
        byte[] bytes = contenido.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().set("Content-Type", tipo + "; charset=utf-8");
        intercambio.getResponseHeaders().set("Cache-Control", "no-store");
        intercambio.sendResponseHeaders(codigo, bytes.length);
        try (OutputStream salida = intercambio.getResponseBody()) {
            salida.write(bytes);
        }
    }

    private static String textoJson(String texto) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : String.valueOf(texto).toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    private static String nuevoToken() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String origen(HttpExchange intercambio) {
        return String.valueOf(intercambio.getRemoteAddress());
    }
}
