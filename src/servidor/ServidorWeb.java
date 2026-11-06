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
 *   POST /api/login     (usuario, password como formulario)                 → token de sesión
 *   POST /api/registro  (usuario, nombre, correo, password como formulario) → cuenta creada y token de sesión
 *   POST /api/latido    (cabecera Authorization: Bearer token)                → mantiene viva la sesión
 *   POST /api/logout    (cabecera Authorization: Bearer token)
 *   GET  /            página para aceptar el certificado en el navegador
 */
public final class ServidorWeb {
    public static final int PUERTO_POR_DEFECTO = 8443;

    private static final int MAX_CUERPO = 4096;
    private static final long SIN_LATIDO_MAX_MS = 30 * 1000L; // la app envía un latido cada 10 s
    private static final SecureRandom ALEATORIO = new SecureRandom();

    // token → sesión. La sesión ya no está ligada a una conexión, así que se identifica con un token aleatorio.
    private static final Map<String, SesionWeb> SESIONES = new ConcurrentHashMap<>();

    /** Cerrar el navegador no avisa al servidor: se sabe que la app sigue abierta porque llegan sus latidos. */
    private static final class SesionWeb implements SesionesActivas.Sesion {
        private final String usuario;
        private volatile long ultimoLatido = System.currentTimeMillis();

        SesionWeb(String usuario) {
            this.usuario = usuario;
        }

        void renovar() {
            ultimoLatido = System.currentTimeMillis();
        }

        @Override
        public boolean caducada() {
            return System.currentTimeMillis() - ultimoLatido > SIN_LATIDO_MAX_MS;
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
        servidor.createContext("/api/registro", api(ServidorWeb::registrar));
        servidor.createContext("/api/latido", api(ServidorWeb::latido));
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
        abrirSesion(intercambio, r);
    }

    /** Crea la cuenta con las mismas validaciones que la consola y deja la sesión ya iniciada. */
    private static void registrar(HttpExchange intercambio) throws IOException {
        Map<String, String> datos = leerFormulario(intercambio);
        if (datos == null) {
            return;
        }
        String usuario = datos.get("usuario");
        String nombre = datos.get("nombre");
        String correo = datos.get("correo");
        String password = datos.get("password");
        if (usuario == null || nombre == null || correo == null || password == null) {
            responder(intercambio, 400, false, "Datos incompletos.");
            return;
        }

        GestorCuentas.Resultado r = GestorCuentas.registrar(usuario, nombre, correo, password);
        if (!r.exito()) {
            responder(intercambio, 409, false, r.mensaje());
            return;
        }
        System.out.println("[*] Nueva cuenta (web): " + r.usuario() + " desde " + origen(intercambio));
        abrirSesion(intercambio, r);
    }

    private static void abrirSesion(HttpExchange intercambio, GestorCuentas.Resultado r) throws IOException {
        SesionWeb sesion = new SesionWeb(r.usuario());
        if (!SesionesActivas.ocupar(r.usuario(), sesion)) {
            System.out.println("[!] " + r.usuario() + " ya está conectado, se rechazó el acceso web desde " + origen(intercambio));
            responder(intercambio, 409, false, SesionesActivas.mensajeOcupado(r.usuario()));
            return;
        }
        System.out.println("[*] " + r.usuario() + " inició sesión (web) desde " + origen(intercambio));

        SESIONES.values().removeIf(s -> !SesionesActivas.ocupa(s.usuario, s));
        String token = nuevoToken();
        SESIONES.put(token, sesion);

        GestorCuentas.Perfil perfil = GestorCuentas.obtenerPerfil(r.usuario());
        responder(intercambio, 200, true, r.mensaje(),
                "usuario", r.usuario(),
                "nombre", perfil != null ? perfil.nombre() : r.usuario(),
                "token", token);
    }

    /** Si faltaron latidos pero nadie más entró con ese usuario, la sesión continúa (p. ej. el celular se bloqueó un rato). */
    private static void latido(HttpExchange intercambio) throws IOException {
        String token = tokenDe(intercambio);
        SesionWeb sesion = token != null ? SESIONES.get(token) : null;
        if (sesion == null || !SesionesActivas.ocupa(sesion.usuario, sesion)) {
            responder(intercambio, 401, false, "Tu sesión ya no está activa, inicia sesión de nuevo.");
            return;
        }
        sesion.renovar();
        responder(intercambio, 200, true, "Sesión activa.");
    }

    private static void cerrarSesion(HttpExchange intercambio) throws IOException {
        String token = tokenDe(intercambio);
        SesionWeb sesion = token != null ? SESIONES.remove(token) : null;
        if (sesion != null) {
            SesionesActivas.liberar(sesion.usuario, sesion);
            System.out.println("[*] " + sesion.usuario + " cerró sesión (web)");
        }
        responder(intercambio, 200, true, "Sesión cerrada.");
    }

    private static String tokenDe(HttpExchange intercambio) {
        String autorizacion = intercambio.getRequestHeaders().getFirst("Authorization");
        return autorizacion != null && autorizacion.startsWith("Bearer ")
                ? autorizacion.substring("Bearer ".length())
                : null;
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
