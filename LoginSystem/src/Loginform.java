// ============================================================================
// Loginform.java  —  TWILIGHT PEAKS LOGIN  (updated)
// ----------------------------------------------------------------------------
// NEW FEATURE: smart validation now detects the case where the typed
// PASSWORD is correct but the USERNAME is wrong (belongs to another
// account or doesn't exist). It shows a dedicated "Incorrect Username"
// message instead of the generic "Account does not exist" message.
// ============================================================================
import javax.swing.*;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class Loginform extends JFrame {

    private final RoundedTextField usernameField = new RoundedTextField("Username");
    private final PasswordBox passwordField = new PasswordBox("Password"); // show/hide toggle inside

    public Loginform() {
        setTitle("Climbers Profile System");
        setSize(540, 660);
        setMinimumSize(new Dimension(600, 760));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);

        ScenePanel scene = new ScenePanel();
        scene.setLayout(new GridBagLayout());

        /* ---- one frosted card, everything stacked vertically ---- */
        GlassPanel card = new GlassPanel(null, 30);
        GridBagLayout cardLayout = new GridBagLayout();
        cardLayout.rowHeights = new int[]{0, 0, 0, 0, 0, 0, 0};
        card.setLayout(cardLayout);
        card.setPreferredSize(new Dimension(520, 680));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(6, 28, 6, 28);

        c.gridy = 0;
        c.insets = new Insets(30, 28, 2, 28);
        c.anchor = GridBagConstraints.CENTER;
        c.fill = GridBagConstraints.NONE;
        card.add(new MoonLabel(), c);

        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(2, 28, 2, 28);

        JLabel title = new JLabel("Welcome back Climber!", SwingConstants.CENTER);
        title.setFont(UIKit.font(24f, Font.BOLD));
        title.setForeground(UIKit.TEXT);
        c.gridy = 1; card.add(title, c);

        JLabel subtitle = new JLabel("Sign in to continue your climb", SwingConstants.CENTER);
        subtitle.setFont(UIKit.font(12.5f, Font.PLAIN));
        subtitle.setForeground(UIKit.TEXT_DIM);
        c.gridy = 2; c.insets = new Insets(2, 28, 16, 28); card.add(subtitle, c);

        c.insets = new Insets(6, 28, 6, 28);
        c.gridy = 3; card.add(usernameField, c);
        c.gridy = 4; card.add(passwordField, c);

        RoundedButton loginButton = new RoundedButton("Login");
        c.gridy = 5; c.insets = new Insets(40, 28, 40, 28); card.add(loginButton, c);

        scene.add(card);
        setContentPane(scene);

        loginButton.addActionListener(e -> validateLogin());
        passwordField.addActionListener(e -> validateLogin());
        getRootPane().setDefaultButton(loginButton);
    }

    
    private void validateLogin() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        String userQuery     = "SELECT password FROM users WHERE username = ?";
        String passwordQuery = "SELECT username FROM users WHERE password = ?";

        try (Connection conn = Databaseconnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(userQuery)) {

            stmt.setString(1, username);

            try (ResultSet rs = stmt.executeQuery()) {

                if (!rs.next()) {

                    if (isPasswordRegistered(conn, passwordQuery, password)) {
                        ThemedDialogs.showMessage(this,
                                "Incorrect Username",
                                "Login Failed", JOptionPane.ERROR_MESSAGE);
                        usernameField.setText("");
                        usernameField.requestFocusInWindow();
                    } else {
                        ThemedDialogs.showMessage(this,
                                "Account does not exist",
                                "Login Failed", JOptionPane.ERROR_MESSAGE);
                        usernameField.setText("");
                        passwordField.setText("");
                        usernameField.requestFocusInWindow();
                    }
                    return;
                }

                String storedPassword = rs.getString("password");

                if (!storedPassword.equals(password)) {
                    ThemedDialogs.showMessage(this, "Password is incorrect",
                            "Login Failed", JOptionPane.ERROR_MESSAGE);
                    passwordField.setText("");
                    passwordField.requestFocusInWindow();
                    return;
                }

                
                ThemedDialogs.showMessage(this, "Log-in Successfully",
                        "Success", JOptionPane.INFORMATION_MESSAGE);
                dispose();
                SwingUtilities.invokeLater(() -> new Dashboard().setVisible(true));
            }

        } catch (SQLException ex) {
            ThemedDialogs.showMessage(this,
                    "Database error: " + ex.getMessage(),
                    "Connection Error", JOptionPane.ERROR_MESSAGE);
        }
    }


    private boolean isPasswordRegistered(Connection conn, String query, String password)
            throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, password);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }
}