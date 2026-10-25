package aquatrack.dao;

import aquatrack.model.OrderLine;
import aquatrack.model.Product;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Transaction-contract doubles. Real SQL semantics are covered by the isolated database checks. */
public final class OrderTransactionTest {
    private static int checks;

    public static void main(String[] args) throws Exception {
        mixedPricesAndHistoricalAmounts();
        priceReviewRollsBackStock();
        rejectedAndFailedOrdersRollBack();
        malformedInputDoesNotConnect();
        System.out.println("OrderTransactionTest passed: " + checks + " checks");
    }

    private static void mixedPricesAndHistoricalAmounts() throws Exception {
        Store delivery = new Store();
        int id = new OrderDao(delivery::connect).placeOrder(1, "Delivery", "Credit", 1, 2, 1, lines("30", "5"));
        check(id == 41 && delivery.commits == 1, "returns ID only after successful commit");
        check(money("65").compareTo(delivery.total) == 0, "delivery refill plus unchanged accessory price");
        check(delivery.stock.get(20) == 9 && delivery.stock.get(10) == 0, "only tracked product stock changes");
        check(delivery.gallons == 3 && "Pending".equals(delivery.status), "delivery records containers and pending status together");
        check(delivery.savedLines.size() == 2 && money("30").compareTo((BigDecimal) delivery.savedLines.get(0).get(4)) == 0,
                "order items persist delivered unit price");
        delivery.deliveryPrice = money("99");
        check(money("30").compareTo((BigDecimal) delivery.savedLines.get(0).get(4)) == 0,
                "later catalog change does not alter recorded item amounts");
        Store pickup = new Store();
        new OrderDao(pickup::connect).placeOrder(1, "Walk-in", "Cash", 1, 0, 0, lines("25", "5"));
        check(money("55").compareTo(pickup.total) == 0 && "Delivered".equals(pickup.status),
                "pickup uses ordinary prices and immediate service");
    }

    private static void priceReviewRollsBackStock() throws Exception {
        Store store = new Store();
        OrderDao dao = new OrderDao(store::connect);
        try {
            dao.placeOrder(1, "Delivery", "Cash", 1, 0, 0, lines("25", "4"));
            throw new AssertionError("expected price review");
        } catch (OrderDao.PricesChangedException expected) {
            List<OrderLine> reviewed = expected.getUpdatedLines();
            check(reviewed.size() == 2 && reviewed.get(0).getUnitPrice().compareTo(money("30")) == 0
                    && reviewed.get(1).getUnitPrice().compareTo(money("5")) == 0, "review includes all authoritative prices");
            check(store.stock.get(20) == 10 && store.rollbacks == 1 && store.commits == 0,
                    "price mismatch rolls back prior stock deduction");
            check(store.savedLines.isEmpty() && store.total == null, "price review creates no sale");
            dao.placeOrder(1, "Delivery", "Cash", 1, 0, 0, reviewed);
        }
        check(store.commits == 1 && store.stock.get(20) == 9, "explicit resubmission takes stock exactly once");
    }

    private static void rejectedAndFailedOrdersRollBack() throws Exception {
        Store missing = new Store();
        missing.deliveryPrice = null;
        reject(() -> new OrderDao(missing::connect).placeOrder(1, "Delivery", "Cash", 1, 0, 0, lines("30", "5")), "delivery");
        check(missing.rollbacks == 1 && missing.commits == 0, "missing delivery rate never falls back or commits");
        Store stock = new Store();
        stock.stock.put(20, 0);
        reject(() -> new OrderDao(stock::connect).placeOrder(1, "Walk-in", "Cash", 1, 0, 0, lines("25", "5")), "stock");
        check(stock.rollbacks == 1 && stock.total == null, "out-of-stock item cannot create partial order");
        Store containers = new Store();
        reject(() -> new OrderDao(containers::connect).placeOrder(1, "Walk-in", "Cash", 1, 0, 3, lines("25", "5")), "too many");
        check(containers.stock.get(20) == 10 && containers.gallons == 2, "invalid return restores earlier stock changes");
        Store archived = new Store();
        archived.customerActive = false;
        reject(() -> new OrderDao(archived::connect).placeOrder(1, "Walk-in", "Cash", 1, 0, 0, lines("25", "5")), "archived");
        check(archived.productReads == 0 && archived.rollbacks == 1, "archived customer rejected before product changes");
        for (boolean atCommit : List.of(false, true)) {
            Store failing = new Store();
            failing.failCommit = atCommit;
            failing.failLineInsert = !atCommit;
            try {
                new OrderDao(failing::connect).placeOrder(1, "Walk-in", "Cash", 1, 1, 0, lines("25", "5"));
                throw new AssertionError("expected database failure");
            } catch (SQLException expected) { check(expected.getMessage().contains("simulated"), "database error propagated"); }
            check(failing.commits == 0 && failing.rollbacks == 1 && failing.stock.get(20) == 10
                    && failing.total == null && failing.gallons == 2, "write or commit failure leaves no partial order");
        }
    }

    private static void malformedInputDoesNotConnect() throws Exception {
        Store store = new Store();
        OrderDao dao = new OrderDao(store::connect);
        reject(() -> dao.placeOrder(1, "Other", "Cash", 1, 0, 0, lines("25", "5")), "Walk-in or Delivery");
        reject(() -> dao.placeOrder(1, "Walk-in", "Other", 1, 0, 0, lines("25", "5")), "Cash or Credit");
        reject(() -> dao.placeOrder(1, "Walk-in", "Cash", 1, -1, 0, lines("25", "5")), "negative");
        reject(() -> dao.placeOrder(1, "Walk-in", "Cash", 1, 0, 0, List.of()), "at least one");
        check(store.opens == 0, "invalid order inputs fail before connecting");
    }

