package aquatrack.ui;

import aquatrack.model.User;
import aquatrack.util.PasswordUtil;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;

/** Behavioral login regression and real Swing rendering without a database or display. */
public final class LoginAndThemeTest {
    private static int checks;
    private static final String HASH = PasswordUtil.hash("password123");
    private static final User OWNER = new User(1, "owner", "Demo Owner", "Owner/Admin", HASH);

    private static final class Feedback implements LoginPanel.Feedback {
        final AtomicReference<String> success = new AtomicReference<>();
        final AtomicReference<String> warning = new AtomicReference<>();
        final AtomicReference<String> error = new AtomicReference<>();
        final CountDownLatch done = new CountDownLatch(1);
        public void success(String value) { success.set(value); }
        public void warning(String value) { warning.set(value); done.countDown(); }
        public void error(String value) { error.set(value); done.countDown(); }
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        SwingUtilities.invokeAndWait(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception e) { throw new RuntimeException(e); }
        });
        // Unknown username: "Account doesn't exist", and both fields are cleared.
        rejected("wrong-user", null, "Account doesn't exist", false);
        // Known username, wrong password: "Password is incorrect", the username stays, the password is cleared.
        rejected("owner", OWNER, "Password is incorrect", true);
        // Inactive accounts are treated as missing.
        rejected("off", new User(2, "off", "Disabled User", "Staff", HASH, false), "Account doesn't exist", false);
        incomplete();
        databaseFailure();
        duplicateSubmitAndSuccess();
        centeringAndRender();
        System.out.println("LoginAndThemeTest passed: " + checks + " checks.");
    }

    private static void rejected(String typedUsername, User returned, String expectedMessage, boolean keepUsername)
            throws Exception {
        Feedback feedback = new Feedback();
        AtomicReference<LoginPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            panel.set(new LoginPanel(new LoginAuthenticator(name -> returned), u -> {
                throw new AssertionError("Unexpected successful login");
            }, feedback));
            fill(panel.get(), typedUsername, "wrong-password");
            panel.get().attemptLogin();
        });
        check(feedback.done.await(5, TimeUnit.SECONDS), "login completed");
        SwingUtilities.invokeAndWait(() -> {
            check(expectedMessage.equals(feedback.warning.get()), "popup message: " + expectedMessage);
            String remaining = username(panel.get()).getText();
            check(keepUsername ? typedUsername.equals(remaining) : remaining.isEmpty(),
                    keepUsername ? "username kept for the retry" : "username cleared for a new attempt");
            check(password(panel.get()).getPassword().length == 0, "password cleared");
            check(panel.get().signInButton().isEnabled(), "retry enabled");
        });
    }

    private static void incomplete() throws Exception {
        Feedback feedback = new Feedback();
        SwingUtilities.invokeAndWait(() -> {
            LoginPanel panel = new LoginPanel(new LoginAuthenticator(name -> {
                throw new AssertionError("Incomplete input must not query database");
            }), user -> { }, feedback);
            fill(panel, "owner", "");
            panel.attemptLogin();
            check(feedback.warning.get().contains("both"), "missing input prompt");
            check("owner".equals(username(panel).getText()), "valid input preserved");
        });
    }

    private static void databaseFailure() throws Exception {
        Feedback feedback = new Feedback();
        AtomicReference<LoginPanel> panel = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            panel.set(new LoginPanel(new LoginAuthenticator(name -> { throw new SQLException("offline"); }), u -> { }, feedback));
            fill(panel.get(), "owner", "password123");
            panel.get().attemptLogin();
        });
        check(feedback.done.await(5, TimeUnit.SECONDS), "database failure completed");
        SwingUtilities.invokeAndWait(() -> {
            check(feedback.error.get() != null && feedback.warning.get() == null, "database error distinct");
            check("owner".equals(username(panel.get()).getText()), "database failure preserves username");
            check(panel.get().signInButton().isEnabled(), "database retry enabled");
        });
    }

    private static void duplicateSubmitAndSuccess() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), completed = new CountDownLatch(1);
        AtomicReference<LoginPanel> panel = new AtomicReference<>();
        Feedback feedback = new Feedback();
        SwingUtilities.invokeAndWait(() -> {
            panel.set(new LoginPanel(new LoginAuthenticator(name -> {
                check(!SwingUtilities.isEventDispatchThread(), "authentication outside EDT");
                calls.incrementAndGet(); entered.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new SQLException("test timeout"); }
                catch (InterruptedException e) { throw new SQLException(e); }
                return OWNER;
            }), user -> {
                check(SwingUtilities.isEventDispatchThread(), "success on EDT");
                check(user == OWNER, "authenticated identity returned");
                completed.countDown();
            }, feedback));
            fill(panel.get(), "owner", "password123");
            panel.get().attemptLogin(); panel.get().attemptLogin();
            check(!panel.get().signInButton().isEnabled(), "submit disabled while pending");
        });
        check(entered.await(5, TimeUnit.SECONDS), "worker entered");
        release.countDown();
        check(completed.await(5, TimeUnit.SECONDS), "success callback");
        check("Log-in Successfully".equals(feedback.success.get()), "success popup says Log-in Successfully");
        check(calls.get() == 1, "duplicate request rejected");
    }

    private static void centeringAndRender() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTable table = UiKit.table(new DefaultTableModel(new Object[][] {{"Water", 12, "₱25.00", "Low Stock"}},
                    new Object[] {"Item", "Count", "Price", "Status"}));
            UiKit.styleTable(table); UiKit.statusColumn(table, 3); UiKit.alignCenter(table, 1, 2);
            for (int i = 0; i < 4; i++) {
                JLabel cell = (JLabel) table.prepareRenderer(table.getCellRenderer(0, i), 0, i);
                check(cell.getHorizontalAlignment() == SwingConstants.CENTER, "centered cell " + i);
                JLabel header = (JLabel) table.getTableHeader().getDefaultRenderer()
                        .getTableCellRendererComponent(table, table.getColumnName(i), false, false, -1, i);
                check(header.getHorizontalAlignment() == SwingConstants.CENTER, "centered header " + i);
            }
            LoginPanel panel = new LoginPanel(new LoginAuthenticator(name -> null), u -> { }, new Feedback());
            render(panel, 1366, 768, 1, "login-1366.png");
            render(panel, 1920, 1080, 1, "login-1920.png");
            render(panel, 1280, 720, 1.5, "login-1920-at150.png");
            render(panel, 1536, 864, 1.25, "login-1920-at125.png");
            render(panel, 640, 560, 1, "login-small.png");
        });
    }

    static void render(Component component, int width, int height, double scale, String file) {
        if (!component.isDisplayable()) component.addNotify();
        component.setSize(width, height);
        invalidateTree(component);
        for (int i = 0; i < 3; i++) layout(component);
        BufferedImage image = new BufferedImage((int) (width * scale), (int) (height * scale), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.scale(scale, scale); component.paint(g); g.dispose();
        try { Files.createDirectories(Path.of("build", "qa")); ImageIO.write(image, "png", Path.of("build", "qa", file).toFile()); }
        catch (Exception e) { throw new AssertionError(e); }
        component.removeNotify();
        check(image.getWidth() > 0, "rendered " + file);
    }

    private static void layout(Component c) { if (c instanceof Container p) { p.doLayout(); for (Component child : p.getComponents()) layout(child); } }
    private static void invalidateTree(Component c) { c.invalidate(); if (c instanceof Container p) for (Component child : p.getComponents()) invalidateTree(child); }
    private static void fill(LoginPanel panel, String user, String pass) { username(panel).setText(user); password(panel).setText(pass); }
    private static JTextField username(Container panel) { return find(panel, JTextField.class, false); }
    private static JPasswordField password(Container panel) { return find(panel, JPasswordField.class, true); }
    private static <T> T find(Container parent, Class<T> type, boolean password) {
        for (Component child : parent.getComponents()) {
            if (type.isInstance(child) && (password || !(child instanceof JPasswordField))) return type.cast(child);
            if (child instanceof Container nested) { T value = find(nested, type, password); if (value != null) return value; }
        }
        return null;
    }
    private static synchronized void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
}