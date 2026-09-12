// ============================================================================
// Dashboard.java  —  TWILIGHT PEAKS DASHBOARD  (updated)
// ----------------------------------------------------------------------------
// NEW: logout confirmation and settings popups now use the themed
// ThemedDialogs frosted-glass dialogs instead of plain JOptionPanes.
// ============================================================================
import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class Dashboard extends JFrame {

    private final CardLayout contentCards = new CardLayout();
    private final JPanel contentPanel = new JPanel(contentCards);
    private final Map<String, NavButton> nav = new LinkedHashMap<>();

    public Dashboard() {
        setTitle("Dashboard — Twilight Peaks");
        setSize(1080, 700);
        setMinimumSize(new Dimension(920, 580));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        ScenePanel root = new ScenePanel();
        root.setLayout(new BorderLayout(14, 14));
        root.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        /* ---------- top bar ---------- */
        GlassPanel topBar = new GlassPanel(new BorderLayout(), 24);
        topBar.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 18));
        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        brand.setOpaque(false);
        brand.add(new MoonLabel());
        JLabel appName = new JLabel("Twilight Peaks");
        appName.setFont(UIKit.font(17f, Font.BOLD));
        appName.setForeground(UIKit.TEXT);
        brand.add(appName);
        topBar.add(brand, BorderLayout.WEST);
        JLabel greeting = new JLabel("Good evening, explorer");
        greeting.setFont(UIKit.font(12.5f, Font.PLAIN));
        greeting.setForeground(UIKit.TEXT_DIM);
        topBar.add(greeting, BorderLayout.EAST);
        root.add(topBar, BorderLayout.NORTH);

        /* ---------- sidebar ---------- */
        GlassPanel sidebar = new GlassPanel(new GridBagLayout(), 24);
        GridBagConstraints sc = new GridBagConstraints();
        sc.gridx = 0; sc.weightx = 1; sc.fill = GridBagConstraints.HORIZONTAL;
        sc.insets = new Insets(6, 10, 6, 10);

        JLabel navTitle = new JLabel("  MENU");
        navTitle.setFont(UIKit.font(11f, Font.BOLD));
        navTitle.setForeground(UIKit.TEXT_DIM);
        sc.gridy = 0; sc.insets = new Insets(16, 14, 8, 10); sidebar.add(navTitle, sc);
        sc.insets = new Insets(6, 10, 6, 10);

        String[] items = {"Home", "Profile", "Settings"};
        int y = 1;
        for (String item : items) {
            NavButton b = new NavButton(item);
            nav.put(item, b);
            sc.gridy = y++;
            sidebar.add(b, sc);
            b.addActionListener(e -> select(item));
        }
        sc.gridy = y++; sc.weighty = 1;
        sidebar.add(Box.createGlue(), sc);
        sc.weighty = 0;

        GhostButton logoutButton = new GhostButton("Logout");
        sc.gridy = y; sc.insets = new Insets(6, 10, 16, 10);
        sidebar.add(logoutButton, sc);
        logoutButton.addActionListener(e -> {
            int confirm = ThemedDialogs.showConfirm(
                    logoutButton,
                    "Are you sure you want to logout?",
                    "Confirm Logout");

            if (confirm == JOptionPane.YES_OPTION) {
                dispose();
                SwingUtilities.invokeLater(() -> new Loginform().setVisible(true));
            }
        });
        sidebar.setPreferredSize(new Dimension(210, 10));
        root.add(sidebar, BorderLayout.WEST);

        /* ---------- content cards ---------- */
        contentPanel.setOpaque(false);
        contentPanel.add(homeCard(), "Home");
        contentPanel.add(profileCard(), "Profile");
        contentPanel.add(settingsCard(), "Settings");
        root.add(contentPanel, BorderLayout.CENTER);

        setContentPane(root);
        select("Home");
    }

    private void select(String name) {
        for (Map.Entry<String, NavButton> e : nav.entrySet())
            e.getValue().setSelected(e.getKey().equals(name));
        contentCards.show(contentPanel, name);
    }

    /* ---------- HOME: greeting + three rounded stat cards ---------- */
    private JPanel homeCard() {
        GlassPanel card = new GlassPanel(new BorderLayout(18, 18), 26);
        card.setBorder(BorderFactory.createEmptyBorder(26, 28, 26, 28));

        JPanel head = new JPanel();
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.setOpaque(false);
        JLabel t = new JLabel("Overview");
        t.setFont(UIKit.font(22f, Font.BOLD)); t.setForeground(UIKit.TEXT);
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel s = new JLabel("Your night-sky summary at a glance");
        s.setFont(UIKit.font(12.5f, Font.PLAIN)); s.setForeground(UIKit.TEXT_DIM);
        s.setAlignmentX(Component.LEFT_ALIGNMENT);
        head.add(t); head.add(Box.createVerticalStrut(4)); head.add(s);
        card.add(head, BorderLayout.NORTH);

        JPanel stats = new JPanel(new GridLayout(1, 3, 16, 16));
        stats.setOpaque(false);
        stats.add(statCard("12", "Projects climbing"));
        stats.add(statCard("36", "Focus hours"));
        stats.add(statCard("9", "Day streak"));
        card.add(stats, BorderLayout.CENTER);

        JLabel quote = new JLabel("“Climb quietly — the view from the peak is worth the mist.”");
        quote.setFont(UIKit.font(12f, Font.ITALIC));
        quote.setForeground(UIKit.TEXT_DIM);
        card.add(quote, BorderLayout.SOUTH);
        return card;
    }

    private JPanel statCard(String value, String label) {
        GlassPanel p = new GlassPanel(new GridBagLayout(), 22);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.insets = new Insets(2, 12, 2, 12);
        JLabel v = new JLabel(value);
        v.setFont(UIKit.font(30f, Font.BOLD)); v.setForeground(UIKit.GLOW_PEACH);
        JLabel l = new JLabel(label);
        l.setFont(UIKit.font(12f, Font.PLAIN)); l.setForeground(UIKit.TEXT_DIM);
        c.gridy = 0; p.add(v, c);
        c.gridy = 1; p.add(l, c);
        return p;
    }

    /* ---------- PROFILE: avatar circle + details ---------- */
    private JPanel profileCard() {
        GlassPanel card = new GlassPanel(new GridBagLayout(), 26);
        card.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.insets = new Insets(6, 0, 6, 0);

        c.gridy = 0; card.add(new Avatar("EX"), c);
        JLabel name = new JLabel("Explorer of the Peaks");
        name.setFont(UIKit.font(20f, Font.BOLD)); name.setForeground(UIKit.TEXT);
        c.gridy = 1; card.add(name, c);
        JLabel mail = new JLabel("explorer@twilightpeaks.dev");
        mail.setFont(UIKit.font(12.5f, Font.PLAIN)); mail.setForeground(UIKit.TEXT_DIM);
        c.gridy = 2; card.add(mail, c);
        JLabel role = new JLabel("Role: Night Cartographer   ·   Base camp: Valley of Mist");
        role.setFont(UIKit.font(12.5f, Font.PLAIN)); role.setForeground(UIKit.TEXT_DIM);
        c.gridy = 3; c.insets = new Insets(14, 0, 6, 0); card.add(role, c);
        return card;
    }

    /* ---------- SETTINGS: playful toggles + save ---------- */
    private JPanel settingsCard() {
        GlassPanel card = new GlassPanel(new GridBagLayout(), 26);
        card.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(8, 40, 8, 40);

        JLabel t = new JLabel("Settings", SwingConstants.CENTER);
        t.setFont(UIKit.font(20f, Font.BOLD)); t.setForeground(UIKit.TEXT);
        c.gridy = 0; card.add(t, c);

        GhostButton mist = new GhostButton("Valley mist: ON");
        mist.addActionListener(e -> mist.setText(mist.getText().endsWith("ON")
                ? "Valley mist: OFF" : "Valley mist: ON"));
        c.gridy = 1; card.add(mist, c);

        GhostButton stars = new GhostButton("Star field: ON");
        stars.addActionListener(e -> stars.setText(stars.getText().endsWith("ON")
                ? "Star field: OFF" : "Star field: ON"));
        c.gridy = 2; card.add(stars, c);

        RoundedButton save = new RoundedButton("Save changes");
        save.addActionListener(e -> ThemedDialogs.showMessage(this,
                "Settings saved", "Success", JOptionPane.INFORMATION_MESSAGE));
        c.gridy = 3; c.insets = new Insets(16, 40, 8, 40); card.add(save, c);
        return card;
    }
}

/* ---------- circular gradient avatar with initials ---------- */
class Avatar extends JComponent {
    private final String initials;
    Avatar(String initials) {
        this.initials = initials;
        setPreferredSize(new Dimension(84, 84));
    }
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setPaint(new GradientPaint(0, 0, UIKit.GLOW_PINK, getWidth(), getHeight(), UIKit.MIST));
        g2.fillOval(0, 0, getWidth(), getHeight());
        g2.setPaint(new Color(255, 255, 255, 70));
        g2.setStroke(new BasicStroke(1.4f));
        g2.drawOval(1, 1, getWidth() - 3, getHeight() - 3);
        g2.setPaint(UIKit.INK_ON_ACCENT);
        g2.setFont(UIKit.font(26f, Font.BOLD));
        FontMetrics fm = g2.getFontMetrics();
        int tx = (getWidth() - fm.stringWidth(initials)) / 2;
        int ty = getHeight() / 2 + fm.getAscent() / 2 - 3;
        g2.drawString(initials, tx, ty);
        g2.dispose();
    }
}