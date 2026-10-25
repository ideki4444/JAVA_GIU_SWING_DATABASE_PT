package aquatrack.ui;

import aquatrack.dao.ProductDao;
import aquatrack.model.Product;
import aquatrack.model.User;
import aquatrack.util.Session;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** Database-free search, stock, pricing and asynchronous screen regression checks. */
public final class ProductInventoryFlowTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        Session.start(new User(1, "owner", "Owner", "Owner/Admin", ""));
        policy();
        products();
        inventory();
        Session.end();
        System.out.println("ProductInventoryFlowTest passed: " + checks + " checks");
    }

    private static void policy() {
        Product refill = new Product(1, "Refill", Product.REFILL, money("25"), money("30"), 0, 0, true);
        check(refill.priceForOrderType("Walk-in").compareTo(money("25")) == 0, "pickup rate");
        check(refill.priceForOrderType("Delivery").compareTo(money("30")) == 0, "delivery rate");
        check(refill.getAvailability().equals("Available"), "untracked refill is available");
        Product supply = new Product(2, "Cap", Product.CAP_SEAL, money("5"), money("999"), 4, 3, true);
        check(supply.priceForOrderType("Delivery").compareTo(money("5")) == 0, "supply ignores delivery rate");
        check(supply.getAvailability().equals("In Stock"), "above alert");
        check(new Product(2, "Cap", Product.CAP_SEAL, money("5"), 3, 3, true).getAvailability().equals("Low Stock"), "at alert");
        check(new Product(2, "Cap", Product.CAP_SEAL, money("5"), 0, 3, true).getAvailability().equals("Out of Stock"), "zero stock");
        check(new Product(2, "Cap", Product.CAP_SEAL, money("5"), 0, 3, false).getAvailability().equals("Archived"), "archive precedence");
        expectInvalid(() -> new Product(1, "Refill", Product.REFILL, money("25"), 0, 0, true).priceForOrderType("Delivery"));
        expectInvalid(() -> refill.priceForOrderType("delivery"));
    }

    private static void products() throws Exception {
        FakeProducts dao = new FakeProducts();
        dao.gate = new CountDownLatch(1);
        ProductsPanel panel = edt(() -> new ProductsPanel(dao));
        edt(() -> { panel.refresh(); return null; });
        check(dao.entered.await(3, TimeUnit.SECONDS), "load started");
        check(edt(() -> !field(panel, "saveButton", javax.swing.JButton.class).isEnabled()), "loading disables writes without blocking EDT");
        dao.gate.countDown();
        idle(panel);
        JTable table = field(panel, "table", JTable.class);
        check(edt(() -> table.getRowCount() == 2), "current filter excludes archived");
        check(edt(() -> table.getValueAt(0, 3).toString().contains("30.00")), "delivery price visible");
        JTextField search = field(panel, "search", JTextField.class);
        edt(() -> { search.setText(" [ "); return null; });
        await(() -> edt(() -> table.getRowCount() == 1));
        check(edt(() -> table.getValueAt(0, 0).equals("Cap [small]")), "literal punctuation search");
        edt(() -> { table.setRowSelectionInterval(0, 0); search.setText("purified"); return null; });
        await(() -> edt(() -> table.getRowCount() == 1 && table.getValueAt(0, 0).equals("Purified refill")));
        check(edt(() -> field(panel, "editing", JLabel.class).getText().contains("hidden by filter")), "hidden edit is explicit");
        edt(() -> {
            field(panel, "nameField", JTextField.class).setText("Cap [small] updated");
            field(panel, "priceField", JTextField.class).setText("6.00");
            call(panel, "save");
            call(panel, "save"); // duplicate click must be ignored
            return null;
        });
        idle(panel);
        check(dao.updatedId == 20 && dao.writes == 1, "hidden selected ID updated exactly once");
        check(dao.savedDelivery == null, "supplies clear unused delivery rate");
        edt(() -> { search.setText("nothing matches"); return null; });
        await(() -> edt(() -> table.getRowCount() == 0));
        check(edt(() -> field(panel, "status", JLabel.class).getText().contains("No products match")), "empty search state");
        edt(() -> { search.setText(""); field(panel, "showBox", JComboBox.class).setSelectedIndex(2); return null; });
        await(() -> edt(() -> table.getRowCount() == 3));
        check(dao.reads == 2, "search does not query database");
        dao.failReads = true;
        edt(() -> { panel.refresh(); return null; });
        idle(panel);
        check(edt(() -> !field(panel, "saveButton", javax.swing.JButton.class).isEnabled()), "failed refresh disables stale writes");
    }

    private static void inventory() throws Exception {
        FakeProducts dao = new FakeProducts();
        InventoryPanel panel = edt(() -> new InventoryPanel(dao));
        edt(() -> { panel.refresh(); return null; });
        idle(panel);
        JTable table = field(panel, "table", JTable.class);
        check(edt(() -> table.getRowCount() == 1), "inventory excludes refill and archive");
        edt(() -> {
            table.setRowSelectionInterval(0, 0);
            field(panel, "addField", JTextField.class).setText("2");
            call(panel, "addStock");
            call(panel, "addStock");
            return null;
        });
        idle(panel);
        check(dao.stockId == 20 && dao.stockAdded == 2, "restock targets selected ID exactly once");
        check(edt(() -> table.getValueAt(0, 2).equals(10)), "stock refresh reflects committed count");
        check(edt(() -> field(panel, "status", JLabel.class).getText().contains("2 added")), "success survives refresh");
        check(edt(() -> ((javax.swing.JLabel) table.prepareRenderer(table.getCellRenderer(0, 0), 0, 0))
                .getHorizontalAlignment() == javax.swing.SwingConstants.CENTER), "custom inventory renderer centered");
        JTextField search = field(panel, "search", JTextField.class);
        JTextField alert = field(panel, "reorderField", JTextField.class);
        edt(() -> { alert.setText("7"); search.setText("cap"); return null; });
        await(() -> edt(() -> !field(panel, "searchTimer", javax.swing.Timer.class).isRunning()));
        check(edt(() -> alert.getText().equals("7")), "filter preserving selection preserves unfinished stock alert");
        edt(() -> { panel.refresh(); return null; });
        idle(panel);
        check(edt(() -> alert.getText().equals("7")), "refresh preserves unfinished alert for same item");
        edt(() -> { search.setText("does not exist"); return null; });
        await(() -> edt(() -> table.getRowCount() == 0));
        check(edt(() -> !field(panel, "addButton", javax.swing.JButton.class).isEnabled()), "no hidden restock target");
    }

    private static final class FakeProducts extends ProductDao {
        volatile CountDownLatch gate;
        final CountDownLatch entered = new CountDownLatch(1);
        volatile boolean failReads;
        volatile int reads, writes, updatedId, stockId, stockAdded;
        volatile BigDecimal savedDelivery;
        final List<Product> rows = new ArrayList<>(List.of(
            new Product(10, "Purified refill", Product.REFILL, money("25"), money("30"), 0, 0, true),
            new Product(20, "Cap [small]", Product.CAP_SEAL, money("5"), null, 8, 3, true),
            new Product(30, "Old bottle", Product.CONTAINER, money("180"), null, 0, 2, false)));
        @Override public List<Product> findAll(boolean activeOnly) throws SQLException {
            offEdt();
            reads++;
            entered.countDown();
            if (gate != null) try { if (!gate.await(3, TimeUnit.SECONDS)) throw new SQLException("test timeout"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new SQLException(e); }
            if (failReads) throw new SQLException("test unavailable");
            return rows.stream().filter(p -> !activeOnly || p.isActive()).toList();
        }
        @Override public Product findByName(String name, int except) { offEdt(); return null; }
        @Override public void update(int id, String name, String category, BigDecimal price, BigDecimal delivery, int alert) {
            offEdt(); writes++; updatedId = id; savedDelivery = delivery;
            for (int i = 0; i < rows.size(); i++) if (rows.get(i).getId() == id) {
                Product old = rows.get(i);
                rows.set(i, new Product(id, name, category, price, delivery, old.getStockQty(), alert, old.isActive()));
            }
        }
        @Override public void restock(int id, int count) {
            offEdt(); stockId = id; stockAdded += count;
            for (int i = 0; i < rows.size(); i++) if (rows.get(i).getId() == id) {
                Product old = rows.get(i);
                rows.set(i, new Product(id, old.getName(), old.getCategory(), old.getUnitPrice(), old.getDeliveryPrice(),
                        old.getStockQty() + count, old.getReorderLevel(), old.isActive()));
            }
        }
    }

    private static void offEdt() { if (SwingUtilities.isEventDispatchThread()) throw new AssertionError("database work ran on EDT"); }
    private static BigDecimal money(String value) { return new BigDecimal(value); }
    private static void expectInvalid(Runnable action) {
        try { action.run(); throw new AssertionError("expected validation failure"); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
    private static void idle(Object panel) throws Exception {
        await(() -> edt(() -> !field(panel, "busy", Boolean.class)));
    }
    private static void await(Callable<Boolean> condition) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.call()) {
            if (System.nanoTime() > until) throw new AssertionError("Timed out waiting for Swing worker");
            Thread.sleep(10);
        }
    }
    private static <T> T edt(Callable<T> task) throws Exception {
        java.util.concurrent.FutureTask<T> future = new java.util.concurrent.FutureTask<>(task);
        SwingUtilities.invokeAndWait(future);
        return future.get();
    }
    private static <T> T field(Object object, String name, Class<T> type) throws Exception {
        Field field = object.getClass().getDeclaredField(name); field.setAccessible(true);
        return type.cast(field.get(object));
    }
    private static void call(Object object, String name) throws Exception {
        Method method = object.getClass().getDeclaredMethod(name); method.setAccessible(true); method.invoke(object);
    }
}
