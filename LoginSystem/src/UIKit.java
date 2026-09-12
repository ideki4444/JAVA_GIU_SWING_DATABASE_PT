// ============================================================================
// UIKit.java  —  "TWILIGHT PEAKS" DESIGN SYSTEM  (updated)
// ----------------------------------------------------------------------------
// NEW: 
//   • EyeIconButton + PasswordBox  -> show/hide password toggle for login
//   • ThemedDialogs                -> frosted-glass replacements for the
//                                     plain JOptionPane popups (message +
//                                     confirm variants), matching the theme
// ============================================================================
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Random;

public final class UIKit {

    /* ---------- Palette sampled from the wallpaper ---------- */
    public static final Color SKY_TOP    = new Color(0x262046);              // deep indigo
    public static final Color SKY_MID    = new Color(0x4A3F76);              // violet
    public static final Color MIST       = new Color(0x9C8FC7);              // lavender mist
    public static final Color GLOW_PINK  = new Color(0xE79FB4);              // alpenglow pink (accent)
    public static final Color GLOW_PEACH = new Color(0xF2B8A2);              // horizon peach
    public static final Color MOON       = new Color(0xF7F3EC);              // moon cream
    public static final Color TEXT       = new Color(0xEDE9F7);              // primary text
    public static final Color TEXT_DIM   = new Color(0xB9B0D6);              // secondary text
    public static final Color CARD_BG    = new Color(30, 25, 55, 185);       // frosted glass card
    public static final Color FIELD_BG   = new Color(255, 255, 255, 38);     // frosted input
    public static final Color LINE       = new Color(255, 255, 255, 70);     // hairline border
    public static final Color INK_ON_ACCENT = new Color(0x33254A);           // text on pink button
    public static final Color DANGER     = new Color(0xE2726E);              // soft error red

    private static final String REMOTE_FALLBACK =
            "file:///C:/Users/idefkicabti/Pictures/Saved%20Pictures/javapt2.jpg";
    private static BufferedImage bg;

    private UIKit() {}

    public static Font font(float size, int style) {
        return new Font("Segoe UI", style, Math.round(size));
    }

    /* ---------- Wallpaper loader: local file -> classpath -> remote ---------- */
    public static Image background() {
        if (bg != null) return bg;
        String[] paths = {"assets/background.png", "assets/background.jpg", "background.png"};
        for (String p : paths) {
            try {
                File f = new File(p);
                if (f.exists()) { bg = ImageIO.read(f); return bg; }
            } catch (Exception ignored) {}
        }
        try (InputStream in = UIKit.class.getResourceAsStream("/assets/background.png")) {
            if (in != null) { bg = ImageIO.read(in); return bg; }
        } catch (Exception ignored) {}
        try { bg = ImageIO.read(new URL(REMOTE_FALLBACK)); } catch (Exception ignored) {}
        return bg;
    }

