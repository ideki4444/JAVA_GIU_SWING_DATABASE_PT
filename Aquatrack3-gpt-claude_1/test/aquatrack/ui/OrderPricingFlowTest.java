package aquatrack.ui;

import aquatrack.dao.OrderDao;
import aquatrack.model.Customer;
import aquatrack.model.CustomerOrder;
import aquatrack.model.OrderLine;
import aquatrack.model.Product;
import aquatrack.model.User;
import aquatrack.util.OrderRules;
import aquatrack.util.Session;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;

/** Tests draft repricing, explicit review, worker boundaries and post-commit receipt retry without MySQL. */
public final class OrderPricingFlowTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        Session.start(new User(1, "owner", "Owner", "Owner/Admin", ""));
        typeChangesAndSave();
        missingDeliveryRate();
        authoritativeRetryAndFailedRefresh();
        Session.end();
        System.out.println("OrderPricingFlowTest passed: " + checks + " checks");
    }

    @SuppressWarnings("unchecked")
    private static void typeChangesAndSave() throws Exception {
        FakeData data = new FakeData();
        Messages messages = new Messages();
        OrderPanel panel = ready(data, messages);
        check(edt(() -> {
            JComboBox<Product> choices = field(panel, "productBox", JComboBox.class);
            javax.swing.JLabel label = (javax.swing.JLabel) choices.getRenderer().getListCellRendererComponent(
                    new javax.swing.JList<Product>(), choices.getItemAt(0), 0, false, false);
            return label.getText().contains("Available");
        }), "order choices share the refill availability terminology");
        edt(() -> {
            field(panel, "quantity", JSpinner.class).setValue(2);
            call(panel, "addItem");
            field(panel, "borrowed", JSpinner.class).setValue(2);
            field(panel, "delivery", JRadioButton.class).doClick();
            return null;
        });
        check(total(panel).compareTo(money("60")) == 0, "existing refill repriced on Delivery");
        edt(() -> { call(panel, "addItem"); return null; });
        check(total(panel).compareTo(money("90")) == 0, "merged quantity uses delivery price");
        edt(() -> {
            field(panel, "productBox", JComboBox.class).setSelectedIndex(1);
            call(panel, "addItem");
            field(panel, "walkIn", JRadioButton.class).doClick();
            return null;
        });
        check(total(panel).compareTo(money("80")) == 0, "pickup reprices refills while accessory remains five");
        check(edt(() -> field(panel, "borrowed", JSpinner.class).getValue().equals(2)), "type switch preserves container counts");
        edt(() -> { field(panel, "delivery", JRadioButton.class).doClick(); return null; });
        check(total(panel).compareTo(money("95")) == 0, "repeated switching does not stack a surcharge");
        data.saveGate = new CountDownLatch(1);
        edt(() -> { call(panel, "save"); call(panel, "save"); return null; });
        check(data.saveStarted.await(3, TimeUnit.SECONDS), "background save started");
        check(edt(() -> !field(panel, "saveButton", JButton.class).isEnabled()), "save controls disabled while pending");
        data.saveGate.countDown();
        idle(panel);
        check(data.saves == 1, "double submit ignored");
        check(data.savedType.equals("Delivery") && data.savedTotal.compareTo(money("95")) == 0, "submitted type and prices agree");
        check(total(panel).signum() == 0 && messages.receipts == 1, "committed draft clears and preview opens");
        check(edt(() -> field(panel, "lastSavedOrderId", Integer.class) == 77), "receipt ID retained after commit");
        data.failReceipt = true;
        edt(() -> { call(panel, "showLastReceipt"); return null; });
        idle(panel);
        check(data.saves == 1 && messages.lastWarning.contains("saved successfully"), "receipt failure never resubmits sale");
    }

    private static void missingDeliveryRate() throws Exception {
        FakeData data = new FakeData();
        data.products.set(0, new Product(10, "Refill", Product.REFILL, money("25"), 0, 0, true));
        Messages messages = new Messages();
        OrderPanel panel = ready(data, messages);
        edt(() -> {
            call(panel, "addItem");
            field(panel, "delivery", JRadioButton.class).doClick();
            return null;
        });
        check(edt(() -> field(panel, "walkIn", JRadioButton.class).isSelected()), "invalid rate reverts attempted order type");
        check(total(panel).compareTo(money("25")) == 0, "missing rate preserves original draft");
        check(messages.lastWarning.contains("positive delivery price"), "missing rate requires setup, no fallback");
    }

    private static void authoritativeRetryAndFailedRefresh() throws Exception {
        FakeData data = new FakeData();
        Messages messages = new Messages();
        OrderPanel panel = ready(data, messages);
        edt(() -> {
            field(panel, "delivery", JRadioButton.class).doClick();
            call(panel, "addItem");
            return null;
        });
        data.changePrice = true;
        data.failRefreshAfterChange = true;
        edt(() -> { call(panel, "save"); return null; });
        idle(panel);
        check(total(panel).compareTo(money("35")) == 0, "authoritative returned price survives refresh failure");
        check(edt(() -> !field(panel, "saveButton", JButton.class).isEnabled()), "refresh failure blocks accidental resubmission");
        check(messages.receipts == 0 && data.saves == 1, "price review does not show a saved receipt");
        data.failRefreshAfterChange = false;
        edt(() -> { panel.refresh(); return null; });
        idle(panel);
        check(total(panel).compareTo(money("35")) == 0, "choice refresh does not silently reprice reviewed lines from stale catalog");
        edt(() -> { call(panel, "save"); return null; });
        idle(panel);
        check(data.savedTotal.compareTo(money("35")) == 0 && messages.receipts == 1, "explicit second Save submits reviewed price");
    }

    private static OrderPanel ready(FakeData data, Messages messages) throws Exception {
        OrderPanel panel = edt(() -> new OrderPanel(data, messages));
        edt(() -> { panel.refresh(); return null; });
        idle(panel);
        return panel;
    }

    private static final class FakeData implements OrderPanel.OrderData {
        final List<Product> products = new ArrayList<>(List.of(
                new Product(10, "Refill", Product.REFILL, money("25"), money("30"), 0, 0, true),
                new Product(20, "Cap", Product.CAP_SEAL, money("5"), null, 20, 3, true)));
        volatile CountDownLatch saveGate;
        final CountDownLatch saveStarted = new CountDownLatch(1);
        volatile int saves;
        volatile boolean changePrice, changed, failRefreshAfterChange, failReceipt;
        volatile String savedType;
        volatile BigDecimal savedTotal;
        volatile List<OrderLine> savedLines = List.of();
        public List<Customer> customers() { offEdt(); return List.of(new Customer(1, "Customer", "Bohol", "09171234567")); }
        public List<Product> products() throws SQLException {
            offEdt();
            if (changed && failRefreshAfterChange) throw new SQLException("refresh failed");
            return new ArrayList<>(products);
        }
        public int placeOrder(int id, String type, String payment, int user, int borrowed, int returned, List<OrderLine> lines)
                throws SQLException {
            offEdt();
            saves++;
            saveStarted.countDown();
            if (saveGate != null) try { if (!saveGate.await(3, TimeUnit.SECONDS)) throw new SQLException("test timeout"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new SQLException(e); }
            if (changePrice) {
                changePrice = false;
                changed = true;
                throw new OrderDao.PricesChangedException(List.of(OrderLine.priced(10, "Refill", 1, money("35"))));
            }
            savedType = type;
            savedTotal = OrderRules.total(lines);
            savedLines = new ArrayList<>(lines);
            return 77;
        }
        public CustomerOrder receipt(int id) throws SQLException {
            offEdt();
            if (failReceipt) throw new SQLException("receipt failed");
            CustomerOrder order = new CustomerOrder(id, LocalDateTime.of(2026, 10, 21, 12, 0),
                    "Customer", "Bohol", "09171234567", savedType, "Cash", savedTotal, "Pending", "", "Owner");
            order.setLines(savedLines);
            return order;
        }
    }

    private static final class Messages implements OrderPanel.Feedback {
        volatile int receipts;
        volatile String lastWarning = "";
        public void warn(String message) { onEdt(); lastWarning = message; }
        public void error(SQLException error) { throw new AssertionError(error); }
        public void receipt(CustomerOrder order) { onEdt(); receipts++; }
    }

    @SuppressWarnings("unchecked")
    private static BigDecimal total(OrderPanel panel) throws Exception {
        return edt(() -> OrderRules.total((List<OrderLine>) field(panel, "cart", List.class)));
    }
    private static BigDecimal money(String value) { return new BigDecimal(value); }
    private static void offEdt() { if (SwingUtilities.isEventDispatchThread()) throw new AssertionError("DB on EDT"); }
    private static void onEdt() { if (!SwingUtilities.isEventDispatchThread()) throw new AssertionError("feedback off EDT"); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
    private static void idle(Object panel) throws Exception {
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!edt(() -> !field(panel, "busy", Boolean.class))) {
            if (System.nanoTime() > until) throw new AssertionError("worker timeout");
            Thread.sleep(10);
        }
    }
    private static <T> T edt(Callable<T> task) throws Exception {
        java.util.concurrent.FutureTask<T> future = new java.util.concurrent.FutureTask<>(task);
        SwingUtilities.invokeAndWait(future);
        return future.get();
    }
    private static <T> T field(Object object, String name, Class<T> type) throws Exception {
        Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return type.cast(f.get(object));
    }
    private static void call(Object object, String name) throws Exception {
        Method m = object.getClass().getDeclaredMethod(name); m.setAccessible(true); m.invoke(object);
    }
}
