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
        SwingUtilities.invokeLater(() -> {
            try {
                Database.getConnection().close();
            } catch (SQLException e) {
                JOptionPane.showMessageDialog(null,
                        "AquaTrack cannot reach its database.\n\n" + e.getMessage()
                        + "\n\nStart Apache and MySQL in the XAMPP Control Panel, then run AquaTrack again.",
                        "AquaTrack", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
                return;
            }
            new LoginFrame().setVisible(true);
        });
    }
}
