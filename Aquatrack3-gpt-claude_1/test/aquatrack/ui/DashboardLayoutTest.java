package aquatrack.ui;

import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Database-free checks for first-open dashboard proportions and readable single-line captions. */
public final class DashboardLayoutTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception e) { throw new AssertionError(e); }
            DashboardPanel dashboard = new DashboardPanel();
            JSplitPane split = find(dashboard, JSplitPane.class);
            resize(dashboard, 0, 0);
            for (int[] size : new int[][] {{954, 720}, {1668, 1032}, {1114, 720}, {920, 660}}) {
                resize(dashboard, size[0], size[1]);
                check(split.getLeftComponent().getWidth() == (int) Math.round(available(split) * 0.45),
                        "initial chart share follows available width before first paint");
                checkCaptions(dashboard);
            }
            resize(dashboard, 1668, 1032);
            split.setDividerLocation(split.getInsets().left + 900);
            walk(dashboard);
            check(split.getLeftComponent().getWidth() == 900, "manual chart width applied");
            double selectedShare = 900.0 / available(split);
            CardLayout layout = new CardLayout();
            JPanel cards = new JPanel(layout);
            cards.add(dashboard, "dashboard");
            cards.add(new JPanel(), "other");
            layout.show(cards, "other");
            resize(cards, 920, 660);
            check(!dashboard.isVisible(), "dashboard hidden during resize");
            check(split.getLeftComponent().getWidth() == (int) Math.round(available(split) * selectedShare),
                    "manual share persists on hidden resize");
            resize(cards, 1668, 1032);
            layout.show(cards, "dashboard");
            walk(cards);
            check(split.getLeftComponent().getWidth() == 900, "manual share restored after navigation");
            // Check that a temporary minimum-width clamp does not overwrite the saved share.
            split.setDividerLocation(split.getInsets().left + 1200);
            walk(cards);
            resize(cards, 920, 660);
            check(split.getRightComponent().getWidth() == 320, "recent orders retain minimum width");
            resize(cards, 1668, 1032);
            check(split.getLeftComponent().getWidth() == 1200, "share restored after constrained layout");
            render(new DashboardPanel(), 1668, 1032, 1, "dashboard-adjusted.png");
            render(new DashboardPanel(), 1114, 720, 1, "dashboard-adjusted-1366.png");
            render(new DashboardPanel(), 1028, 672, 1.5, "dashboard-adjusted-150.png");
        });
        System.out.println("DashboardLayoutTest passed: " + checks + " checks.");
    }

    private static void checkCaptions(Container parent) {
        for (String text : new String[] {"Today's sales", "Pending deliveries", "Gallons out with customers", "Low or out of stock"}) {
            JLabel label = findLabel(parent, text);
            check(label != null, "caption remains plain, single-line text: " + text);
            check(label.getFontMetrics(label.getFont()).stringWidth(text) <= label.getWidth(),
                    "complete caption fits without an ellipsis: " + text);
            check(label.getFont().getSize2D() >= 10, "caption remains legible: " + text);
        }
    }

    private static void resize(Container component, int width, int height) {
        component.setSize(width, height);
        for (int i = 0; i < 3; i++) walk(component);
    }
    private static void walk(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents()) if (child instanceof Container nested) walk(nested);
    }
    private static int available(JSplitPane split) {
        return split.getWidth() - split.getInsets().left - split.getInsets().right - split.getDividerSize();
    }
    private static JLabel findLabel(Container parent, String text) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JLabel label && text.equals(label.getText())) return label;
            if (child instanceof Container nested) { JLabel found = findLabel(nested, text); if (found != null) return found; }
        }
        return null;
    }
    private static <T> T find(Container parent, Class<T> type) {
        for (Component child : parent.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container nested) { T found = find(nested, type); if (found != null) return found; }
        }
        return null;
    }
    private static void render(DashboardPanel panel, int width, int height, double scale, String name) {
        panel.addNotify();
        resize(panel, width, height);
        BufferedImage image = new BufferedImage((int) Math.round(width * scale), (int) Math.round(height * scale),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.scale(scale, scale);
        panel.paint(graphics);
        graphics.dispose();
        panel.removeNotify();
        try {
            Files.createDirectories(Path.of("build", "qa"));
            ImageIO.write(image, "png", Path.of("build", "qa", name).toFile());
        } catch (Exception e) { throw new AssertionError(e); }
        checkCaptions(panel);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
