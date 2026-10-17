package aquatrack.ui;

import aquatrack.dao.UserDao;
import aquatrack.util.Session;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Font;
import java.sql.SQLException;
import java.util.concurrent.ExecutionException;
import javax.swing.Box;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

/** Sign-in window. A correct username and password opens the main window. */
public final class LoginFrame extends JFrame {

    private final LoginAuthenticator authenticator = new LoginAuthenticator(new UserDao()::findByUsername);
    private final JTextField usernameField = UiKit.field(20);
    private final JPasswordField passwordField = new JPasswordField(20);
    private final JLabel status = UiKit.muted(" ");
    private final UiKit.ThemedButton signIn = UiKit.button("Sign in", UiKit.ACCENT);
    private boolean loginPending;

    public LoginFrame() {
        super("AquaTrack - Sign in");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setIconImages(UiKit.appIcons());
        setLayout(new BorderLayout());
        add(buildBrandPanel(), BorderLayout.WEST);
        add(buildFormPanel(), BorderLayout.CENTER);
        setSize(860, 540);
        setResizable(false);
        setLocationRelativeTo(null);
    }

    private JPanel buildBrandPanel() {
        JPanel brand = new JPanel();
        brand.setLayout(new BoxLayout(brand, BoxLayout.Y_AXIS));
        brand.setBackground(UiKit.SIDEBAR);
        brand.setBorder(BorderFactory.createEmptyBorder(70, 44, 40, 44));
        brand.add(UiKit.light("AquaTrack", new Font("Segoe UI", Font.BOLD, 32)));
        brand.add(UiKit.light("Water refilling station management", UiKit.BODY));
        brand.add(Box.createVerticalStrut(28));
        brand.add(UiKit.light("<html><body style='width: 240px'>Customers, orders, deliveries, "
                + "gallon containers, unpaid balances and daily sales in one place.</body></html>", UiKit.BODY));
        return brand;
    }

    private JPanel buildFormPanel() {
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(UiKit.SURFACE);
        form.setBorder(BorderFactory.createEmptyBorder(80, 60, 40, 60));
        form.add(UiKit.titleLabel("Sign in"));
        form.add(Box.createVerticalStrut(4));
        form.add(UiKit.muted("Use your AquaTrack username and password."));
        form.add(Box.createVerticalStrut(26));
        form.add(UiKit.label("Username"));
        form.add(Box.createVerticalStrut(6));
        form.add(UiKit.fullWidth(usernameField));
        form.add(Box.createVerticalStrut(16));
        form.add(UiKit.label("Password"));
        form.add(Box.createVerticalStrut(6));
        UiKit.styleInput(passwordField);
        form.add(UiKit.fullWidth(passwordField));
        form.add(Box.createVerticalStrut(8));
        form.add(UiKit.fullWidth(showPasswordBox()));
        form.add(Box.createVerticalStrut(20));
        signIn.addActionListener(e -> attemptLogin());
        form.add(UiKit.fullWidth(signIn));
        form.add(Box.createVerticalStrut(14));
        form.add(status);
        getRootPane().setDefaultButton(signIn);
        return form;
    }

    /** Reveals the typed password while it is ticked, so the user can check what they typed. */
    private JCheckBox showPasswordBox() {
        JCheckBox box = new JCheckBox("Show password");
        box.setFont(UiKit.BODY);
        box.setBackground(UiKit.SURFACE);
        box.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        char hidden = passwordField.getEchoChar();
        box.addActionListener(e -> passwordField.setEchoChar(box.isSelected() ? (char) 0 : hidden));
        return box;
    }

    private void attemptLogin() {
        if (loginPending) {
            return;
        }
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        if (username.isEmpty() || password.isEmpty()) {
            status.setText(LoginAuthenticator.Outcome.INCOMPLETE.getMessage());
            if (username.isEmpty()) {
                usernameField.requestFocusInWindow();
            } else {
                passwordField.requestFocusInWindow();
            }
            return;
        }
        setLoginPending(true);
        status.setText("Signing in...");
        new SwingWorker<LoginAuthenticator.Result, Void>() {
            @Override
            protected LoginAuthenticator.Result doInBackground() throws SQLException {
                return authenticator.authenticate(username, password);
            }

            @Override
            protected void done() {
                if (!isDisplayable()) {
                    return;
                }
                try {
                    LoginAuthenticator.Result result = get();
                    status.setText(result.getOutcome().getMessage());
                    if (result.getOutcome() != LoginAuthenticator.Outcome.SUCCESS) {
                        setLoginPending(false);
                    }
                    if (result.getOutcome() == LoginAuthenticator.Outcome.ACCOUNT_NOT_FOUND) {
                        usernameField.requestFocusInWindow();
                        usernameField.selectAll();
                    } else if (result.getOutcome() == LoginAuthenticator.Outcome.INCORRECT_PASSWORD) {
                        passwordField.setText("");
                        passwordField.requestFocusInWindow();
                    } else if (result.getOutcome() == LoginAuthenticator.Outcome.SUCCESS) {
                        UiKit.info(LoginFrame.this, result.getOutcome().getMessage());
                        Session.start(result.getUser());
                        new MainFrame().setVisible(true);
                        dispose();
                    }
                } catch (InterruptedException e) {
                    setLoginPending(false);
                    Thread.currentThread().interrupt();
                    status.setText("Sign-in interrupted. Please try again.");
                } catch (ExecutionException e) {
                    setLoginPending(false);
                    Throwable cause = e.getCause();
                    status.setText(cause instanceof SQLException
                            ? "Database problem: " + cause.getMessage()
                            : "Could not sign in. Please try again.");
                }
            }
        }.execute();
    }

    private void setLoginPending(boolean pending) {
        loginPending = pending;
        signIn.setEnabled(!pending);
        usernameField.setEnabled(!pending);
        passwordField.setEnabled(!pending);
    }
}
