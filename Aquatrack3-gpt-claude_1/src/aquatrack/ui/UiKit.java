package aquatrack.ui;

import java.awt.BorderLayout;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GradientPaint;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JViewport;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableModel;

/** Theme colors, fonts, and small builders shared by every screen: the aqua-and-white look. */
public final class UiKit {

    public static final Color SIDEBAR = new Color(0x0E4D64);
    public static final Color SIDEBAR_HOVER = new Color(0x176784);
    public static final Color ACCENT = new Color(0x087E9B);
    public static final Color BACKGROUND = new Color(0xF0F7FA);
    public static final Color SURFACE = Color.WHITE;
    public static final Color BORDER = new Color(0xC9E4EC);
    public static final Color TEXT = new Color(0x1E2D3B);
    public static final Color MUTED = new Color(0x5B7083);
    public static final Color DANGER = new Color(0xC9423A);
    public static final Color SELECTION = new Color(0xCDEFF7);
    public static final Color DISABLED = new Color(0xB8C4CC);
    public static final Color WARNING = new Color(0xFFF1C2);
    public static final Color IDLE_BG = new Color(0xE7EDF1);
    public static final Color GOOD = new Color(0x0F6B3A);
    public static final Color GOOD_BG = new Color(0xDDF3E4);
    public static final Color HOLD = new Color(0x7A5A00);
    public static final Color MOVING = new Color(0x0B5C8A);
    public static final Color MOVING_BG = new Color(0xD6EEF9);
    public static final Color SIDEBAR_HEADING = new Color(0x9ED8E6);

    public static final Font BODY = new Font("Segoe UI", Font.PLAIN, 14);
    public static final Font BOLD = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font TITLE = new Font("Segoe UI", Font.BOLD, 26);
    public static final Font SECTION = new Font("Segoe UI", Font.BOLD, 16);
    public static final Font HEADING = new Font("Segoe UI", Font.BOLD, 11);
    public static final Font KPI = new Font("Segoe UI", Font.BOLD, 26);

    private UiKit() {
    }

    /** A radio button styled like the other inputs. */
    public static javax.swing.JRadioButton radio(String text, boolean selected) {
        javax.swing.JRadioButton radio = new javax.swing.JRadioButton(text, selected);
        radio.setFont(BODY);
        radio.setForeground(TEXT);
        radio.setOpaque(false);
        return radio;
    }

    // ----- text -----

    public static JLabel label(String text) {
        return styled(text, BODY, TEXT);
    }

    public static JLabel muted(String text) {
        return styled(text, BODY, MUTED);
    }

    public static JLabel sectionTitle(String text) {
        return styled(text, SECTION, SIDEBAR);
    }

    public static JLabel titleLabel(String text) {
        return styled(text, TITLE, SIDEBAR);
    }

    public static JLabel light(String text, Font font) {
        return styled(text, font, Color.WHITE);
    }

    private static JLabel styled(String text, Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    /** Page title with a short description underneath. */
    public static JComponent header(String title, String subtitle) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        panel.add(titleLabel(title), BorderLayout.NORTH);
        panel.add(muted("<html>" + escapeHtml(subtitle) + "</html>"), BorderLayout.CENTER);
        return panel;
    }

    // ----- panels and inputs -----

    public static JPanel card() {
        return card(new BorderLayout());
    }

