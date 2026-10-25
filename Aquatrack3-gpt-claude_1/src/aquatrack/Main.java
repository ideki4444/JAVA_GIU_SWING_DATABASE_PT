package aquatrack;

import aquatrack.db.Database;
import aquatrack.ui.LoginFrame;
import java.sql.SQLException;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/** Program entry point. It checks the database first, so a stopped XAMPP shows a clear message instead of a crash. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException | UnsupportedLookAndFeelException ignored) {
            // Keep the default look and feel if the system one is unavailable.
        }
        // Login performs its connection work in a SwingWorker and offers retry if MySQL is offline.
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}
