package cliente;

import comun.Protocolo;
import comun.Validador;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Ventana con cuatro pantallas: conectar, iniciar sesión, crear cuenta y mi cuenta. */
public class VentanaPrincipal extends JFrame {
    private static final String CONEXION = "conexion";
    private static final String LOGIN = "login";
    private static final String REGISTRO = "registro";
    private static final String CUENTA = "cuenta";

    private final CardLayout tarjetas = new CardLayout();
    private final JPanel contenedor = new JPanel(tarjetas);
    private final JLabel barraEstado = new JLabel(" ");

    private final JTextField campoHost = new JTextField("localhost", 18);
    private final JTextField campoPuerto = new JTextField(String.valueOf(Protocolo.PUERTO_POR_DEFECTO), 18);

    private final JTextField loginUsuario = new JTextField(18);
    private final JPasswordField loginPassword = new JPasswordField(18);

    private final JTextField regUsuario = new JTextField(18);
    private final JTextField regNombre = new JTextField(18);
    private final JTextField regCorreo = new JTextField(18);
    private final JPasswordField regPassword = new JPasswordField(18);
    private final JPasswordField regConfirmar = new JPasswordField(18);

    private final JLabel lblUsuario = new JLabel();
    private final JLabel lblNombre = new JLabel();
    private final JLabel lblCorreo = new JLabel();
    private final JLabel lblFecha = new JLabel();

    private ConexionServidor conexion;
    private boolean ocupado;
    private String estadoPrevio = " ";