    /** A white card with a thin aqua border and inner padding. */
    public static JPanel card(LayoutManager layout) {
        JPanel panel = new JPanel(layout) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D copy = (Graphics2D) g.create();
                copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                copy.setColor(new Color(0xDFEAF0));
                copy.fillRoundRect(0, 3, getWidth(), getHeight() - 3, 20, 20);
                copy.setColor(SURFACE);
                copy.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 4, 20, 20);
                copy.setColor(BORDER);
                copy.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 4, 20, 20);
                copy.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setBackground(SURFACE);
        panel.setBorder(BorderFactory.createEmptyBorder(16, 18, 18, 18));
        return panel;
    }

    public static JTextField field(int columns) {
        JTextField field = new JTextField(columns);
        styleInput(field);
        return field;
    }

    /** Gives text fields, password fields, and combo boxes the same font and border. */
    public static void styleInput(JComponent input) {
        input.setFont(BODY);
        input.setForeground(TEXT);
        input.setBackground(SURFACE);
        input.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), BorderFactory.createEmptyBorder(5, 7, 5, 7)));
        input.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) {
                input.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(ACCENT, 2),
                        BorderFactory.createEmptyBorder(4, 6, 4, 6)));
            }
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                input.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),
                        BorderFactory.createEmptyBorder(5, 7, 5, 7)));
            }
        });
    }

    /** Stretches a component across a BoxLayout column. */
    public static <T extends JComponent> T fullWidth(T component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
        return component;
    }

    /** Adds "Label    [input]" on one row of a panel that uses GridBagLayout. */
    public static void formRow(JPanel form, int row, String text, JComponent input) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(5, 0, 5, 12);
        JLabel label = label(text);
        label.setLabelFor(input);
        input.getAccessibleContext().setAccessibleName(text);
        form.add(label, c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(5, 0, 5, 0);
        form.add(input, c);
    }

    // ----- tables -----

    public static JScrollPane scroll(Component view) {
        JScrollPane pane = new JScrollPane(view);
        if (view instanceof JTable table) pane.setColumnHeaderView(table.getTableHeader());
        pane.setBorder(BorderFactory.createLineBorder(BORDER));
        pane.getViewport().setBackground(SURFACE);
        return pane;
    }

    /** Common table look: aqua selection, teal header, comfortable row height. */
    public static void styleTable(JTable table) {
        table.setFont(BODY);
        table.setRowHeight(36);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        DefaultTableCellRenderer centered = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                                       boolean focused, int row, int column) {
                super.getTableCellRendererComponent(t, value, selected, focused, row, column);
                setHorizontalAlignment(SwingConstants.CENTER);
                setBackground(selected ? SELECTION : row % 2 == 0 ? SURFACE : new Color(0xF6FAFC));
                setForeground(TEXT);
                return this;
            }
        };
        table.setDefaultRenderer(Object.class, centered);
        table.setDefaultRenderer(Number.class, centered);
        table.setDefaultRenderer(java.util.Date.class, centered);
        table.setGridColor(BORDER);
        table.setSelectionBackground(SELECTION);
        table.setSelectionForeground(TEXT);
        table.setFillsViewportHeight(true);
        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                           boolean focused, int row, int column) {
                super.getTableCellRendererComponent(t, value, selected, focused, row, column);
                setBackground(SIDEBAR);
                setHorizontalAlignment(SwingConstants.CENTER);
                setForeground(Color.WHITE);
                setFont(BOLD);
                setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
                setOpaque(true);
                return this;
            }
        });
    }

    /** Compatibility for older screens: every table column now uses centered alignment. */
    public static void alignRight(JTable table, int... columns) {
        alignCenter(table, columns);
    }

    public static void alignCenter(JTable table, int... columns) {
        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(SwingConstants.CENTER);
        for (int column : columns) {
            table.getColumnModel().getColumn(column).setCellRenderer(right);
        }
    }

    // ----- buttons -----

    public static ThemedButton button(String text, Color fill) {
        return new ThemedButton(text, fill);
    }

    /** Rounded, colored button, drawn by hand so every Look and Feel shows the same color. */
    public static final class ThemedButton extends JButton {

        private final Color fill;

        public ThemedButton(String text, Color fill) {
            super(text);
            this.fill = fill;
            setFont(BOLD);
            setForeground(Color.WHITE);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(9, 18, 9, 18));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color color = fill;
            if (!isEnabled()) {
                color = DISABLED;
            } else if (getModel().isPressed()) {
                color = fill.darker();
            } else if (getModel().isRollover()) {
                color = fill.brighter();
            }
            g2.setColor(color);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
            if (hasFocus()) {
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2));
                g2.drawRoundRect(3, 3, getWidth() - 7, getHeight() - 7, 10, 10);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Sidebar item that stays highlighted while its screen is open. */
    public static final class NavButton extends JToggleButton {

        public NavButton(String text) {
            super(text);
            setFont(BOLD);
            setForeground(Color.WHITE);
            setHorizontalAlignment(SwingConstants.LEFT);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(10, 22, 10, 12));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            if (isSelected()) {
                g2.setColor(ACCENT);
            } else if (getModel().isRollover()) {
                g2.setColor(SIDEBAR_HOVER);
            } else {
                g2.setColor(SIDEBAR);
            }
            g2.fillRect(0, 0, getWidth(), getHeight());
            if (isSelected()) {
                g2.setColor(new Color(0x8DE0E9));
                g2.fillRoundRect(5, 8, 4, getHeight() - 16, 4, 4);
            }
            if (hasFocus()) {
                g2.setColor(Color.WHITE);
                g2.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, 8, 8);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /**
     * Wraps a note so long text breaks onto more lines instead of being cut off at the card's edge.
     * A table is used because Swing ignores a width style on the body element.
     */
    public static String wrapped(String text) {
        return wrapped(text, 330);
    }

    /** Wraps text at the given width in pixels. */
    public static String wrapped(String text, int width) {
        return "<html><table width='" + width + "' cellspacing='0' cellpadding='0'><tr><td>" + text
                + "</td></tr></table></html>";
    }

    // ----- tables that fit any window -----

    /**
     * A table that stretches to fill a wide window, but scrolls sideways instead of squeezing its text
     * when the window is narrow.
     */
    public static JTable table(TableModel model) {
        return new JTable(model) {
            @Override public Component prepareRenderer(javax.swing.table.TableCellRenderer renderer,
                                                        int row, int column) {
                Component component = super.prepareRenderer(renderer, row, column);
                if (component instanceof JLabel label) {
                    label.setHorizontalAlignment(SwingConstants.CENTER);
                    Object value = getValueAt(row, column);
                    label.setToolTipText(value == null ? null : value.toString());
                } else if (component instanceof AbstractButton button) {
                    button.setHorizontalAlignment(SwingConstants.CENTER);
                }
                return component;
            }
            @Override
            public boolean getScrollableTracksViewportWidth() {
                Container parent = getParent();
                return !(parent instanceof JViewport) || getPreferredSize().width <= parent.getWidth();
            }
        };
    }

    /** Sets each column's starting width in pixels, in column order. */
    public static void widths(JTable table, int... widths) {
        int count = Math.min(widths.length, table.getColumnModel().getColumnCount());
        for (int i = 0; i < count; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    /** Shows a Status column as colored tags instead of plain text. */
    public static void statusColumn(JTable table, int column) {
        table.getColumnModel().getColumn(column).setCellRenderer(new TagRenderer());
    }

    /** The text color and tint for a status word, or null when the word is not a known status. */
    private static Color[] toneOf(String word) {
        switch (word) {
            case "Active":
            case "Available":
            case "In Stock":
            case "Delivered":
            case "OK":
                return new Color[] {GOOD, GOOD_BG};
            case "Archived":
            case "Off":
                return new Color[] {MUTED, IDLE_BG};
            case "Pending":
            case "Low Stock":
            case "LOW - reorder":
                return new Color[] {HOLD, WARNING};
            case "Out of Stock":
                return new Color[] {DANGER, new Color(0xFCE6E4)};
            case "Out for Delivery":
                return new Color[] {MOVING, MOVING_BG};
            default:
                return null;
        }
    }

    /** Draws a known status word as a rounded tag on the row's own background. */
    static final class TagRenderer extends DefaultTableCellRenderer {

        private Color textColor;
        private Color tint;
        private Color rowColor;

        TagRenderer() {
            setOpaque(false);
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            Color[] tone = toneOf(value == null ? "" : value.toString());
            textColor = tone == null ? null : tone[0];
            tint = tone == null ? null : tone[1];
            rowColor = selected ? table.getSelectionBackground() : table.getBackground();
            setForeground(textColor != null ? textColor : (selected ? table.getSelectionForeground() : TEXT));
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(rowColor == null ? Color.WHITE : rowColor);
            g2.fillRect(0, 0, getWidth(), getHeight());
            if (tint != null) {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(tint);
                g2.fillRoundRect(6, 4, getWidth() - 12, getHeight() - 8, 14, 14);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ----- a side panel of fixed width next to the main content -----

    /** The main content on the left and a fixed-width form on the right. The divider can be dragged. */
    public static JSplitPane splitFixedRight(JComponent main, JComponent side, int sideWidth) {
        return new FixedSplit(main, side, sideWidth, true);
    }

    /** A fixed-width panel on the left and the main content on the right. The divider can be dragged. */
    public static JSplitPane splitFixedLeft(JComponent side, JComponent main, int sideWidth) {
        return new FixedSplit(side, main, sideWidth, false);
    }

    /** A balanced dashboard split that remembers the user's chosen share as the window resizes. */
    public static JSplitPane splitProportional(JComponent left, JComponent right, double leftShare) {
        if (!Double.isFinite(leftShare) || leftShare <= 0 || leftShare >= 1) {
            throw new IllegalArgumentException("The left share must be between zero and one.");
        }
        return new ProportionalSplit(left, right, leftShare);
    }

    private static final class ProportionalSplit extends JSplitPane {
        private double desiredLeftShare;
        private boolean layingOut;

        ProportionalSplit(JComponent left, JComponent right, double leftShare) {
            super(JSplitPane.HORIZONTAL_SPLIT, left, right);
            desiredLeftShare = leftShare;
            setResizeWeight(leftShare);
            setDividerSize(10);
            setContinuousLayout(true);
            setBorder(BorderFactory.createEmptyBorder());
            setBackground(BACKGROUND);
            left.setMinimumSize(new Dimension(320, 0));
            right.setMinimumSize(new Dimension(320, 0));
            addPropertyChangeListener(DIVIDER_LOCATION_PROPERTY, event -> {
                if (!layingOut && availableWidth() > 0 && getHeight() > 0 && getDividerLocation() >= 0) {
                    desiredLeftShare = Math.max(0, Math.min(1,
                            (getDividerLocation() - getInsets().left) / (double) availableWidth()));
                }
            });
        }

        private int availableWidth() {
            Insets insets = getInsets();
            return Math.max(0, getWidth() - insets.left - insets.right - getDividerSize());
        }

        @Override public void doLayout() {
            layingOut = true;
            try {
                super.doLayout();
                if (getWidth() <= 0 || getHeight() <= 0) return;
                int available = availableWidth();
                int maximumLeft = Math.max(0, available - getRightComponent().getMinimumSize().width);
                int minimumLeft = Math.min(getLeftComponent().getMinimumSize().width, maximumLeft);
                int leftWidth = Math.max(minimumLeft, Math.min(maximumLeft,
                        (int) Math.round(available * desiredLeftShare)));
                int location = getInsets().left + leftWidth;
                // Apply the share before the first paint as well as after a hidden card resizes.
                if (getDividerLocation() != location || getLeftComponent().getX()
                        + getLeftComponent().getWidth() != location) {
                    setDividerLocation(location);
                    super.doLayout();
                }
            } finally {
                layingOut = false;
            }
        }
    }

    /** Keeps the side panel at its width when the window grows, so only the main content stretches. */
    private static final class FixedSplit extends JSplitPane {

        private int desiredSideWidth;
        private final boolean sideOnRight;
        private boolean layingOut;

        FixedSplit(JComponent left, JComponent right, int sideWidth, boolean sideOnRight) {
            super(JSplitPane.HORIZONTAL_SPLIT, left, right);
            this.desiredSideWidth = sideWidth;
            this.sideOnRight = sideOnRight;
            setResizeWeight(sideOnRight ? 1.0 : 0.0);
            setDividerSize(10);
            setContinuousLayout(true);
            setBorder(BorderFactory.createEmptyBorder());
            setBackground(BACKGROUND);
            JComponent side = sideOnRight ? right : left;
            JComponent main = sideOnRight ? left : right;
            side.setPreferredSize(new Dimension(sideWidth, side.getPreferredSize().height));
            side.setMinimumSize(new Dimension(sideOnRight ? Math.min(sideWidth, 280) : sideWidth - 60, 0));
            main.setMinimumSize(new Dimension(320, 0));
            addPropertyChangeListener(DIVIDER_LOCATION_PROPERTY, event -> {
                // Only explicit moves (including dragging) change the remembered width.
                // Swing also updates this property during layout, including temporary clamps.
                if (!layingOut && getWidth() > 0 && getHeight() > 0 && getDividerLocation() >= 0) {
                    Insets insets = getInsets();
                    int width = sideOnRight
                            ? getWidth() - insets.right - getDividerSize() - getDividerLocation()
                            : getDividerLocation() - insets.left;
                    desiredSideWidth = Math.max(0, width);
                }
            });
        }

        @Override
        public void doLayout() {
            layingOut = true;
            try {
                super.doLayout();
                if (getWidth() <= 0 || getHeight() <= 0) {
                    return;
                }
                Insets insets = getInsets();
                int available = Math.max(0, getWidth() - insets.left - insets.right - getDividerSize());
                Component side = sideOnRight ? getRightComponent() : getLeftComponent();
                Component main = sideOnRight ? getLeftComponent() : getRightComponent();
                int maxSideWidth = Math.max(0, available - main.getMinimumSize().width);
                int minSideWidth = Math.min(side.getMinimumSize().width, maxSideWidth);
                int sideWidth = Math.max(minSideWidth, Math.min(desiredSideWidth, maxSideWidth));
                int location = insets.left + (sideOnRight ? available - sideWidth : sideWidth);
                // Before a hidden card's first paint, Swing ignores resizeWeight and keeps
                // its old absolute divider position. Anchor the side on every layout instead.
                Component left = getLeftComponent();
                if (getDividerLocation() != location || left.getX() + left.getWidth() != location) {
                    setDividerLocation(location);
                    super.doLayout();
                }
            } finally {
                layingOut = false;
            }
        }
    }

    // ----- sidebar headings and the window icon -----

    /** A small uppercase heading that groups items in the sidebar menu. */
    public static JLabel sidebarHeading(String text) {
        JLabel label = new JLabel(text.toUpperCase(Locale.ENGLISH));
        label.setFont(HEADING);
        label.setForeground(SIDEBAR_HEADING);
        label.setBorder(BorderFactory.createEmptyBorder(6, 22, 6, 12));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    /** The AquaTrack droplet at the sizes Windows uses for the title bar, taskbar, and window switcher. */
    public static List<Image> appIcons() {
        List<Image> icons = new ArrayList<>();
        for (int size : new int[] {16, 24, 32, 48, 64, 128}) {
            icons.add(dropletIcon(size));
        }
        return icons;
    }

    public static String escapeHtml(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** Draws the droplet with plain Java2D, so the app needs no image file. */
    static BufferedImage dropletIcon(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        double s = size;
        Path2D.Double drop = new Path2D.Double();
        drop.moveTo(0.50 * s, 0.05 * s);
        drop.curveTo(0.50 * s, 0.05 * s, 0.17 * s, 0.44 * s, 0.17 * s, 0.64 * s);
        drop.curveTo(0.17 * s, 0.85 * s, 0.31 * s, 0.95 * s, 0.50 * s, 0.95 * s);
        drop.curveTo(0.69 * s, 0.95 * s, 0.83 * s, 0.85 * s, 0.83 * s, 0.64 * s);
        drop.curveTo(0.83 * s, 0.44 * s, 0.50 * s, 0.05 * s, 0.50 * s, 0.05 * s);
        drop.closePath();
        g.setPaint(new GradientPaint(0f, (float) (0.05 * s), ACCENT, 0f, (float) (0.95 * s), SIDEBAR));
        g.fill(drop);
        g.setColor(new Color(255, 255, 255, 190));
        g.fill(new Ellipse2D.Double(0.29 * s, 0.50 * s, 0.11 * s, 0.19 * s));
        g.dispose();
        return image;
    }

    // ----- dialogs -----

    public static void info(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "AquaTrack", JOptionPane.INFORMATION_MESSAGE);
    }

    public static void warn(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Please check", JOptionPane.WARNING_MESSAGE);
    }

    public static boolean confirm(Component parent, String question) {
        return JOptionPane.showConfirmDialog(parent, question, "AquaTrack", JOptionPane.YES_NO_OPTION)
                == JOptionPane.YES_OPTION;
    }

    /** Turns a database or program exception into a message a non-programmer can act on. */
    public static void error(Component parent, Exception e) {
        String message;
        if (e instanceof SQLIntegrityConstraintViolationException) {
            message = "That conflicts with an existing record, such as a duplicate name or number.";
        } else if (e instanceof SQLException) {
            message = "Database problem:\n" + e.getMessage() + "\n\nMake sure XAMPP's MySQL is running.";
        } else {
            message = "Something went wrong:\n" + e.getMessage();
        }
        JOptionPane.showMessageDialog(parent, message, "AquaTrack", JOptionPane.ERROR_MESSAGE);
    }
}
