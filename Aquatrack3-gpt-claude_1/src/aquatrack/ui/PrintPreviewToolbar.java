package aquatrack.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Receipt previews have zoom only; reports also expose paper orientation and page navigation. */
final class PrintPreviewToolbar extends JPanel {
    final JComboBox<String> orientation = new JComboBox<>(new String[] {"Portrait", "Landscape"});
    final JComboBox<String> zoom = new JComboBox<>(new String[] {"Fit page", "Fit width", "50%", "75%", "100%", "125%", "150%"});
    final UiKit.ThemedButton zoomOut = UiKit.button("\u2212", UiKit.SIDEBAR);
    final UiKit.ThemedButton zoomIn = UiKit.button("+", UiKit.SIDEBAR);
    final UiKit.ThemedButton previous = UiKit.button("Previous", UiKit.SIDEBAR);
    final UiKit.ThemedButton next = UiKit.button("Next", UiKit.SIDEBAR);
    final JLabel pageNumber = UiKit.label("Preparing pages...");

    PrintPreviewToolbar(boolean receipt) {
        super(new BorderLayout());
        setBackground(UiKit.SURFACE);
        JPanel settings = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        settings.setOpaque(false);
        UiKit.styleInput(orientation);
        UiKit.styleInput(zoom);
        if (!receipt) {
            settings.add(UiKit.label("A4 paper"));
            settings.add(orientation);
        }
        settings.add(UiKit.label("Zoom"));
        settings.add(zoomOut);
        settings.add(zoom);
        settings.add(zoomIn);
        zoomOut.setToolTipText("Zoom out");
        zoomIn.setToolTipText("Zoom in");
        zoomOut.getAccessibleContext().setAccessibleName("Zoom out");
        zoomIn.getAccessibleContext().setAccessibleName("Zoom in");
        add(settings, BorderLayout.NORTH);
        if (!receipt) {
            JPanel navigation = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
            navigation.setOpaque(false);
            navigation.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
            navigation.add(previous);
            navigation.add(pageNumber);
            navigation.add(next);
            add(navigation, BorderLayout.SOUTH);
        }
    }

    /** Next existing percentage choice, including when the starting mode is a fitted scale. */
    static int zoomStep(double scale, int direction) {
        double[] choices = {.5, .75, 1, 1.25, 1.5};
        if (direction > 0) {
            for (int i = 0; i < choices.length; i++) if (choices[i] > scale + .00001) return i + 2;
        } else {
            for (int i = choices.length - 1; i >= 0; i--) if (choices[i] < scale - .00001) return i + 2;
        }
        return -1;
    }
}