    public static void antialias(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    public static void drawCrescent(Graphics2D g2, int x, int y, int r) {
        Area moon = new Area(new Ellipse2D.Double(x - r, y - r, 2.0 * r, 2.0 * r));
        moon.subtract(new Area(new Ellipse2D.Double(x - r - r * 0.55, y - r - r * 0.2, r * 1.9, r * 1.9)));
        g2.setPaint(MOON);
        g2.fill(moon);
    }

    /* ---------- Painted fallback scene (used only if no image file) ---------- */
    public static void paintFallbackScene(Graphics2D g2, int w, int h) {
        g2.setPaint(new GradientPaint(0, 0, SKY_TOP, 0, h, SKY_MID));
        g2.fillRect(0, 0, w, h);
        g2.setPaint(new RadialGradientPaint(w * 0.9f, h * 0.52f, w * 0.55f,
                new float[]{0, 1}, new Color[]{new Color(242, 184, 162, 140), new Color(242, 184, 162, 0)}));
        g2.fillRect(0, 0, w, h);
        Random rnd = new Random(7);
        for (int i = 0; i < 90; i++) {
            int x = rnd.nextInt(w), y = rnd.nextInt(Math.max(1, h / 2)), s = 1 + rnd.nextInt(2);
            g2.setPaint(new Color(237, 233, 247, 60 + rnd.nextInt(140)));
            g2.fillRect(x, y, s, s);
        }
        drawCrescent(g2, (int) (w * 0.58), (int) (h * 0.24), 12);
        int px = w / 2, py = (int) (h * 0.32), baseY = (int) (h * 0.64);
        Polygon peak = new Polygon();
        peak.addPoint(px - (int) (w * 0.22), baseY); peak.addPoint(px, py); peak.addPoint(px + (int) (w * 0.24), baseY);
        g2.setPaint(new Color(0x8B7DB8)); g2.fill(peak);
        Polygon cap = new Polygon();
        cap.addPoint(px, py); cap.addPoint(px - 34, py + 46); cap.addPoint(px - 14, py + 34);
        cap.addPoint(px + 2, py + 50); cap.addPoint(px + 20, py + 36); cap.addPoint(px + 36, py + 46);
        g2.setPaint(new Color(0xEFE6F5)); g2.fill(cap);
        g2.setPaint(new GradientPaint(0, baseY - 60, new Color(237, 233, 247, 0),
                0, baseY + 40, new Color(237, 233, 247, 150)));
        g2.fillRect(0, baseY - 60, w, h - baseY + 60);
        Polygon leftHill = new Polygon();
        leftHill.addPoint(0, (int) (h * 0.6)); leftHill.addPoint((int) (w * 0.6), h); leftHill.addPoint(0, h);
        Polygon rightHill = new Polygon();
        rightHill.addPoint(w, (int) (h * 0.58)); rightHill.addPoint((int) (w * 0.4), h); rightHill.addPoint(w, h);
        g2.setPaint(new Color(0x4E4379)); g2.fill(leftHill); g2.fill(rightHill);
        g2.setPaint(new Color(0x3A3260));
        int[] tx = {(int) (w * 0.06), (int) (w * 0.12), (int) (w * 0.86), (int) (w * 0.93)};
        int[] ty = {(int) (h * 0.66), (int) (h * 0.7), (int) (h * 0.68), (int) (h * 0.64)};
        for (int i = 0; i < tx.length; i++) {
            Polygon t = new Polygon();
            t.addPoint(tx[i] - 14, ty[i] + 46); t.addPoint(tx[i], ty[i]); t.addPoint(tx[i] + 14, ty[i] + 46);
            g2.fill(t);
        }
    }
}

/* ================= SCENE BACKDROP (image cover-fit + readability veil) ===== */
class ScenePanel extends JPanel {
    ScenePanel() { setOpaque(false); }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        UIKit.antialias(g2);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) { g2.dispose(); return; }
        Image img = UIKit.background();
        if (img != null && img.getWidth(null) > 0) {
            double s = Math.max((double) w / img.getWidth(null), (double) h / img.getHeight(null));
            int iw = (int) (img.getWidth(null) * s), ih = (int) (img.getHeight(null) * s);
            g2.drawImage(img, (w - iw) / 2, (h - ih) / 2, iw, ih, this);
        } else {
            UIKit.paintFallbackScene(g2, w, h);
        }
        g2.setPaint(new GradientPaint(0, 0, new Color(20, 16, 40, 110), 0, h, new Color(20, 16, 40, 30)));
        g2.fillRect(0, 0, w, h);
        g2.dispose();
    }
}

/* ================= FROSTED GLASS CARD ====================================== */
class GlassPanel extends JPanel {
    private final int arc;
    GlassPanel(LayoutManager lm, int arc) {
        super(lm);
        this.arc = arc;
        setOpaque(false);
    }
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        UIKit.antialias(g2);
        g2.setPaint(UIKit.CARD_BG);
        g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc));
        g2.setPaint(UIKit.LINE);
        g2.setStroke(new BasicStroke(1f));
        g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, arc, arc));
        g2.dispose();
        super.paintComponent(g);
    }
}

/* ================= ROUNDED INPUTS WITH PLACEHOLDERS ======================== */
class RoundedTextField extends JTextField {
    private final String placeholder;
    RoundedTextField(String placeholder) {
        this.placeholder = placeholder;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(12, 18, 12, 18));
        setFont(UIKit.font(14f, Font.PLAIN));
        setForeground(UIKit.TEXT);
        setCaretColor(UIKit.MOON);
        setSelectionColor(UIKit.GLOW_PINK);
        setSelectedTextColor(UIKit.INK_ON_ACCENT);
        addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) { repaint(); }
            public void focusLost(FocusEvent e) { repaint(); }
        });
    }
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        UIKit.antialias(g2);
        g2.setPaint(UIKit.FIELD_BG);
        g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 26, 26));
        boolean focused = isFocusOwner();
        g2.setPaint(focused ? UIKit.GLOW_PINK : UIKit.LINE);
        g2.setStroke(new BasicStroke(focused ? 1.6f : 1f));
        g2.draw(new RoundRectangle2D.Float(0.8f, 0.8f, getWidth() - 1.6f, getHeight() - 1.6f, 26, 26));
        if (getText().isEmpty()) {
            g2.setFont(getFont());
            g2.setPaint(UIKit.TEXT_DIM);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(placeholder, 18, getHeight() / 2 + fm.getAscent() / 2 - 2);
        }
        g2.dispose();
        super.paintComponent(g);
    }
}

