package aquatrack.ui;

import aquatrack.dao.UserDao;
import aquatrack.model.Product;
import aquatrack.model.User;
import aquatrack.util.Session;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Read-only smoke and render checks using the synthetic integration database. Never saves a record. */
public final class ScreenVisualSmokeTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        String url = System.getProperty("aquatrack.db.url", "");
        if (!url.startsWith("jdbc:mysql://127.0.0.1:3306/aquatrack_usability_test_20261022?")) {
            throw new IllegalArgumentException("Use the explicit isolated integration database URL.");
        }
        User owner = new UserDao().findAll().stream().filter(u -> u.isOwner() && u.isActive()).findFirst().orElseThrow();
        AtomicReference<List<JPanel>> holder = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception e) { throw new RuntimeException(e); }
            Session.start(owner);
            holder.set(List.of(new DashboardPanel(), new ProductsPanel(), new InventoryPanel(), new AccountsPanel(),
                    new ContainersPanel(), new OrderPanel(), new CustomersPanel(), new DeliveriesPanel(), new ReportsPanel()));
            holder.get().forEach(panel -> ((MainFrame.Screen) panel).refresh());
        });
        for (JPanel panel : holder.get()) {
            long deadline = System.nanoTime() + 20_000_000_000L;
            boolean ready = false;
            while (!ready && System.nanoTime() < deadline) {
                AtomicReference<Boolean> done = new AtomicReference<>(false);
                SwingUtilities.invokeAndWait(() -> done.set(ready(panel)));
                ready = done.get();
                if (!ready) Thread.sleep(40);
            }
            check(ready, panel.getClass().getSimpleName() + " data load completed");
            SwingUtilities.invokeAndWait(() -> {
                JTable table = find(panel, JTable.class);
                if (table != null && table.getRowCount() > 0) table.setRowSelectionInterval(0, 0);
                if (panel instanceof OrderPanel) {
                    JComboBox<?> choices = field(panel, "productBox", JComboBox.class);
                    for (int i = 0; i < choices.getItemCount(); i++) {
                        if (choices.getItemAt(i) instanceof Product p && Product.REFILL.equals(p.getCategory())) {
                            choices.setSelectedIndex(i); break;
                        }
                    }
                    field(panel, "addButton", JButton.class).doClick(); // Draft only, never Save.
                }
                String name = panel.getClass().getSimpleName();
                LoginAndThemeTest.render(panel, 1114, 720, 1, name + "-1366.png");
                LoginAndThemeTest.render(panel, 1668, 1032, 1, name + "-1920.png");
                LoginAndThemeTest.render(panel, 1284, 816, 1.25, name + "-125.png");
                LoginAndThemeTest.render(panel, 1028, 672, 1.5, name + "-150.png");
                WorkspacePanel workspace = new WorkspacePanel(new java.awt.BorderLayout());
                workspace.add(panel);
                javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(workspace);
                scroll.setBorder(javax.swing.BorderFactory.createEmptyBorder());
                LoginAndThemeTest.render(scroll, 658, 464, 1.5, name + "-1366-at150.png");
                workspace.remove(panel);
                if (table != null && table.getRowCount() > 0) {
                    for (int i = 0; i < table.getColumnCount(); i++) {
                        Component cell = table.prepareRenderer(table.getCellRenderer(0, i), 0, i);
                        if (cell instanceof JLabel label) check(label.getHorizontalAlignment() == SwingConstants.CENTER,
                                name + " centered column " + i);
                    }
                }
            });
        }
        SwingUtilities.invokeAndWait(Session::end);
        System.out.println("ScreenVisualSmokeTest passed: " + checks + " checks; 45 populated-screen images in build/qa.");
    }
    private static boolean ready(JPanel panel) {
        if (panel instanceof DashboardPanel) return !"—".equals(field(panel, "todaySales", JLabel.class).getText());
        if (panel instanceof ReportsPanel) return field(panel, "loadedDocument", Object.class) != null;
        if (panel instanceof DeliveriesPanel) return !field(panel, "loading", Boolean.class);
        if (panel instanceof OrderPanel) return field(panel, "choicesLoaded", Boolean.class) && !field(panel, "busy", Boolean.class);
        return field(panel, "recordsLoaded", Boolean.class) && !((MainFrame.Screen) panel).hasPendingOperation();
    }
    private static <T> T field(Object object, String name, Class<T> type) {
        try { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return type.cast(field.get(object)); }
        catch (Exception e) { throw new AssertionError(e); }
    }
    private static <T> T find(Container parent, Class<T> type) {
        for (Component child : parent.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container nested) { T found = find(nested, type); if (found != null) return found; }
        }
        return null;
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
}
