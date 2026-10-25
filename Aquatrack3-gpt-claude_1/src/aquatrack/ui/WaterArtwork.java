package aquatrack.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import javax.swing.JComponent;

/** Resolution-independent station illustration, bundled as Java2D shapes. */
public final class WaterArtwork extends JComponent {
    public WaterArtwork() {
        setPreferredSize(new Dimension(500, 330));
        setMinimumSize(new Dimension(200, 150));
    }
    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double scale = Math.min(getWidth() / 500.0, getHeight() / 330.0);
        g.translate((getWidth() - 500 * scale) / 2, (getHeight() - 330 * scale) / 2);
        g.scale(scale, scale);
        g.setColor(new Color(255, 255, 255, 15));
        g.fillOval(85, 0, 340, 320);
        g.setColor(new Color(255, 255, 255, 18));
        g.fillOval(10, 100, 80, 80);
        g.fillOval(409, 25, 46, 46);
        g.setColor(new Color(4, 35, 57, 80));
        g.fillOval(58, 280, 380, 32);
        bottle(g, 100, 65, 0.78, new Color(0xB5EDF2));
        bottle(g, 235, 24, 1.0, new Color(0xDDF8FA));
        g.setColor(new Color(0x86DDE6));
        g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D wave = new Path2D.Double();
        wave.moveTo(40, 313);
        wave.curveTo(150, 285, 260, 340, 450, 301);
        g.draw(wave);
        g.drawImage(UiKit.dropletIcon(55), 56, 32, null);
        g.setColor(new Color(0xB5EDF2));
        g.fillOval(425, 215, 9, 9);
        g.fillOval(451, 185, 5, 5);
        g.dispose();
    }
    private static void bottle(Graphics2D original, int x, int y, double scale, Color body) {
        Graphics2D g = (Graphics2D) original.create();
        g.translate(x, y);
        g.scale(scale, scale);
        g.setColor(new Color(0x1598AE));
        g.fillRoundRect(43, 0, 68, 28, 9, 9);
        g.setColor(new Color(0x8AD6E0));
        g.fillRect(51, 23, 52, 24);
        Path2D shape = new Path2D.Double();
        shape.moveTo(51, 40);
        shape.curveTo(45, 58, 5, 60, 5, 95);
        shape.lineTo(5, 241);
        shape.quadTo(5, 263, 28, 263);
        shape.lineTo(128, 263);
        shape.quadTo(151, 263, 151, 241);
        shape.lineTo(151, 95);
        shape.curveTo(151, 60, 111, 58, 103, 40);
        shape.closePath();
        g.setPaint(new GradientPaint(10, 80, body, 150, 240, new Color(0x7ACFD9)));
        g.fill(shape);
        g.setColor(new Color(255, 255, 255, 155));
        g.fillRoundRect(20, 86, 12, 125, 10, 10);
        g.setColor(new Color(0x53B5C4));
        g.setStroke(new BasicStroke(3));
        g.drawRoundRect(13, 109, 130, 16, 10, 10);
        g.drawRoundRect(13, 214, 130, 16, 10, 10);
        g.setColor(new Color(0xF7FDFE));
        g.fillRoundRect(40, 142, 79, 55, 12, 12);
        g.drawImage(UiKit.dropletIcon(39), 60, 148, null);
        g.dispose();
    }
}
