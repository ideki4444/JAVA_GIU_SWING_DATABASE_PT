package aquatrack.dao;

import aquatrack.db.Database;
import aquatrack.model.CustomerOrder;
import aquatrack.model.OrderLine;
import aquatrack.model.Product;
import aquatrack.model.User;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Opt-in integration checks against one dedicated, initially empty database.
 * Run from the repository root after creating the database with sql/schema-before-usability.sql.
 * Requires an explicit -Daquatrack.db.url=jdbc:mysql://127.0.0.1:3306/aquatrack_usability_test_20261022?... .
 * Never resets, truncates, drops or deletes data. A second run refuses the retained synthetic fixtures.
 */
public final class MySqlIntegrationTest {
    private static final String CATALOG = "aquatrack_usability_test_20261022";
    private static final String URL_PREFIX = "jdbc:mysql://127.0.0.1:3306/" + CATALOG;
    private static final String LOCK = "aquatrack_usability_test_20261022_integration";
    private static final Set<String> TABLES = Set.of("users", "customers", "products", "orders",
            "order_items", "payments", "container_ledger", "delivery_staff");
    private static int checks;

    public static void main(String[] args) throws Exception {
        String url = System.getProperty("aquatrack.db.url");
        if (url == null || !(url.equals(URL_PREFIX) || url.startsWith(URL_PREFIX + "?"))) {
            throw new IllegalStateException("Refusing integration writes: set aquatrack.db.url explicitly to " + URL_PREFIX);
        }
        try (Connection guard = Database.getConnection()) {
            requireCatalog(guard);
            boolean locked = false;
            try {
                check(number(guard, "SELECT GET_LOCK('" + LOCK + "', 0)") == 1,
                        "dedicated integration database is not in use by another test run");
                locked = true;
                assertEmptyBaseline(guard);
                seed(guard);
                migration(guard);
                pricingAndRollback();
                concurrentPaymentsAndReturns();
                accountEditingAndOwners();
                System.out.println("MySqlIntegrationTest passed: " + checks + " checks on " + CATALOG + ".");
                System.out.println("Synthetic fixtures retained for inspection; no production data was changed.");
            } finally {
                if (locked) number(guard, "SELECT RELEASE_LOCK('" + LOCK + "')");
            }
        }
    }

    private static void requireCatalog(Connection connection) throws SQLException {
        check(CATALOG.equals(connection.getCatalog()) && CATALOG.equals(text(connection, "SELECT DATABASE()")),
                "connection must resolve to the exact dedicated test catalog");
    }

