package aquatrack.ui;

import aquatrack.model.User;
import aquatrack.util.Session;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Standalone, database-free checks for the split panes' startup and resize lifecycle.
 * Compile and run with the project's JDK after building the production classes:
 * javac -cp build/classes -d build/test/classes test/aquatrack/ui/FixedSplitLayoutTest.java
 * java "-Djava.awt.headless=true" -cp "build/classes;build/test/classes" aquatrack.ui.FixedSplitLayoutTest
 */
public final class FixedSplitLayoutTest {

    private static final int STARTUP_WIDTH = 906;
    private static final int MAXIMIZED_WIDTH = 1642;
    private static final int SMALL_WIDTH = 706;
    private static int checks;

    private FixedSplitLayoutTest() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        SwingUtilities.invokeAndWait(() -> {
            try {
                // The application uses this LAF; on Windows this also exercises WindowsSplitPaneUI.
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                throw new AssertionError("Could not install the application's look and feel", e);
            }
            int[] formWidths = {460, 470, 280, 470, 400, 360};
            for (int width : formWidths) {
                checkHelper(width, true);
            }
            checkHelper(340, false);
            checkHelper(400, false);
            checkRealScreens();
            System.out.println("FixedSplitLayoutTest passed: " + checks + " checks ("
                    + UIManager.getLookAndFeel().getName() + ", EDT, headless).");
        });
    }

    private static void checkHelper(int width, boolean sideOnRight) {
        String name = (sideOnRight ? "right " : "left ") + width;
        JSplitPane split = sideOnRight
                ? UiKit.splitFixedRight(new JPanel(), new JPanel(), width)
                : UiKit.splitFixedLeft(new JPanel(), new JPanel(), width);

        // No paint is allowed between these layouts: hidden cards are laid out before maximization.
        resize(split, STARTUP_WIDTH);
        expectSide(name + " initial layout", split, sideOnRight, width);
        resize(split, MAXIMIZED_WIDTH);
        expectSide(name + " unpainted 906 -> 1642", split, sideOnRight, width);
        layoutTree(split);
        layoutTree(split);
        expectSide(name + " repeated layout before first paint", split, sideOnRight, width);
        paint(split);
        layoutTree(split);
        expectSide(name + " first paint", split, sideOnRight, width);

        int adjustedWidth = width + 65;
        moveDivider(split, sideOnRight, adjustedWidth);
        layoutTree(split);
        expectSide(name + " manual divider move", split, sideOnRight, adjustedWidth);

        CardLayout layout = new CardLayout();
        JPanel cards = new JPanel(layout);
        cards.add(split, "split");
        cards.add(new JPanel(), "other");
        resize(cards, MAXIMIZED_WIDTH);
        layout.show(cards, "other");
        check(!split.isVisible(), name + " card should be hidden during resize");
        resize(cards, 1300);
        expectSide(name + " manual width while hidden", split, sideOnRight, adjustedWidth);
        layout.show(cards, "split");
        layoutTree(cards);
        paint(cards);
        expectSide(name + " manual width after returning", split, sideOnRight, adjustedWidth);

        resize(cards, SMALL_WIDTH);
        expectSide(name + " constrained manual width", split, sideOnRight,
                Math.min(adjustedWidth, availableWidth(split) - 320));
        checkMainMinimum(name, split, sideOnRight);
        paint(cards);
        layoutTree(cards);
        expectSide(name + " constrained manual width after paint", split, sideOnRight,
                Math.min(adjustedWidth, availableWidth(split) - 320));
        resize(cards, MAXIMIZED_WIDTH);
        expectSide(name + " manual width restored", split, sideOnRight, adjustedWidth);

        JSplitPane initiallyZero = sideOnRight
                ? UiKit.splitFixedRight(new JPanel(), new JPanel(), width)
                : UiKit.splitFixedLeft(new JPanel(), new JPanel(), width);
        initiallyZero.setSize(0, 0);
        layoutTree(initiallyZero);
        resize(initiallyZero, MAXIMIZED_WIDTH);
        expectSide(name + " zero size followed by first layout", initiallyZero, sideOnRight, width);
        resize(initiallyZero, SMALL_WIDTH);
        expectSide(name + " constrained default width", initiallyZero, sideOnRight,
                Math.min(width, availableWidth(initiallyZero) - 320));
        checkMainMinimum(name, initiallyZero, sideOnRight);
        paint(initiallyZero);
        layoutTree(initiallyZero);
        expectSide(name + " first paint while constrained", initiallyZero, sideOnRight,
                Math.min(width, availableWidth(initiallyZero) - 320));
        resize(initiallyZero, MAXIMIZED_WIDTH);
        expectSide(name + " default width restored", initiallyZero, sideOnRight, width);
    }

    private static void checkRealScreens() {
        User previous = Session.user();
        Session.start(new User(0, "layout-test", "Layout Test", "Owner/Admin", ""));
        try {
            // Constructors only build controls; never refresh or activate database actions here.
            JPanel[] screens = {
                new DeliveriesPanel(), new ContainersPanel(), new CustomersPanel(),
                new ProductsPanel(), new InventoryPanel(), new AccountsPanel(),
                new DashboardPanel(), new OrderPanel()
            };
            int[] widths = {460, 470, 280, 470, 400, 360, 0, 400};
            CardLayout layout = new CardLayout();
            JPanel cards = new JPanel(layout);
            cards.add(new JPanel(), "other");
            for (int i = 0; i < screens.length; i++) {
                cards.add(screens[i], Integer.toString(i));
            }

            // Each screen has 24-pixel outer margins, matching the real main-window content.
            resize(cards, STARTUP_WIDTH + 48);
            for (JPanel screen : screens) {
                check(!screen.isVisible(), screen.getClass().getSimpleName() + " starts hidden");
                expect(screen.getClass().getSimpleName() + " initial split width",
                        STARTUP_WIDTH, findSplit(screen).getWidth());
            }
            resize(cards, MAXIMIZED_WIDTH + 48);

            for (int i = 0; i < screens.length; i++) {
                JPanel screen = screens[i];
                String name = screen.getClass().getSimpleName();
                boolean sideOnRight = i < 6;
                JSplitPane split = findSplit(screen);
                boolean proportional = screen instanceof DashboardPanel;
                int initialWidth = proportional ? (int) Math.round(availableWidth(split) * 0.45) : widths[i];
                expect(name + " maximized split width", MAXIMIZED_WIDTH, split.getWidth());
                expectSide(name + " hidden before first visit", split, sideOnRight, initialWidth);
                layout.show(cards, Integer.toString(i));
                layoutTree(cards);
                paint(cards);
                layoutTree(cards);
                expectSide(name + " first visit and first paint", split, sideOnRight, initialWidth);

                int adjustedWidth = initialWidth + 37;
                double adjustedShare = adjustedWidth / (double) availableWidth(split);
                moveDivider(split, sideOnRight, adjustedWidth);
                layoutTree(cards);
                expectSide(name + " manually resized", split, sideOnRight, adjustedWidth);
                layout.show(cards, "other");
                resize(cards, SMALL_WIDTH + 48);
                check(!screen.isVisible(), name + " hidden during smaller-window layout");
                expectSide(name + " smaller window while hidden", split, sideOnRight,
                        proportional ? Math.max(320, Math.min((int) Math.round(availableWidth(split) * adjustedShare),
                                availableWidth(split) - 320)) : Math.min(adjustedWidth, availableWidth(split) - 320));
                checkMainMinimum(name, split, sideOnRight);
                resize(cards, MAXIMIZED_WIDTH + 48);
                layout.show(cards, Integer.toString(i));
                layoutTree(cards);
                paint(cards);
                expectSide(name + " manual width restored on return", split, sideOnRight, adjustedWidth);
                layout.show(cards, "other");
            }
        } finally {
            if (previous == null) {
                Session.end();
            } else {
                Session.start(previous);
            }
        }
    }

    private static void resize(JComponent component, int width) {
        component.setSize(width, 900);
        layoutTree(component);
    }

    /** Includes invisible cards, just as the initial hierarchy validation does. */
    private static void layoutTree(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents()) {
            if (child instanceof Container) {
                layoutTree((Container) child);
            }
        }
    }

    private static void paint(JComponent component) {
        BufferedImage image = new BufferedImage(component.getWidth(), component.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            component.paint(graphics);
        } finally {
            graphics.dispose();
            image.flush();
        }
    }

    private static JSplitPane findSplit(Container parent) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JSplitPane) {
                return (JSplitPane) child;
            }
            if (child instanceof Container) {
                JSplitPane found = findSplit((Container) child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static int availableWidth(JSplitPane split) {
        Insets insets = split.getInsets();
        return split.getWidth() - insets.left - insets.right - split.getDividerSize();
    }

    private static void moveDivider(JSplitPane split, boolean sideOnRight, int sideWidth) {
        split.setDividerLocation(split.getInsets().left
                + (sideOnRight ? availableWidth(split) - sideWidth : sideWidth));
    }

    private static void expectSide(String description, JSplitPane split, boolean sideOnRight, int expected) {
        Component side = sideOnRight ? split.getRightComponent() : split.getLeftComponent();
        expect(description, expected, side.getWidth());
    }

    private static void checkMainMinimum(String description, JSplitPane split, boolean sideOnRight) {
        Component main = sideOnRight ? split.getLeftComponent() : split.getRightComponent();
        check(main.getWidth() >= 320, description + ": main content must remain at least 320 pixels wide"
                + ", got " + main.getWidth());
    }

    private static void expect(String description, int expected, int actual) {
        check(expected == actual, description + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