class RoundedPasswordField extends JPasswordField {
    private final String placeholder;
    RoundedPasswordField(String placeholder) {
        this.placeholder = placeholder;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(12, 18, 12, 18));
        setFont(UIKit.font(14f, Font.PLAIN));
        setForeground(UIKit.TEXT);
        setCaretColor(UIKit.MOON);
        setSelectionColor(UIKit.GLOW_PINK);
        setSelectedTextColor(UIKit.INK_ON_ACCENT);
        addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) { repaint(); }
            public void focusLost(FocusEvent e) { repaint(); }
        });
    }
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        UIKit.antialias(g2);
        g2.setPaint(UIKit.FIELD_BG);
        g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 26, 26));
        boolean focused = isFocusOwner();
        g2.setPaint(focused ? UIKit.GLOW_PINK : UIKit.LINE);
        g2.setStroke(new BasicStroke(focused ? 1.6f : 1f));
        g2.draw(new RoundRectangle2D.Float(0.8f, 0.8f, getWidth() - 1.6f, getHeight() - 1.6f, 26, 26));
        if (getPassword().length == 0) {
            g2.setFont(getFont());
            g2.setPaint(UIKit.TEXT_DIM);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(placeholder, 18, getHeight() / 2 + fm.getAscent() / 2 - 2);
        }
        g2.dispose();
        super.paintComponent(g);
    }
}

/* ================= EYE TOGGLE (show / hide password) ======================= */
class EyeIconButton extends JToggleButton {
    private boolean hover;

    EyeIconButton() {
        setPreferredSize(new Dimension(34, 34));
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setToolTipText("Show / hide password");
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
            public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        UIKit.antialias(g2);
        double w = getWidth(), h = getHeight();
        double cx = w / 2.0, cy = h / 2.0;

        /* soft hover / active chip */
        if (hover || isSelected()) {
            g2.setPaint(new Color(255, 255, 255, isSelected() ? 55 : 38));
            g2.fill(new RoundRectangle2D.Double(2, 2, w - 4, h - 4, 12, 12));
        }

        /* eye outline */
        Color icon = isSelected() ? UIKit.GLOW_PINK : UIKit.TEXT_DIM;
        g2.setPaint(icon);
        g2.setStroke(new BasicStroke(1.7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double lid = h * 0.34;
        GeneralPath eye = new GeneralPath();
        eye.moveTo(cx - w * 0.30, cy);
        eye.curveTo(cx - w * 0.13, cy - lid, cx + w * 0.13, cy - lid, cx + w * 0.30, cy);
        eye.curveTo(cx + w * 0.13, cy + lid, cx - w * 0.13, cy + lid, cx - w * 0.30, cy);
        eye.closePath();
        g2.draw(eye);

        /* pupil */
        double pr = h * 0.105;
        g2.fill(new Ellipse2D.Double(cx - pr, cy - pr, pr * 2, pr * 2));

        /* slash when the password is currently visible */
        if (isSelected()) {
            g2.setStroke(new BasicStroke(1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new java.awt.geom.Line2D.Double(cx - w * 0.27, cy + h * 0.27, cx + w * 0.27, cy - h * 0.27));
        }
        g2.dispose();
        super.paintComponent(g);
    }
}

/* ================= PASSWORD FIELD WITH BUILT-IN EYE TOGGLE ================= */
class PasswordBox extends JPanel {
    private final String placeholder;
    private final JPasswordField editor = new JPasswordField();
    private final EyeIconButton peek = new EyeIconButton();
    private final char defaultEcho;

    PasswordBox(String placeholder) {
        this.placeholder = placeholder;
        setOpaque(false);
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(0, 46));
        defaultEcho = editor.getEchoChar();

        editor.setOpaque(false);
        editor.setBackground(new Color(0, 0, 0, 0));
        editor.setBorder(BorderFactory.createEmptyBorder(12, 18, 12, 2));
        editor.setFont(UIKit.font(14f, Font.PLAIN));
        editor.setForeground(UIKit.TEXT);
        editor.setCaretColor(UIKit.MOON);
        editor.setSelectionColor(UIKit.GLOW_PINK);
        editor.setSelectedTextColor(UIKit.INK_ON_ACCENT);
        add(editor, BorderLayout.CENTER);

        JPanel east = new JPanel(new GridBagLayout());
        east.setOpaque(false);
        east.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 9));
        east.add(peek);
        add(east, BorderLayout.EAST);

        peek.addActionListener(e -> {
            boolean show = peek.isSelected();
            editor.setEchoChar(show ? (char) 0 : defaultEcho);
            editor.repaint();
        });
        editor.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) { repaint(); }
            public void focusLost(FocusEvent e) { repaint(); }
        });
    }

    char[] getPassword() { return editor.getPassword(); }
    void setText(String text) { editor.setText(text); }
    void addActionListener(ActionListener l) { editor.addActionListener(l); }
    JPasswordField getEditor() { return editor; }
    @Override
    public boolean requestFocusInWindow() { return editor.requestFocusInWindow(); }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        UIKit.antialias(g2);
        g2.setPaint(UIKit.FIELD_BG);
        g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 26, 26));
        boolean focused = editor.isFocusOwner();
        g2.setPaint(focused ? UIKit.GLOW_PINK : UIKit.LINE);
        g2.setStroke(new BasicStroke(focused ? 1.6f : 1f));
        g2.draw(new RoundRectangle2D.Float(0.8f, 0.8f, getWidth() - 1.6f, getHeight() - 1.6f, 26, 26));
        if (editor.getPassword().length == 0) {
            g2.setFont(editor.getFont());
            g2.setPaint(UIKit.TEXT_DIM);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(placeholder, 18, getHeight() / 2 + fm.getAscent() / 2 - 2);
        }
        g2.dispose();
        super.paintComponent(g);
    }
}