    private static void assertEmptyBaseline(Connection connection) throws SQLException {
        Set<String> present = new HashSet<>();
        try (Statement query = connection.createStatement(); ResultSet rows = query.executeQuery(
                "SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'")) {
            while (rows.next()) present.add(rows.getString(1));
        }
        check(present.equals(TABLES), "test catalog must contain exactly the eight baseline tables");
        for (String table : TABLES) {
            check(number(connection, "SELECT COUNT(*) FROM " + table) == 0,
                    "refusing to overwrite nonempty test table " + table + "; prepare a fresh empty baseline first");
        }
        check(number(connection, "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                + "AND TABLE_NAME = 'products' AND COLUMN_NAME = 'delivery_price'") == 0,
                "test requires the pre-migration baseline; delivery_price must not already exist");
    }

    private static void seed(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try (Statement write = connection.createStatement()) {
            write.executeUpdate("INSERT INTO users (user_id, username, password_hash, full_name, role, is_active) VALUES "
                    + "(1, 'test_owner_one', 'synthetic-owner-one-hash', 'Synthetic Owner One', 'Owner/Admin', 1), "
                    + "(2, 'test_owner_two', 'synthetic-owner-two-hash', 'Synthetic Owner Two', 'Owner/Admin', 1), "
                    + "(3, 'test_staff', 'synthetic-staff-hash', 'Synthetic Staff', 'Staff', 1)");
            write.executeUpdate("INSERT INTO customers (customer_id, full_name, address, contact_no) VALUES "
                    + "(1, 'Synthetic Sales Customer', 'Test fixture address', '09170000001'), "
                    + "(2, 'Synthetic Balance Customer', 'Test fixture address', '09170000002')");
            write.executeUpdate("INSERT INTO products (product_id, product_name, category, unit_price, stock_qty, reorder_level, is_active) VALUES "
                    + "(1, 'Synthetic 5-gallon refill', 'Refill', 25.00, 0, 0, 1), "
                    + "(2, 'Synthetic archived 3-gallon refill', 'Refill', 15.00, 0, 0, 0), "
                    + "(3, 'Synthetic cap', 'Cap/Seal', 5.00, 20, 3, 1), "
                    + "(4, 'Synthetic new container', 'Container', 180.00, 10, 2, 1), "
                    + "(5, 'Synthetic credit fixture refill', 'Refill', 100.00, 0, 0, 1)");
            connection.commit();
        } catch (SQLException | RuntimeException failure) {
            connection.rollback();
            throw failure;
        } finally {
            connection.setAutoCommit(true);
        }
        check(number(connection, "SELECT COUNT(*) FROM users") == 3
                && number(connection, "SELECT COUNT(*) FROM customers") == 2
                && number(connection, "SELECT COUNT(*) FROM products") == 5, "synthetic baseline fixtures seeded");
    }

    private static void migration(Connection connection) throws Exception {
        runMigration(connection);
        equalMoney(decimal(connection, "SELECT delivery_price FROM products WHERE product_id = 1"), "25", "active refill backfilled");
        equalMoney(decimal(connection, "SELECT delivery_price FROM products WHERE product_id = 2"), "15", "archived refill backfilled");
        check(number(connection, "SELECT COUNT(*) FROM products WHERE product_id IN (3, 4) AND delivery_price IS NULL") == 2,
                "accessories keep a single price");
        equalMoney(decimal(connection, "SELECT unit_price FROM products WHERE product_id = 1"), "25", "original active pickup price preserved");
        equalMoney(decimal(connection, "SELECT unit_price FROM products WHERE product_id = 2"), "15", "original archived pickup price preserved");
        equalMoney(decimal(connection, "SELECT unit_price FROM products WHERE product_id = 3"), "5", "original cap price preserved");
        equalMoney(decimal(connection, "SELECT unit_price FROM products WHERE product_id = 4"), "180", "original container price preserved");
        new ProductDao().update(1, "Synthetic 5-gallon refill", Product.REFILL, money("25"), money("30"), 0);
        runMigration(connection);
        equalMoney(decimal(connection, "SELECT delivery_price FROM products WHERE product_id = 1"), "30",
                "rerunning migration preserves owner-configured delivery price");
        equalMoney(decimal(connection, "SELECT delivery_price FROM products WHERE product_id = 2"), "15",
                "rerunning migration keeps archived delivery price");
        check(number(connection, "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() "
                + "AND TABLE_NAME = 'products' AND CONSTRAINT_NAME = 'chk_products_delivery_price'") == 1,
                "migration creates exactly one delivery-price constraint");
        connection.setAutoCommit(false);
        try {
            boolean rejected = false;
            try (Statement invalid = connection.createStatement()) {
                invalid.executeUpdate("UPDATE products SET delivery_price = -1 WHERE product_id = 1");
            } catch (SQLException expected) {
                rejected = true;
            }
            check(rejected, "database rejects negative delivered prices");
        } finally {
            connection.rollback();
            connection.setAutoCommit(true);
        }
    }

    private static void runMigration(Connection connection) throws Exception {
        requireCatalog(connection);
        String source = Files.readString(Path.of("sql", "001_delivery_prices.sql"), StandardCharsets.UTF_8)
                .replaceAll("(?m)^\\s*--[^\\r\\n]*", "");
        try (Statement statement = connection.createStatement()) {
            for (String part : source.split(";")) {
                String sql = part.trim();
                if (!sql.isEmpty()) statement.execute(sql);
            }
        }
    }

    private static void pricingAndRollback() throws Exception {
        OrderDao orders = new OrderDao();
        int delivery = orders.placeOrder(1, "Delivery", "Cash", 1, 0, 0, List.of(
                line(1, "Synthetic 5-gallon refill", 2, "30"), line(3, "Synthetic cap", 2, "5")));
        int pickup = orders.placeOrder(1, "Walk-in", "Cash", 1, 0, 0, List.of(
                line(1, "Synthetic 5-gallon refill", 1, "25"), line(4, "Synthetic new container", 1, "180")));
        CustomerOrder delivered = orders.findById(delivery);
        CustomerOrder collected = orders.findById(pickup);
        equalMoney(delivered.getTotal(), "70", "actual delivery order uses delivered refill price plus ordinary cap price");
        equalMoney(collected.getTotal(), "205", "actual pickup order uses ordinary refill and container prices");
        check("Pending".equals(delivered.getStatus()) && "Delivered".equals(collected.getStatus()),
                "order service status matches delivery or pickup");
        try (Connection verify = Database.getConnection()) {
            requireCatalog(verify);
            check(number(verify, "SELECT stock_qty FROM products WHERE product_id = 3") == 18
                    && number(verify, "SELECT stock_qty FROM products WHERE product_id = 4") == 9
                    && number(verify, "SELECT stock_qty FROM products WHERE product_id = 1") == 0,
                    "orders deduct only tracked inventory");
        }
        new ProductDao().update(1, "Synthetic 5-gallon refill", Product.REFILL, money("26"), money("32"), 0);
        CustomerOrder historical = orders.findById(delivery);
        equalMoney(historical.getTotal(), "70", "catalog price edit leaves historic order total unchanged");
        equalMoney(historical.getLines().get(0).getUnitPrice(), "30", "catalog price edit leaves historic line unit price unchanged");
        equalMoney(historical.getLines().get(0).getLineTotal(), "60", "catalog price edit leaves historic line total unchanged");
        List<OrderLine> reviewed = null;
        try {
            // Take cap stock first, so the following refill-price change must roll that write back.
            orders.placeOrder(1, "Delivery", "Cash", 1, 0, 0, List.of(
                    line(3, "Synthetic cap", 3, "5"), line(1, "Synthetic 5-gallon refill", 1, "30")));
            throw new AssertionError("stale price should require review");
        } catch (OrderDao.PricesChangedException expected) {
            reviewed = expected.getUpdatedLines();
            check(reviewed.size() == 2, "price review returns the complete mixed cart");
            equalMoney(reviewed.get(1).getUnitPrice(), "32", "price review returns the current delivered price");
        }
        try (Connection verify = Database.getConnection()) {
            requireCatalog(verify);
            check(number(verify, "SELECT stock_qty FROM products WHERE product_id = 3") == 18,
                    "real transaction rolls back stock taken before price mismatch");
            check(number(verify, "SELECT COUNT(*) FROM orders") == 2
                    && number(verify, "SELECT COUNT(*) FROM order_items") == 4,
                    "price review creates no partial order or order items");
        }
        int retried = orders.placeOrder(1, "Delivery", "Cash", 1, 0, 0, reviewed);
        equalMoney(orders.findById(retried).getTotal(), "47", "explicit resubmission saves reviewed prices");
        try (Connection verify = Database.getConnection()) {
            requireCatalog(verify);
            check(number(verify, "SELECT stock_qty FROM products WHERE product_id = 3") == 15,
                    "explicit retry deducts stock once");
        }
    }

    private static void concurrentPaymentsAndReturns() throws Exception {
        new OrderDao().placeOrder(2, "Walk-in", "Credit", 1, 3, 0,
                List.of(line(5, "Synthetic credit fixture refill", 1, "100")));
        LedgerDao ledger = new LedgerDao();
        equalMoney(ledger.account(2).getUnpaid(), "100", "concurrency fixture owes exactly 100");
        check(ledger.account(2).getGallonsOut() == 3, "concurrency fixture has exactly three containers");
        LedgerDao.PaymentResult payment = (LedgerDao.PaymentResult) compete(
                () -> new LedgerDao().recordPayment(2, money("80")),
                () -> new LedgerDao().recordPayment(2, money("80")), "more than");
        equalMoney(payment.getAmount(), "80", "winning concurrent payment returns its actual committed amount");
        equalMoney(payment.getPreviousBalance(), "100", "winning concurrent payment reports the locked prior balance");
        equalMoney(payment.getRemainingBalance(), "20", "winning concurrent payment reports the committed remaining balance");
        check("Synthetic Balance Customer".equals(payment.getCustomerName()) && payment.getPaymentId() > 0
                && payment.getRecordedAt() != null, "payment acknowledgement identifies saved payment and customer");
        equalMoney(ledger.account(2).getUnpaid(), "20", "competing payments cannot overpay");
        try (Connection verify = Database.getConnection()) {
            requireCatalog(verify);
            check(number(verify, "SELECT COUNT(*) FROM payments WHERE customer_id = 2") == 1,
                    "only one competing payment was inserted");
        }
        compete(() -> { new LedgerDao().recordMovement(2, "Returned", 2); return null; },
                () -> { new LedgerDao().recordMovement(2, "Returned", 2); return null; }, "cannot be returned");
        check(ledger.account(2).getGallonsOut() == 1, "competing returns cannot exceed outstanding containers");
        try (Connection verify = Database.getConnection()) {
            requireCatalog(verify);
            check(number(verify, "SELECT COUNT(*) FROM container_ledger WHERE customer_id = 2 AND movement = 'Returned'") == 1,
                    "only one competing container return was inserted");
        }
    }

    private static void accountEditingAndOwners() throws Exception {
        UserDao accounts = new UserDao();
        User edited = accounts.save(1, 3, "test_staff_edited", "Synthetic Staff Edited", "Staff", true, null);
        check(edited.getId() == 3 && "synthetic-staff-hash".equals(edited.getPasswordHash()),
                "account detail edit returns the same identity and preserves its password");
        User stored = accounts.findByUsername("test_staff_edited");
        check(stored != null && stored.getId() == 3 && "Synthetic Staff Edited".equals(stored.getFullName())
                && "synthetic-staff-hash".equals(stored.getPasswordHash()), "account details persist without changing password");
        try (Connection verify = Database.getConnection()) {
            requireCatalog(verify);
            check(number(verify, "SELECT COUNT(*) FROM users") == 3, "editing does not insert an account");
        }
        compete(() -> new UserDao().save(1, 2, "test_owner_two", "Synthetic Owner Two", "Staff", true, null),
                () -> new UserDao().save(2, 1, "test_owner_one", "Synthetic Owner One", "Staff", true, null), "active Owner/Admin");
        List<User> remaining = accounts.findAll().stream().filter(user -> user.isActive() && user.isOwner()).toList();
        check(remaining.size() == 1, "competing owner reductions leave exactly one active owner");
        User owner = remaining.get(0);
        reject(() -> accounts.save(owner.getId(), owner.getId(), owner.getUsername(), owner.getFullName(), "Staff", true, null), "demote");
        reject(() -> accounts.save(owner.getId(), owner.getId(), owner.getUsername(), owner.getFullName(), "Owner/Admin", false, null), "turn off");
        check(accounts.findAll().stream().filter(user -> user.isActive() && user.isOwner()).count() == 1,
                "last owner remains active after rejected self-demotion and deactivation");
    }

    private record Outcome(Object value, Exception failure) { }

    /** Starts independent real JDBC operations together and requires one committed winner and one validation rejection. */
    private static Object compete(Callable<Object> first, Callable<Object> second, String expectedRejection) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "aquatrack-isolated-integration");
            thread.setDaemon(true);
            return thread;
        });
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Outcome>> pending = new ArrayList<>();
        try {
            for (Callable<Object> operation : List.of(first, second)) {
                pending.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("concurrency start timed out");
                    try { return new Outcome(operation.call(), null); }
                    catch (Exception failure) { return new Outcome(null, failure); }
                }));
            }
            check(ready.await(5, TimeUnit.SECONDS), "both competing operations are ready");
            start.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : pending) outcomes.add(future.get(25, TimeUnit.SECONDS));
            List<Outcome> successes = outcomes.stream().filter(result -> result.failure() == null).toList();
            check(successes.size() == 1, "exactly one concurrent operation commits; results: " + outcomes);
            Outcome loser = outcomes.stream().filter(result -> result.failure() != null).findFirst().orElseThrow();
            check(loser.failure() instanceof IllegalArgumentException
                    && loser.failure().getMessage().contains(expectedRejection),
                    "losing operation rechecks current state and rejects safely: " + loser.failure());
            return successes.get(0).value();
        } finally {
            start.countDown();
            pool.shutdownNow();
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) throw new IllegalStateException("integration workers did not stop");
        }
    }

    private static void reject(Callable<Object> action, String message) throws Exception {
        try { action.call(); throw new AssertionError("expected rejection containing " + message); }
        catch (IllegalArgumentException expected) { check(expected.getMessage().contains(message), expected.getMessage()); }
    }
    private static OrderLine line(int id, String name, int quantity, String price) {
        return OrderLine.priced(id, name, quantity, money(price));
    }
    private static BigDecimal money(String value) { return new BigDecimal(value); }
    private static void equalMoney(BigDecimal actual, String expected, String message) {
        check(actual != null && actual.compareTo(money(expected)) == 0, message + "; actual=" + actual);
    }
    private static long number(Connection connection, String sql) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(sql); ResultSet rows = query.executeQuery()) {
            if (!rows.next()) throw new SQLException("No result for verification query");
            return rows.getLong(1);
        }
    }
    private static String text(Connection connection, String sql) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(sql); ResultSet rows = query.executeQuery()) {
            if (!rows.next()) throw new SQLException("No result for verification query");
            return rows.getString(1);
        }
    }
    private static BigDecimal decimal(Connection connection, String sql) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(sql); ResultSet rows = query.executeQuery()) {
            if (!rows.next()) throw new SQLException("No result for verification query");
            return rows.getBigDecimal(1);
        }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
