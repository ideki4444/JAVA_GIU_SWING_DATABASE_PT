package aquatrack.ui;

import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

/** Keeps forms usable on small or scaled displays; the surrounding viewport supplies scrolling. */
final class WorkspacePanel extends JPanel implements Scrollable {
    private static final int MIN_WIDTH = 920;
    private static final int MIN_HEIGHT = 660;

    WorkspacePanel(LayoutManager layout) { super(layout); }

    @Override public Dimension getPreferredSize() { return new Dimension(MIN_WIDTH, MIN_HEIGHT); }
    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public boolean getScrollableTracksViewportWidth() {
        return getParent() != null && getParent().getWidth() >= MIN_WIDTH;
    }
    @Override public boolean getScrollableTracksViewportHeight() {
        return getParent() != null && getParent().getHeight() >= MIN_HEIGHT;
    }
    @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 24; }
    @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return Math.max(24, (orientation == SwingConstants.VERTICAL ? visible.height : visible.width) - 48);
    }
}
