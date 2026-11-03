package cliente;

import comun.Protocolo;
import comun.Validador;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;

/** Cliente de terminal: menús para crear cuenta, iniciar sesión y borrar la cuenta. */
public class ClienteConsola {
    private final Console consola = System.console();
    private final BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));
    private ConexionServidor conexion;
    private String usuarioActual;

    public static void main(String[] args) {
        new ClienteConsola().iniciar();
    }

    private void iniciar() {
        System.out.println("=== Registro de clientes ===");
        if (consola == null) {
            System.out.println("(Aviso: no se detectó una terminal, las contraseñas se verán al escribirlas)");
        }

        try {
            conectar();
            boolean seguir = true;
            while (seguir) {
                seguir = usuarioActual == null ? menuPrincipal() : menuCuenta();
            }
        } catch (EntradaTerminada e) {
            // El usuario cerró la entrada (Ctrl+Z / Ctrl+D)
        } catch (IOException e) {
            System.out.println("\nSe perdió la conexión con el servidor: " + e.getMessage());
        } finally {
            cerrarConexion();
        }
        System.out.println("Hasta luego.");
    }

    // ---------- Menús ----------

    private void conectar() {
        while (true) {
            String host = leer("IP del servidor [localhost]: ");
            if (host.isEmpty()) {
                host = "localhost";
            }
            String textoPuerto = leer("Puerto [" + Protocolo.PUERTO_POR_DEFECTO + "]: ");
            int puerto;
            try {
                puerto = textoPuerto.isEmpty() ? Protocolo.PUERTO_POR_DEFECTO : Integer.parseInt(textoPuerto);
            } catch (NumberFormatException e) {
                System.out.println("[ERROR] El puerto no es válido.\n");
                continue;
            }

            try {
                conexion = new ConexionServidor(host, puerto, this::confirmarServidorNuevo);
                System.out.println("[OK] Conectado a " + host + ":" + puerto + " (cifrado TLS)");
                return;
            } catch (IOException | GeneralSecurityException e) {
                System.out.println("[ERROR] No se pudo conectar: " + e.getMessage());
                if (!leer("¿Intentar de nuevo? (s/n): ").equalsIgnoreCase("s")) {
                    throw new EntradaTerminada();
                }
            }
        }
    }

    private boolean menuPrincipal() throws IOException {
        System.out.println("\n--- Menú principal ---");
        System.out.println("1) Crear cuenta");
        System.out.println("2) Iniciar sesión");
        System.out.println("0) Salir");
        switch (leer("Opción: ")) {
            case "1" -> crearCuenta();
            case "2" -> iniciarSesion();
            case "0" -> {
                return false;
            }
            default -> System.out.println("Opción no válida.");
        }
        return true;
    }

    private boolean menuCuenta() throws IOException {
        System.out.println("\n--- Mi cuenta (" + usuarioActual + ") ---");
        System.out.println("1) Ver mis datos");
        System.out.println("2) Cerrar sesión");
        System.out.println("3) Borrar cuenta");
        System.out.println("0) Salir");
        switch (leer("Opción: ")) {
            case "1" -> verDatos();
            case "2" -> cerrarSesion();
            case "3" -> borrarCuenta();
            case "0" -> {
                return false;
            }
            default -> System.out.println("Opción no válida.");
        }
        return true;
    }

    // ---------- Acciones ----------

    /** Cada dato se comprueba en cuanto se escribe; si está mal se vuelve a pedir solo ese. */
    private void crearCuenta() throws IOException {
        System.out.println("\n-- Crear cuenta --");
        System.out.println("(Deja un dato vacío y pulsa Enter para cancelar)");

        String usuario = pedirDato("Usuario: ", u -> comprobarEnServidor(Protocolo.COMPROBAR_USUARIO, u, Validador.validarUsuario(u)));
        if (usuario == null) {
            return;
        }
        String nombre = pedirDato("Nombre completo: ", Validador::validarNombre);
        if (nombre == null) {
            return;
        }
        String correo = pedirDato("Correo: ", c -> comprobarEnServidor(Protocolo.COMPROBAR_CORREO, c, Validador.validarCorreo(c)));
        if (correo == null) {
            return;
        }
        String password = pedirPin();
        if (password == null) {
            return;
        }

        mostrarResultado(conexion.solicitar(Protocolo.REGISTRAR, usuario, nombre, correo, password));
    }

    /** Pide un dato hasta que sea válido. Devuelve null si se deja vacío (cancelar). */
    private String pedirDato(String mensaje, Comprobacion comprobacion) throws IOException {
        while (true) {
            String valor = leer(mensaje);
            if (valor.isEmpty()) {
                System.out.println("Registro cancelado.");
                return null;
            }
            String problema = comprobacion.problema(valor);
            if (problema == null) {
                System.out.println("  [OK]");
                return valor;
            }
            System.out.println("  [ERROR] " + problema);
        }
    }

    /** Primero las reglas locales (sin gastar una petición) y después si está libre en el servidor. */
    private String comprobarEnServidor(String comando, String valor, String problemaLocal) throws IOException {
        if (problemaLocal != null) {
            return problemaLocal;
        }
        String[] respuesta = conexion.solicitar(comando, valor);
        return esOk(respuesta) ? null : respuesta[1];
    }

    private String pedirPin() {
        System.out.println("(La contraseña es un PIN de exactamente 4 números)");
        while (true) {
            String password = leerPassword("Contraseña: ");
            if (password.isEmpty()) {
                System.out.println("Registro cancelado.");
                return null;
            }
            String problema = Validador.validarPassword(password);
            if (problema != null) {
                System.out.println("  [ERROR] " + problema);
                continue;
            }
            if (!password.equals(leerPassword("Confirmar contraseña: "))) {
                System.out.println("  [ERROR] Las contraseñas no coinciden, escríbela de nuevo.");
                continue;
            }
            return password;
        }
    }

    @FunctionalInterface
    private interface Comprobacion {
        /** Motivo por el que el dato no vale, o null si es válido. */
        String problema(String valor) throws IOException;
    }

    private void iniciarSesion() throws IOException {
        System.out.println("\n-- Iniciar sesión --");
        String usuario = leer("Usuario: ");
        String password = leerPassword("Contraseña: ");
        if (usuario.isEmpty() || password.isEmpty()) {
            System.out.println("[ERROR] Escribe tu usuario y contraseña.");
            return;
        }

        String[] respuesta = conexion.solicitar(Protocolo.LOGIN, usuario, password);
        mostrarResultado(respuesta);
        if (esOk(respuesta)) {
            verDatos();
        }
    }

    private void verDatos() throws IOException {
        String[] r = conexion.solicitar(Protocolo.PERFIL);
        if (!esOk(r)) {
            mostrarResultado(r);
            return;
        }
        usuarioActual = r[1];
        System.out.println("\n  Usuario:        " + r[1]);
        System.out.println("  Nombre:         " + r[2]);
        System.out.println("  Correo:         " + r[3]);
        System.out.println("  Registrado el:  " + r[4]);
    }

    private void cerrarSesion() throws IOException {
        mostrarResultado(conexion.solicitar(Protocolo.LOGOUT));
        usuarioActual = null;
    }

    private void borrarCuenta() throws IOException {
        System.out.println("\n-- Borrar cuenta --");
        System.out.println("Esta acción eliminará tu cuenta de forma permanente.");
        String password = leerPassword("Escribe tu contraseña para confirmar: ");
        if (password.isEmpty()) {
            System.out.println("Cancelado.");
            return;
        }
        if (!leer("¿Seguro que quieres borrar la cuenta \"" + usuarioActual + "\"? (s/n): ").equalsIgnoreCase("s")) {
            System.out.println("Cancelado.");
            return;
        }

        String[] respuesta = conexion.solicitar(Protocolo.BORRAR_CUENTA, password);
        mostrarResultado(respuesta);
        if (esOk(respuesta)) {
            usuarioActual = null;
        }
    }

    /** Se llama durante la conexión TLS la primera vez que se ve este servidor. */
    private boolean confirmarServidorNuevo(String servidor, String huella) {
        System.out.println("\nEs la primera vez que te conectas a " + servidor + ".");
        System.out.println("Huella SHA-256 del certificado:");
        System.out.println("   " + huella);
        System.out.println("Compárala con la que aparece en la consola del servidor.");
        return leer("¿Confiar en este servidor? (s/n): ").equalsIgnoreCase("s");
    }

    // ---------- Utilidades ----------

    private String leer(String mensaje) {
        String linea;
        if (consola != null) {
            linea = consola.readLine("%s", mensaje);
        } else {
            System.out.print(mensaje);
            try {
                linea = teclado.readLine();
            } catch (IOException e) {
                linea = null;
            }
        }
        if (linea == null) {
            throw new EntradaTerminada();
        }
        return linea.trim();
    }

    /** Con una terminal real la contraseña no se muestra mientras se escribe. */
    private String leerPassword(String mensaje) {
        if (consola == null) {
            return leer(mensaje);
        }
        char[] password = consola.readPassword("%s", mensaje);
        if (password == null) {
            throw new EntradaTerminada();
        }
        return new String(password);
    }

    private static boolean esOk(String[] respuesta) {
        return Protocolo.OK.equals(respuesta[0]);
    }

    private static void mostrarResultado(String[] respuesta) {
        String mensaje = respuesta.length > 1 ? respuesta[1] : "";
        System.out.println((esOk(respuesta) ? "[OK] " : "[ERROR] ") + mensaje);
    }

    private void cerrarConexion() {
        if (conexion != null) {
            try {
                conexion.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static class EntradaTerminada extends RuntimeException {}
}
