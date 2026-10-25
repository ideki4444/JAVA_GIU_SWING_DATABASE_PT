package aquatrack.ui;

import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.Icon;

/** Small line icons paired with words; no font glyph or external image dependency. */
final class NavIcon implements Icon {
    private final String kind;
    NavIcon(String kind) { this.kind = kind; }
    public int getIconWidth() { return 20; }
    public int getIconHeight() { return 20; }
    public void paintIcon(Component c, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.translate(x, y);
        g.setColor(c.getForeground());
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (kind) {
            case "dashboard" -> {
                for (int a : new int[] {2, 11}) for (int b : new int[] {2, 11}) g.drawRoundRect(a, b, 6, 6, 2, 2);
            }
            case "orders" -> {
                g.drawRoundRect(3, 2, 13, 16, 2, 2);
                g.drawLine(7, 6, 12, 6); g.drawLine(7, 10, 12, 10); g.drawLine(7, 14, 10, 14);
            }
            case "deliveries" -> {
                g.drawRect(1, 4, 11, 10); g.drawRect(12, 8, 6, 6);
                g.fillOval(3, 13, 4, 4); g.fillOval(13, 13, 4, 4);
            }
            case "customers", "accounts" -> {
                g.drawOval(6, 1, 7, 7); g.drawArc(2, 10, 15, 15, 0, 180);
                g.drawLine(2, 17, 17, 17);
            }
            case "reports" -> {
                g.drawLine(2, 18, 18, 18); g.drawRect(3, 10, 3, 8);
                g.drawRect(8, 6, 3, 12); g.drawRect(13, 2, 3, 16);
            }
            case "containers" -> {
                g.drawRoundRect(5, 6, 11, 13, 4, 4); g.drawRect(8, 1, 5, 5);
                g.drawLine(6, 10, 15, 10); g.drawLine(6, 15, 15, 15);
            }
            default -> {
                g.drawRect(2, 4, 16, 14); g.drawLine(2, 8, 18, 8); g.drawLine(10, 4, 10, 18);
            }
        }
        g.dispose();
    }
}
