package cliente;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class ClienteApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // Si falla se usa el aspecto por defecto de Swing
            }
            new VentanaPrincipal().setVisible(true);
        });
    }
}