/* ================= PRIMARY PILL BUTTON (pink -> peach gradient) ============ */
class RoundedButton extends JButton {
    private boolean hover, pressed;
    RoundedButton(String text) {
        super(text);
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setBorder(BorderFactory.createEmptyBorder(12, 24, 12, 24));
        setFont(UIKit.font(14f, Font.BOLD));
        setForeground(UIKit.INK_ON_ACCENT);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
            public void mouseExited(MouseEvent e)  { hover = false; pressed = false; repaint(); }
            public void mousePressed(MouseEvent e) { pressed = true; repaint(); }
            public void mouseReleased(MouseEvent e){ pressed = false; repaint(); }
        });
    }
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        UIKit.antialias(g2);
        int y = pressed ? 1 : 0;
        g2.setPaint(new GradientPaint(0, 0, UIKit.GLOW_PINK, getWidth(), 0, UIKit.GLOW_PEACH));
        g2.fill(new RoundRectangle2D.Float(0, y, getWidth(), getHeight(), 26, 26));
        if (hover) {
            g2.setPaint(new Color(255, 255, 255, 35));
            g2.fill(new RoundRectangle2D.Float(0, y, getWidth(), getHeight(), 26, 26));
        }
        g2.dispose();
        super.paintComponent(g);
    }
}

/* ================= GHOST / OUTLINED PILL BUTTON ============================ */
class GhostButton extends JButton {
    private boolean hover;
    GhostButton(String text) {
        super(text);
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 18));
        setFont(UIKit.font(13f, Font.PLAIN));
        setForeground(UIKit.TEXT);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
            public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
        });
    }
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        UIKit.antialias(g2);
        g2.setPaint(new Color(255, 255, 255, hover ? 45 : 22));
        g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 24, 24));
        g2.setPaint(UIKit.LINE);
        g2.setStroke(new BasicStroke(1f));
        g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 24, 24));
        g2.dispose();
        super.paintComponent(g);
    }
}

/* ================= SIDEBAR NAV PILL (selected = frosted + pink bar) ======== */
class NavButton extends JButton {
    private boolean selected, hover;
    NavButton(String text) {
        super(text);
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setBorder(BorderFactory.createEmptyBorder(12, 34, 12, 16));
        setFont(UIKit.font(14f, Font.PLAIN));
        setForeground(UIKit.TEXT);
        setHorizontalAlignment(SwingConstants.LEFT);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
            public void mouseExited(MouseEvent e)  { hover = false; repaint(); }
        });
    }
    public void setSelected (boolean s) { selected = s; repaint(); }
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        UIKit.antialias(g2);
        if (selected) {
            g2.setPaint(new Color(255, 255, 255, 55));
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 22, 22));
            g2.setPaint(UIKit.GLOW_PINK);
            g2.fill(new RoundRectangle2D.Float(0, (getHeight() - 24) / 2f, 4, 24, 4, 4));
        } else if (hover) {
            g2.setPaint(new Color(255, 255, 255, 26));
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 22, 22));
        }
        g2.setPaint(selected ? UIKit.GLOW_PINK : UIKit.TEXT_DIM);
        g2.fillOval(16, getHeight() / 2 - 3, 6, 6);
        g2.dispose();
        super.paintComponent(g);
    }
}

