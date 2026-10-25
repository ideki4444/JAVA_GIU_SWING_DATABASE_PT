package aquatrack.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.LayoutManager;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import aquatrack.model.User;

/** Testable sign-in content with responsive artwork and a bounded-width form. */
final class LoginPanel extends JPanel {
    interface Feedback {
        void success(String message);
        void warning(String message);
        void error(String message);
    }
    private final JTextField usernameField = UiKit.field(23);
    private final JPasswordField passwordField = new JPasswordField(23);
    private final JCheckBox showPassword = new JCheckBox("Show password");
    private final JLabel status = UiKit.muted(" ");
    private final UiKit.ThemedButton signIn = UiKit.button("Sign in", UiKit.ACCENT);
    private final LoginAuthenticator authenticator;
    private final Consumer<User> onSuccess;
    private final Feedback feedback;
    private final JPanel brand;
    private final JScrollPane formScroll;
    private boolean pending;
    private boolean closed;
    private final char passwordEcho;

    LoginPanel(LoginAuthenticator authenticator, Consumer<User> onSuccess, Feedback feedback) {
        this.authenticator = authenticator;
        this.onSuccess = onSuccess;
        this.feedback = feedback;
        UiKit.styleInput(passwordField);
        passwordEcho = passwordField.getEchoChar();
        brand = buildBrand();
        JPanel host = new JPanel(new GridBagLayout());
        host.setBackground(UiKit.BACKGROUND);
        host.setBorder(BorderFactory.createEmptyBorder(30, 28, 30, 28));
        host.add(buildForm());
        formScroll = new JScrollPane(host);
        formScroll.setBorder(BorderFactory.createEmptyBorder());
        formScroll.getVerticalScrollBar().setUnitIncrement(20);
        setLayout(new LayoutManager() {
            public void addLayoutComponent(String name, Component component) { }
            public void removeLayoutComponent(Component component) { }
            public Dimension preferredLayoutSize(Container parent) { return new Dimension(1200, 740); }
            public Dimension minimumLayoutSize(Container parent) { return new Dimension(520, 520); }
            public void layoutContainer(Container parent) {
                int width = parent.getWidth(), height = parent.getHeight();
                boolean wide = width >= 1000;
                int brandWidth = wide ? (int) (width * 0.51) : 0;
                brand.setVisible(wide);
                brand.setBounds(0, 0, brandWidth, height);
                formScroll.setBounds(brandWidth, 0, width - brandWidth, height);
            }
        });
        add(brand);
        add(formScroll);
        signIn.addActionListener(e -> attemptLogin());
        passwordField.addActionListener(e -> attemptLogin());
        usernameField.addActionListener(e -> passwordField.requestFocusInWindow());
    }

