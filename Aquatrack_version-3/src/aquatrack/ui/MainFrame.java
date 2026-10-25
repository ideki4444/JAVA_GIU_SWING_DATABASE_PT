package aquatrack.ui;

import aquatrack.model.User;
import aquatrack.util.Session;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.Map;
import javax.swing.Box;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/** Main window: a sidebar on the left and one screen at a time on the right. Owner-only items are hidden from Staff. */
public final class MainFrame extends JFrame {

    /** Implemented by every screen that loads its own data from the database. */
    public interface Screen {
        void refresh();
    }

    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final JPanel navBox = new JPanel();
    private final ButtonGroup navGroup = new ButtonGroup();
    private final Map<String, Screen> screens = new HashMap<>();
    private final Map<String, UiKit.NavButton> navButtons = new HashMap<>();
    private final JLabel userLabel = UiKit.light("", UiKit.BODY);
    private JPanel group;

    public MainFrame() {
        super("AquaTrack");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setIconImages(UiKit.appIcons());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exit();
            }
        });

        navBox.setLayout(new BoxLayout(navBox, BoxLayout.Y_AXIS));
        navBox.setOpaque(false);
        content.setBackground(UiKit.BACKGROUND);
        add(buildSidebar(), BorderLayout.WEST);
        add(content, BorderLayout.CENTER);

        startSection(null, false);
        addScreen("dashboard", "Dashboard", new DashboardPanel(), false);
        startSection("Sales", false);
        addScreen("orders", "New Order", new OrderPanel(), false);
        addScreen("deliveries", "Deliveries", new DeliveriesPanel(), false);
        addScreen("containers", "Containers & Balances", new ContainersPanel(), false);
        startSection("Records", false);
        addScreen("customers", "Customers", new CustomersPanel(), false);
        addScreen("products", "Products & Prices", new ProductsPanel(), false);
        addScreen("inventory", "Inventory", new InventoryPanel(), false);
        startSection("Admin", true);
        addScreen("reports", "Reports", new ReportsPanel(), true);
        addScreen("accounts", "Accounts", new AccountsPanel(), true);

        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(1200, Math.max(1000, screen.width - 80)),
                Math.min(760, Math.max(660, screen.height - 110)));
        setMinimumSize(new Dimension(1000, 660));
        setLocationRelativeTo(null);
        showScreen("dashboard");
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(UiKit.SIDEBAR);
        sidebar.setPreferredSize(new Dimension(230, 0));

        JPanel brand = new JPanel();
        brand.setLayout(new BoxLayout(brand, BoxLayout.Y_AXIS));
        brand.setOpaque(false);
        brand.setBorder(BorderFactory.createEmptyBorder(22, 20, 18, 20));
        brand.add(UiKit.light("AquaTrack", new Font("Segoe UI", Font.BOLD, 22)));
        brand.add(UiKit.light("Water refilling station", UiKit.BODY));
        sidebar.add(brand, BorderLayout.NORTH);

        // The menu scrolls on a short window instead of being cut off above the footer buttons.
        JScrollPane navScroll = new JScrollPane(navBox, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        navScroll.setBorder(BorderFactory.createEmptyBorder());
        navScroll.setOpaque(false);
        navScroll.getViewport().setOpaque(false);
        navScroll.getVerticalScrollBar().setUnitIncrement(16);
        sidebar.add(navScroll, BorderLayout.CENTER);

        User user = Session.user();
        userLabel.setText(user == null ? "" : "<html>" + user.getFullName() + "<br>" + user.getRole() + "</html>");
        JPanel footer = new JPanel();
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(12, 20, 18, 20));
        footer.add(userLabel);
        footer.add(Box.createVerticalStrut(10));
        UiKit.ThemedButton logoutBtn = UiKit.button("Log out", UiKit.ACCENT);
        logoutBtn.addActionListener(e -> logout());
        footer.add(UiKit.fullWidth(logoutBtn));
        footer.add(Box.createVerticalStrut(8));
        UiKit.ThemedButton exitBtn = UiKit.button("Exit", UiKit.DANGER);
        exitBtn.addActionListener(e -> exit());
        footer.add(UiKit.fullWidth(exitBtn));
        sidebar.add(footer, BorderLayout.SOUTH);
        return sidebar;
    }

    /** Starts a group of menu items under an optional heading. Admin groups are hidden from Staff. */
    private void startSection(String heading, boolean ownerOnly) {
        group = new JPanel() {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));
        group.setOpaque(false);
        group.setAlignmentX(Component.LEFT_ALIGNMENT);
        group.setBorder(BorderFactory.createEmptyBorder(heading == null ? 0 : 14, 0, 0, 0));
        if (heading != null) {
            group.add(UiKit.sidebarHeading(heading));
        }
        group.setVisible(!ownerOnly || Session.isOwner());
        navBox.add(group);
    }

    /** Adds a screen and its sidebar button. Owner-only screens are hidden from Staff. */
    private void addScreen(String key, String label, JPanel screen, boolean ownerOnly) {
        content.add(screen, key);
        if (screen instanceof Screen) {
            screens.put(key, (Screen) screen);
        }
        UiKit.NavButton nav = new UiKit.NavButton(label);
        nav.addActionListener(e -> showScreen(key));
        navGroup.add(nav);
        group.add(nav);
        navButtons.put(key, nav);
        nav.setVisible(!ownerOnly || Session.isOwner());
    }

    private void showScreen(String key) {
        cards.show(content, key);
        UiKit.NavButton nav = navButtons.get(key);
        if (nav != null) {
            nav.setSelected(true);
        }
        Screen screen = screens.get(key);
        if (screen != null) {
            screen.refresh();
        }
    }

    private void logout() {
        if (UiKit.confirm(this, "Log out of AquaTrack?")) {
            Session.end();
            new LoginFrame().setVisible(true);
            dispose();
        }
    }

    private void exit() {
        if (UiKit.confirm(this, "Exit AquaTrack?")) {
            System.exit(0);
        }
    }
}