/* ================= CRESCENT MOON BRAND MARK ================================ */
class MoonLabel extends JComponent {
    MoonLabel() { setPreferredSize(new Dimension(40, 40)); }
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        UIKit.antialias(g2);
        UIKit.drawCrescent(g2, getWidth() / 2 + 2, getHeight() / 2, 13);
        g2.dispose();
    }
}

/* ================= THEMED DIALOG SYSTEM (replaces JOptionPane) =============
 * ThemedDialogs.showMessage(...)  -> drop-in for JOptionPane.showMessageDialog
 * ThemedDialogs.showConfirm(...)  -> drop-in for JOptionPane.showConfirmDialog
 * Accepts the usual JOptionPane.*_MESSAGE constants so call sites stay clean.
 * ========================================================================== */
final class ThemedDialogs {

    private ThemedDialogs() {}

    static void showMessage(Component owner, String message, String title, int kind) {
        JDialog d = shell(owner);
        d.getContentPane().add(build(d, title, message, kind, false, null));
        d.pack();
        d.setLocationRelativeTo(SwingUtilities.getWindowAncestor(owner));
        d.setVisible(true);
    }

    /** Returns JOptionPane.YES_OPTION or JOptionPane.NO_OPTION. */
    static int showConfirm(Component owner, String message, String title) {
        JDialog d = shell(owner);
        int[] result = { JOptionPane.NO_OPTION };
        d.getContentPane().add(build(d, title, message, JOptionPane.QUESTION_MESSAGE, true, result));
        d.pack();
        d.setLocationRelativeTo(SwingUtilities.getWindowAncestor(owner));
        d.setVisible(true);
        return result[0];
    }

    private static JDialog shell(Component owner) {
        Window w = owner == null ? null : SwingUtilities.getWindowAncestor(owner);
        JDialog d;
        if (w instanceof Frame)       d = new JDialog((Frame) w, true);
        else if (w instanceof Dialog) d = new JDialog((Dialog) w, true);
        else                          d = new JDialog((Frame) null, true);
        d.setUndecorated(true);
        d.setResizable(false);
        d.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        try {
            GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            if (gd.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT)) {
                d.setBackground(new Color(0, 0, 0, 0));   // rounded corners float free
            } else {
                d.setBackground(UIKit.SKY_TOP);
            }
        } catch (Exception ignored) { d.setBackground(UIKit.SKY_TOP); }
        return d;
    }

    private static JComponent build(JDialog dialog, String title, String message,
                                    int kind, boolean confirm, int[] result) {
        DialogRoot root = new DialogRoot(new GridBagLayout());
        root.setBorder(BorderFactory.createEmptyBorder(22, 22, 22, 22));

        GlassPanel card = new GlassPanel(new GridBagLayout(), 26);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.weightx = 1;
        c.fill = GridBagConstraints.NONE; c.anchor = GridBagConstraints.CENTER;

        c.gridy = 0; c.insets = new Insets(26, 34, 0, 34);
        card.add(new IconDisc(kind), c);

        JLabel titleLabel = new JLabel(title, SwingConstants.CENTER);
        titleLabel.setFont(UIKit.font(18f, Font.BOLD));
        titleLabel.setForeground(UIKit.TEXT);
        c.gridy = 1; c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(14, 34, 0, 34);
        card.add(titleLabel, c);

        JLabel messageLabel = new JLabel(
                "<html><div style='width:250px; text-align:center;'>" + esc(message) + "</div></html>",
                SwingConstants.CENTER);
        messageLabel.setFont(UIKit.font(12.5f, Font.PLAIN));
        messageLabel.setForeground(UIKit.TEXT_DIM);
        c.gridy = 2; c.insets = new Insets(8, 34, 0, 34);
        card.add(messageLabel, c);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        buttons.setOpaque(false);

        if (confirm) {
            RoundedButton yes = new RoundedButton("Yes");
            GhostButton no = new GhostButton("No");
            yes.addActionListener(e -> { result[0] = JOptionPane.YES_OPTION; dialog.dispose(); });
            no.addActionListener(e -> { result[0] = JOptionPane.NO_OPTION; dialog.dispose(); });
            buttons.add(yes);
            buttons.add(no);
            bindEscape(root, () -> { result[0] = JOptionPane.NO_OPTION; dialog.dispose(); });
        } else {
            RoundedButton ok = new RoundedButton("OK");
            ok.addActionListener(e -> dialog.dispose());
            buttons.add(ok);
            bindEscape(root, dialog::dispose);
        }
        c.gridy = 3; c.insets = new Insets(22, 34, 26, 34);
        card.add(buttons, c);

        root.add(card);
        return root;
    }

    private static void bindEscape(JComponent comp, Runnable action) {
        comp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
            .put(KeyStroke.getKeyStroke("ESCAPE"), "twilight-escape");
        comp.getActionMap().put("twilight-escape", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { action.run(); }
        });
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}