    private JPanel buildBrand() {
        JPanel panel = new JPanel(new BorderLayout(0, 20)) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setPaint(new GradientPaint(0, 0, new Color(0x092F49), getWidth(), getHeight(), new Color(0x126B7C)));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.dispose();
            }
        };
        panel.setBorder(BorderFactory.createEmptyBorder(48, 44, 42, 44));
        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        heading.add(UiKit.light("AquaTrack", new Font("Segoe UI", Font.BOLD, 38)));
        heading.add(Box.createVerticalStrut(12));
        heading.add(UiKit.light("<html>Water refilling station<br>management</html>", new Font("Segoe UI", Font.PLAIN, 22)));
        panel.add(heading, BorderLayout.NORTH);
        panel.add(new WaterArtwork(), BorderLayout.CENTER);
        JPanel footer = new JPanel();
        footer.setOpaque(false);
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        footer.add(UiKit.light("One place for your daily operations", UiKit.SECTION));
        footer.add(Box.createVerticalStrut(10));
        footer.add(UiKit.light("<html>Orders and deliveries · Products and inventory<br>Customer balances and container returns</html>", UiKit.BODY));
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildForm() {
        JPanel form = UiKit.card();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(32, 30, 32, 30));
        form.setPreferredSize(new Dimension(420, 505));
        form.add(UiKit.muted("AQUATRACK"));
        form.add(Box.createVerticalStrut(14));
        form.add(UiKit.titleLabel("Welcome back"));
        form.add(Box.createVerticalStrut(7));
        form.add(UiKit.muted("Sign in to manage your station."));
        form.add(Box.createVerticalStrut(28));
        JLabel username = UiKit.label("Username");
        username.setLabelFor(usernameField);
        usernameField.getAccessibleContext().setAccessibleName("Username");
        form.add(username);
        form.add(Box.createVerticalStrut(7));
        form.add(UiKit.fullWidth(usernameField));
        form.add(Box.createVerticalStrut(18));
        JLabel password = UiKit.label("Password");
        password.setLabelFor(passwordField);
        passwordField.getAccessibleContext().setAccessibleName("Password");
        form.add(password);
        form.add(Box.createVerticalStrut(7));
        form.add(UiKit.fullWidth(passwordField));
        form.add(Box.createVerticalStrut(9));
        showPassword.setOpaque(false);
        showPassword.setFont(UiKit.BODY);
        showPassword.addActionListener(e -> passwordField.setEchoChar(showPassword.isSelected() ? 0 : passwordEcho));
        form.add(showPassword);
        form.add(Box.createVerticalStrut(22));
        form.add(UiKit.fullWidth(signIn));
        form.add(Box.createVerticalStrut(14));
        status.setPreferredSize(new Dimension(350, 38));
        form.add(status);
        form.add(Box.createVerticalStrut(12));
        form.add(UiKit.muted("<html>Need an account? Ask your station owner.</html>"));
        return form;
    }

    void attemptLogin() {
        if (pending || closed) return;
        String username = usernameField.getText().trim();
        char[] input = passwordField.getPassword();
        if (username.isEmpty() || input.length == 0) {
            Arrays.fill(input, '\0');
            status.setText("Enter your username and password.");
            feedback.warning(LoginAuthenticator.Outcome.INCOMPLETE.getMessage());
            (username.isEmpty() ? usernameField : passwordField).requestFocusInWindow();
            return;
        }
        String password = new String(input);
        Arrays.fill(input, '\0');
        setPending(true);
        status.setText("Signing in…");
        new SwingWorker<LoginAuthenticator.Result, Void>() {
            @Override protected LoginAuthenticator.Result doInBackground() throws Exception {
                return authenticator.authenticate(username, password);
            }
            @Override protected void done() {
                if (closed) return;
                try {
                    LoginAuthenticator.Result result = get();
                    LoginAuthenticator.Outcome outcome = result.getOutcome();
                    if (outcome == LoginAuthenticator.Outcome.SUCCESS) {
                        passwordField.setText("");
                        status.setText(outcome.getMessage());
                        feedback.success(outcome.getMessage());
                        onSuccess.accept(result.getUser());
                    } else {
                        setPending(false);
                        status.setText(outcome.getMessage());
                        if (outcome == LoginAuthenticator.Outcome.INCORRECT_PASSWORD) {
                            // The username matched, so only the password is typed again.
                            passwordField.setText("");
                            passwordField.requestFocusInWindow();
                        } else {
                            // No account matched, so start again from a clean form.
                            usernameField.setText("");
                            passwordField.setText("");
                            showPassword.setSelected(false);
                            passwordField.setEchoChar(passwordEcho);
                            usernameField.requestFocusInWindow();
                        }
                        feedback.warning(outcome.getMessage());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    setPending(false);
                    status.setText("Sign-in interrupted. Please try again.");
                } catch (ExecutionException e) {
                    setPending(false);
                    status.setText("Could not connect. You can try again.");
                    feedback.error("Sign-in could not be completed. Check that MySQL is running and try again."
                            + "\nYour account details have not been rejected.");
                }
            }
        }.execute();
    }

    private void setPending(boolean value) {
        pending = value;
        signIn.setEnabled(!value);
        usernameField.setEnabled(!value);
        passwordField.setEnabled(!value);
        showPassword.setEnabled(!value);
    }

    void close() { closed = true; }
    UiKit.ThemedButton signInButton() { return signIn; }
}
