package aquatrack.ui;

import aquatrack.dao.UserDao;
import aquatrack.util.Session;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.GraphicsEnvironment;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

/** Maximized desktop sign-in window with content that adapts to smaller working areas. */
public final class LoginFrame extends JFrame {
    private final LoginPanel login;

    public LoginFrame() {
        super("AquaTrack — Sign in");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setIconImages(UiKit.appIcons());
        login = new LoginPanel(new LoginAuthenticator(new UserDao()::findByUsername), user -> {
            Session.start(user);
            new MainFrame().setVisible(true);
            dispose();
        }, new LoginPanel.Feedback() {
            public void success(String message) { UiKit.info(LoginFrame.this, message); }
            public void warning(String message) { UiKit.warn(LoginFrame.this, message); }
            public void error(String message) {
                JOptionPane.showMessageDialog(LoginFrame.this, message, "Unable to sign in", JOptionPane.ERROR_MESSAGE);
            }
        });
        setContentPane(login);
        getRootPane().setDefaultButton(login.signInButton());
        Rectangle area = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(1200, area.width), Math.min(780, area.height));
        setMinimumSize(new Dimension(Math.min(560, area.width), Math.min(520, area.height)));
        setLocationRelativeTo(null);
        setExtendedState(MAXIMIZED_BOTH);
    }

    @Override public void dispose() {
        login.close();
        super.dispose();
    }
}