/* ---- rounded wallpaper window body for themed dialogs ---- */
class DialogRoot extends JPanel {
    DialogRoot(LayoutManager lm) { super(lm); setOpaque(false); }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        UIKit.antialias(g2);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) { g2.dispose(); return; }
        Shape round = new RoundRectangle2D.Float(0, 0, w, h, 36, 36);
        Shape oldClip = g2.getClip();
        g2.clip(round);
        Image img = UIKit.background();
        if (img != null && img.getWidth(null) > 0) {
            double s = Math.max((double) w / img.getWidth(null), (double) h / img.getHeight(null));
            int iw = (int) (img.getWidth(null) * s), ih = (int) (img.getHeight(null) * s);
            g2.drawImage(img, (w - iw) / 2, (h - ih) / 2, iw, ih, null);
        } else {
            UIKit.paintFallbackScene(g2, w, h);
        }
        g2.setClip(oldClip);
        g2.setPaint(new Color(20, 16, 40, 120));
        g2.fill(round);
        g2.setPaint(UIKit.LINE);
        g2.setStroke(new BasicStroke(1f));
        g2.draw(round);
        g2.dispose();
        super.paintComponent(g);
    }
}

/* ---- tinted icon disc: error = "!", info = crescent, question = "?" ---- */
class IconDisc extends JComponent {
    private final int kind;

    IconDisc(int kind) {
        this.kind = kind;
        setPreferredSize(new Dimension(58, 58));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        UIKit.antialias(g2);
        int w = getWidth(), h = getHeight();

        if (kind == JOptionPane.ERROR_MESSAGE) {
            g2.setPaint(UIKit.DANGER);
            g2.fillOval(0, 0, w, h);
            g2.setPaint(new Color(255, 255, 255, 90));
            g2.setStroke(new BasicStroke(1.4f));
            g2.drawOval(1, 1, w - 3, h - 3);
            g2.setPaint(UIKit.MOON);
            g2.setFont(UIKit.font(26f, Font.BOLD));
            FontMetrics fm = g2.getFontMetrics();
            String mark = "!";
            g2.drawString(mark, (w - fm.stringWidth(mark)) / 2, h / 2 + fm.getAscent() / 2 - 3);
        } else if (kind == JOptionPane.QUESTION_MESSAGE) {
            g2.setPaint(UIKit.MIST);
            g2.fillOval(0, 0, w, h);
            g2.setPaint(new Color(255, 255, 255, 90));
            g2.setStroke(new BasicStroke(1.4f));
            g2.drawOval(1, 1, w - 3, h - 3);
            g2.setPaint(UIKit.INK_ON_ACCENT);
            g2.setFont(UIKit.font(24f, Font.BOLD));
            FontMetrics fm = g2.getFontMetrics();
            String mark = "?";
            g2.drawString(mark, (w - fm.stringWidth(mark)) / 2, h / 2 + fm.getAscent() / 2 - 3);
        } else {
            g2.setPaint(new GradientPaint(0, 0, UIKit.GLOW_PINK, w, h, UIKit.GLOW_PEACH));
            g2.fillOval(0, 0, w, h);
            g2.setPaint(new Color(255, 255, 255, 90));
            g2.setStroke(new BasicStroke(1.4f));
            g2.drawOval(1, 1, w - 3, h - 3);
            UIKit.drawCrescent(g2, w / 2 + 2, h / 2, 12);
        }
        g2.dispose();
        super.paintComponent(g);
    }
}