    public VentanaPrincipal() {
        super("Registro de clientes");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                cerrarConexion();
            }
        });

        contenedor.add(crearPanelConexion(), CONEXION);
        contenedor.add(crearPanelLogin(), LOGIN);
        contenedor.add(crearPanelRegistro(), REGISTRO);
        contenedor.add(crearPanelCuenta(), CUENTA);

        barraEstado.setBorder(BorderFactory.createEmptyBorder(4, 10, 6, 10));
        add(contenedor, BorderLayout.CENTER);
        add(barraEstado, BorderLayout.SOUTH);
        configurarPantallaOcupado();

        pack();
        setResizable(false);
        setLocationRelativeTo(null);
        mostrar(CONEXION, "Desconectado");
    }

    // ---------- Pantallas ----------

    private JPanel crearPanelConexion() {
        campoPuerto.addActionListener(e -> conectar());
        return crearFormulario("Conectar al servidor",
                new String[]{"IP del servidor:", "Puerto:"},
                new JComponent[]{campoHost, campoPuerto},
                filaBotones(boton("Conectar", this::conectar)));
    }

    private JPanel crearPanelLogin() {
        loginPassword.addActionListener(e -> iniciarSesion());
        return crearFormulario("Iniciar sesión",
                new String[]{"Usuario:", "Contraseña:"},
                new JComponent[]{loginUsuario, loginPassword},
                filaBotones(
                        boton("Entrar", this::iniciarSesion),
                        boton("Crear cuenta", () -> mostrar(REGISTRO, null)),
                        boton("Desconectar", this::desconectar)));
    }

    private JPanel crearPanelRegistro() {
        JLabel ayuda = new JLabel("Mínimo 8 caracteres, con letras y números");
        ayuda.setFont(ayuda.getFont().deriveFont(Font.ITALIC, 11f));
        regConfirmar.addActionListener(e -> registrar());
        return crearFormulario("Crear cuenta",
                new String[]{"Usuario:", "Nombre completo:", "Correo:", "Contraseña:", "", "Confirmar contraseña:"},
                new JComponent[]{regUsuario, regNombre, regCorreo, regPassword, ayuda, regConfirmar},
                filaBotones(
                        boton("Crear cuenta", this::registrar),
                        boton("Volver", () -> { limpiarRegistro(); mostrar(LOGIN, null); })));
    }

    private JPanel crearPanelCuenta() {
        return crearFormulario("Mi cuenta",
                new String[]{"Usuario:", "Nombre:", "Correo:", "Registrado el:"},
                new JComponent[]{lblUsuario, lblNombre, lblCorreo, lblFecha},
                filaBotones(
                        boton("Cerrar sesión", this::cerrarSesion),
                        boton("Borrar cuenta", this::borrarCuenta)));
    }

    // ---------- Acciones ----------

    private void conectar() {
        String host = campoHost.getText().trim();
        int puerto;
        try {
            puerto = Integer.parseInt(campoPuerto.getText().trim());
            if (puerto < 1 || puerto > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            aviso("El puerto no es válido.");
            return;
        }
        if (host.isEmpty()) {
            aviso("Escribe la IP del servidor.");
            return;
        }

        ejecutar(() -> new ConexionServidor(host, puerto, this::confirmarServidorNuevo), nueva -> {
            conexion = nueva;
            mostrar(LOGIN, "Conectado a " + host + ":" + puerto + " (cifrado TLS)");
            loginUsuario.requestFocusInWindow();
        });
    }

    private void iniciarSesion() {
        String usuario = loginUsuario.getText().trim();
        String password = new String(loginPassword.getPassword());
        loginPassword.setText("");
        if (usuario.isEmpty() || password.isEmpty()) {
            aviso("Escribe tu usuario y contraseña.");
            return;
        }

        ejecutar(() -> {
            String[] respuesta = conexion.solicitar(Protocolo.LOGIN, usuario, password);
            return esOk(respuesta) ? conexion.solicitar(Protocolo.PERFIL) : respuesta;
        }, respuesta -> {
            if (!esOk(respuesta)) {
                error(respuesta);
                return;
            }
            lblUsuario.setText(respuesta[1]);
            lblNombre.setText(respuesta[2]);
            lblCorreo.setText(respuesta[3]);
            lblFecha.setText(respuesta[4]);
            mostrar(CUENTA, "Sesión iniciada como " + respuesta[1]);
        });
    }

    private void registrar() {
        String usuario = regUsuario.getText().trim();
        String nombre = regNombre.getText().trim();
        String correo = regCorreo.getText().trim();
        String password = new String(regPassword.getPassword());
        String confirmar = new String(regConfirmar.getPassword());

        String problema = Validador.validarRegistro(usuario, nombre, correo, password);
        if (problema == null && !password.equals(confirmar)) {
            problema = "Las contraseñas no coinciden.";
        }
        if (problema != null) {
            aviso(problema);
            return;
        }

        ejecutar(() -> conexion.solicitar(Protocolo.REGISTRAR, usuario, nombre, correo, password), respuesta -> {
            if (!esOk(respuesta)) {
                error(respuesta);
                return;
            }
            limpiarRegistro();
            JOptionPane.showMessageDialog(this, "Cuenta creada. Ya puedes iniciar sesión.",
                    "Cuenta creada", JOptionPane.INFORMATION_MESSAGE);
            loginUsuario.setText(usuario);
            mostrar(LOGIN, "Cuenta creada: " + usuario);
            loginPassword.requestFocusInWindow();
        });
    }

    private void cerrarSesion() {
        ejecutar(() -> conexion.solicitar(Protocolo.LOGOUT), respuesta -> {
            limpiarCuenta();
            mostrar(LOGIN, "Sesión cerrada");
        });
    }

    private void borrarCuenta() {
        JPasswordField campo = new JPasswordField(18);
        Object[] contenido = {
                "Esta acción eliminará tu cuenta de forma permanente.",
                "Escribe tu contraseña para confirmar:",
                campo
        };
        int opcion = JOptionPane.showConfirmDialog(this, contenido, "Borrar cuenta",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (opcion != JOptionPane.OK_OPTION) {
            return;
        }
        String password = new String(campo.getPassword());
        if (password.isEmpty()) {
            aviso("Debes escribir tu contraseña.");
            return;
        }
        int seguro = JOptionPane.showConfirmDialog(this,
                "¿Seguro que quieres borrar la cuenta \"" + lblUsuario.getText() + "\"?\nNo se puede deshacer.",
                "Confirmar", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (seguro != JOptionPane.YES_OPTION) {
            return;
        }

        ejecutar(() -> conexion.solicitar(Protocolo.BORRAR_CUENTA, password), respuesta -> {
            if (!esOk(respuesta)) {
                error(respuesta);
                return;
            }
            limpiarCuenta();
            loginUsuario.setText("");
            JOptionPane.showMessageDialog(this, respuesta[1], "Cuenta borrada", JOptionPane.INFORMATION_MESSAGE);
            mostrar(LOGIN, "Cuenta borrada");
        });
    }

    private void desconectar() {
        cerrarConexion();
        loginPassword.setText("");
        mostrar(CONEXION, "Desconectado");
    }

    /** Se llama desde el hilo de red durante la conexión TLS, por eso el diálogo se lanza en el hilo de Swing. */
    private boolean confirmarServidorNuevo(String servidor, String huella) {
        AtomicBoolean acepta = new AtomicBoolean(false);
        try {
            SwingUtilities.invokeAndWait(() -> acepta.set(JOptionPane.showConfirmDialog(this,
                    "Es la primera vez que te conectas a " + servidor + ".\n\n"
                            + "Huella SHA-256 del certificado:\n" + huella + "\n\n"
                            + "Compárala con la que aparece en la consola del servidor.\n"
                            + "¿Confiar en este servidor?",
                    "Servidor nuevo", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                    == JOptionPane.YES_OPTION));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (InvocationTargetException e) {
            return false;
        }
        return acepta.get();
    }

    // ---------- Utilidades ----------

    /** Ejecuta la operación de red fuera del hilo de Swing para que la ventana no se congele. */
    private <T> void ejecutar(Callable<T> tarea, Consumer<T> alTerminar) {
        if (ocupado) {
            return;
        }
        setOcupado(true);
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return tarea.call();
            }

            @Override
            protected void done() {
                setOcupado(false);
                try {
                    alTerminar.accept(get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    falloDeConexion(e.getCause());
                }
            }
        }.execute();
    }

    private void falloDeConexion(Throwable causa) {
        boolean estabaConectado = conexion != null;
        cerrarConexion();
        limpiarCuenta();
        mostrar(CONEXION, "Desconectado");
        String mensaje = causa.getMessage() != null ? causa.getMessage() : causa.getClass().getSimpleName();
        JOptionPane.showMessageDialog(this,
                (estabaConectado ? "Se perdió la conexión con el servidor:\n" : "No se pudo conectar:\n") + mensaje,
                "Error de conexión", JOptionPane.ERROR_MESSAGE);
    }

    private void cerrarConexion() {
        if (conexion != null) {
            try {
                conexion.close();
            } catch (IOException ignored) {
            }
            conexion = null;
        }
    }

    private void configurarPantallaOcupado() {
        JPanel vidrio = new JPanel();
        vidrio.setOpaque(false);
        vidrio.addMouseListener(new MouseAdapter() {}); // bloquea los clics mientras se espera
        vidrio.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        setGlassPane(vidrio);
    }

    private void setOcupado(boolean valor) {
        ocupado = valor;
        getGlassPane().setVisible(valor);
        if (valor) {
            estadoPrevio = barraEstado.getText();
            barraEstado.setText("Procesando...");
        } else {
            barraEstado.setText(estadoPrevio);
        }
    }

    private void mostrar(String pantalla, String estado) {
        tarjetas.show(contenedor, pantalla);
        if (estado != null) {
            barraEstado.setText(estado);
        }
    }

    private void limpiarRegistro() {
        regUsuario.setText("");
        regNombre.setText("");
        regCorreo.setText("");
        regPassword.setText("");
        regConfirmar.setText("");
    }

    private void limpiarCuenta() {
        lblUsuario.setText("");
        lblNombre.setText("");
        lblCorreo.setText("");
        lblFecha.setText("");
    }

    private static boolean esOk(String[] respuesta) {
        return Protocolo.OK.equals(respuesta[0]);
    }

    private void error(String[] respuesta) {
        String mensaje = respuesta.length > 1 ? respuesta[1] : "Error desconocido.";
        JOptionPane.showMessageDialog(this, mensaje, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void aviso(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Revisa los datos", JOptionPane.WARNING_MESSAGE);
    }

    private static JPanel crearFormulario(String titulo, String[] etiquetas, JComponent[] campos, JComponent botones) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 30, 10, 30));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);

        JLabel lblTitulo = new JLabel(titulo);
        lblTitulo.setFont(lblTitulo.getFont().deriveFont(Font.BOLD, 18f));
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        c.insets = new Insets(0, 5, 15, 5);
        panel.add(lblTitulo, c);

        c.gridwidth = 1;
        c.insets = new Insets(5, 5, 5, 5);
        for (int i = 0; i < etiquetas.length; i++) {
            c.gridy = i + 1;
            c.gridx = 0;
            c.anchor = GridBagConstraints.LINE_END;
            c.fill = GridBagConstraints.NONE;
            c.weightx = 0;
            panel.add(new JLabel(etiquetas[i]), c);

            c.gridx = 1;
            c.anchor = GridBagConstraints.LINE_START;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1;
            panel.add(campos[i], c);
        }

        c.gridy = etiquetas.length + 1;
        c.gridx = 0;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.CENTER;
        c.insets = new Insets(15, 5, 5, 5);
        panel.add(botones, c);
        return panel;
    }

    private static JPanel filaBotones(JButton... botones) {
        JPanel fila = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        for (JButton b : botones) {
            fila.add(b);
        }
        return fila;
    }

    private static JButton boton(String texto, Runnable accion) {
        JButton b = new JButton(texto);
        b.addActionListener(e -> accion.run());
        return b;
    }
}