    private static List<OrderLine> lines(String refillPrice, String capPrice) {
        return List.of(OrderLine.priced(10, "Refill", 2, money(refillPrice)), OrderLine.priced(20, "Cap", 1, money(capPrice)));
    }
    private interface Action { void run() throws Exception; }
    private static void reject(Action action, String fragment) throws Exception {
        try { action.run(); throw new AssertionError("expected rejection"); }
        catch (IllegalArgumentException expected) { check(expected.getMessage().contains(fragment), expected.getMessage()); }
    }
    private static BigDecimal money(String value) { return new BigDecimal(value); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }

    private static final class Store {
        Map<Integer, Integer> stock = new LinkedHashMap<>(Map.of(10, 0, 20, 10));
        List<Map<Integer, Object>> savedLines = new ArrayList<>();
        int opens, commits, rollbacks, productReads, gallons = 2;
        BigDecimal total, deliveryPrice = money("30");
        String status;
        boolean customerActive = true, failCommit, failLineInsert;
        Connection connect() { opens++; return new Tx(this).connection(); }
    }

    private static final class Tx {
        final Store store;
        final Map<Integer, Integer> stock;
        final List<Map<Integer, Object>> savedLines = new ArrayList<>();
        int gallons;
        BigDecimal total;
        String status;
        boolean transaction, customerLocked;
        Tx(Store store) { this.store = store; stock = new LinkedHashMap<>(store.stock); gallons = store.gallons; }
        Connection connection() {
            return proxy(Connection.class, (p, method, args) -> {
                switch (method.getName()) {
                    case "setAutoCommit": transaction = !((Boolean) args[0]); return null;
                    case "prepareStatement": return statement((String) args[0]);
                    case "commit":
                        requireLock();
                        if (store.failCommit) throw new SQLException("simulated commit failure");
                        store.stock = stock; store.savedLines = savedLines; store.gallons = gallons;
                        store.total = total; store.status = status; store.commits++; return null;
                    case "rollback": store.rollbacks++; return null;
                    default: return zero(method.getReturnType());
                }
            });
        }
        void requireLock() {
            if (!transaction || !customerLocked) throw new AssertionError("customer lock required before order reads/writes");
        }
        PreparedStatement statement(String sql) {
            Map<Integer, Object> params = new LinkedHashMap<>();
            List<Map<Integer, Object>> batches = new ArrayList<>();
            return proxy(PreparedStatement.class, (p, method, args) -> {
                String name = method.getName();
                if (name.startsWith("set")) { params.put((Integer) args[0], args[1]); return null; }
                if (name.equals("executeQuery")) {
                    if (sql.contains("FROM customers")) {
                        if (!transaction || !sql.endsWith("FOR UPDATE")) throw new AssertionError("customer lock must be transactional");
                        customerLocked = true;
                        return rows(store.customerActive ? List.of(Map.of(1, 1)) : List.of());
                    }
                    requireLock();
                    if (sql.contains("FROM products")) {
                        if (!sql.endsWith("FOR UPDATE")) throw new AssertionError("product prices/stock must be locked");
                        store.productReads++;
                        int id = (Integer) params.get(1);
                        if (!stock.containsKey(id)) return rows(List.of());
                        Map<Object, Object> row = new LinkedHashMap<>();
                        row.put("product_name", id == 10 ? "Refill" : "Cap");
                        row.put("category", id == 10 ? Product.REFILL : Product.CAP_SEAL);
                        row.put("unit_price", money(id == 10 ? "25" : "5"));
                        row.put("delivery_price", id == 10 ? store.deliveryPrice : null);
                        row.put("stock_qty", stock.get(id)); row.put("is_active", true);
                        return rows(List.of(row));
                    }
                    if (sql.contains("FROM container_ledger")) return rows(List.of(Map.of(1, gallons)));
                    throw new AssertionError("Unexpected query: " + sql);
                }
                if (name.equals("executeUpdate")) {
                    requireLock();
                    if (sql.startsWith("UPDATE products")) {
                        int id = (Integer) params.get(2); stock.put(id, stock.get(id) - (Integer) params.get(1));
                    } else if (sql.startsWith("INSERT INTO orders")) {
                        total = (BigDecimal) params.get(4); status = (String) params.get(5);
                    } else if (sql.startsWith("INSERT INTO container_ledger")) {
                        gallons += ("Borrowed".equals(params.get(2)) ? 1 : -1) * (Integer) params.get(3);
                    } else throw new AssertionError("Unexpected update: " + sql);
                    return 1;
                }
                if (name.equals("addBatch")) { batches.add(new LinkedHashMap<>(params)); return null; }
                if (name.equals("executeBatch")) {
                    requireLock();
                    if (store.failLineInsert) throw new SQLException("simulated line insert failure");
                    savedLines.addAll(batches); return new int[batches.size()];
                }
                if (name.equals("getGeneratedKeys")) return rows(List.of(Map.of(1, 41)));
                return zero(method.getReturnType());
            });
        }
    }
    private static ResultSet rows(List<? extends Map<?, ?>> values) {
        int[] at = {-1};
        return proxy(ResultSet.class, (p, method, args) -> {
            if (method.getName().equals("next")) return ++at[0] < values.size();
            if (method.getName().startsWith("get")) return values.get(at[0]).get(args[0]);
            return zero(method.getReturnType());
        });
    }
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }
    private static Object zero(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }
}
