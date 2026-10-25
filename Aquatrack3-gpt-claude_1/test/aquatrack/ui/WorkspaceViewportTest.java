package aquatrack.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/** Verifies reachable actions at desktop resolutions with 100%, 125% and 150% logical scaling. */
public final class WorkspaceViewportTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (int[] display : new int[][] {{1366, 768}, {1920, 1080}}) {
                for (double scale : new double[] {1, 1.25, 1.5}) {
                    CardLayout cards = new CardLayout();
                    WorkspacePanel workspace = new WorkspacePanel(cards);
                    JPanel screen = new JPanel(new BorderLayout());
                    JButton action = new JButton("Save and preview receipt");
                    JPanel bottom = new JPanel(new BorderLayout());
                    bottom.add(action, BorderLayout.EAST);
                    screen.add(bottom, BorderLayout.SOUTH);
                    workspace.add(screen, "form");
                    workspace.add(new JPanel(), "other");
                    JScrollPane scroll = new JScrollPane(workspace);
                    scroll.setBorder(BorderFactory.createEmptyBorder());
                    int width = (int) (display[0] / scale) - 252;
                    int height = (int) (display[1] / scale) - 48;
                    scroll.setSize(new Dimension(width, height));
                    layout(scroll);
                    check(workspace.getWidth() >= 920 && workspace.getHeight() >= 660, "forms keep usable space");
                    check(scroll.getHorizontalScrollBar().isVisible() == (width < 920), "horizontal scrolling matches space");
                    check(scroll.getVerticalScrollBar().isVisible() == (height < 660), "vertical scrolling matches space");
                    Rectangle button = SwingUtilities.convertRectangle(action.getParent(), action.getBounds(), workspace);
                    workspace.scrollRectToVisible(button);
                    check(workspace.getVisibleRect().contains(button), "bottom action reachable by scrolling");
                    cards.show(workspace, "other");
                    layout(scroll);
                    cards.show(workspace, "form");
                    layout(scroll);
                    check(screen.getWidth() == workspace.getWidth() && screen.getHeight() == workspace.getHeight(),
                            "card changes retain the full scrollable form");
                    if (width >= 920 && height >= 660) {
                        check(workspace.getWidth() == scroll.getViewport().getWidth()
                                && workspace.getHeight() == scroll.getViewport().getHeight(), "large windows use available space");
                    }
                }
            }
        });
        System.out.println("WorkspaceViewportTest passed: " + checks + " checks.");
    }
    private static void layout(java.awt.Container parent) {
        for (int i = 0; i < 3; i++) walk(parent);
    }
    private static void walk(java.awt.Container parent) {
        parent.doLayout();
        for (java.awt.Component child : parent.getComponents()) {
            if (child instanceof java.awt.Container nested) walk(nested);
        }
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
