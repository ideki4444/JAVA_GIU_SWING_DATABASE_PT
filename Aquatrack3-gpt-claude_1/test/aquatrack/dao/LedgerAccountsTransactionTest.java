package aquatrack.dao;

import aquatrack.model.User;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/** Database-free transactional JDBC doubles: validation, rollback and serialized competing writes. */
public final class LedgerAccountsTransactionTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        paymentValidation();
        paymentCommitAndRollback();
        concurrentPaymentsAndReturns();
        accountRules();
        concurrentOwners();
        System.out.println("LedgerAccountsTransactionTest passed: " + checks + " checks.");
    }

    private static void paymentValidation() throws Exception {
        Store store = new Store();
        LedgerDao dao = new LedgerDao(store::connect);
        reject(() -> dao.recordPayment(7, null), "number");
        reject(() -> dao.recordPayment(7, BigDecimal.ZERO), "more than zero");
        reject(() -> dao.recordPayment(7, new BigDecimal("-1")), "negative");
        reject(() -> dao.recordPayment(7, new BigDecimal("1.001")), "decimal");
        reject(() -> dao.recordMovement(7, "Unknown", 1), "Returned or Borrowed");
        reject(() -> dao.recordMovement(7, "Returned", 0), "more than zero");
        check(store.opens == 0, "invalid arguments must be rejected before opening a connection");
        reject(() -> dao.recordPayment(7, new BigDecimal("101")), "more than");
        reject(() -> dao.recordMovement(7, "Returned", 3), "cannot be returned");
        check(store.rollbacks == 2 && store.commits == 0, "invalid current balances roll back");
        store.archived = true;
        reject(() -> dao.recordPayment(7, BigDecimal.ONE), "archived");
        check(store.balance.compareTo(new BigDecimal("100")) == 0, "archived customer cannot receive a payment");
    }

    private static void paymentCommitAndRollback() throws Exception {
        Store store = new Store();
        LedgerDao dao = new LedgerDao(store::connect);
        LedgerDao.PaymentResult result = dao.recordPayment(7, new BigDecimal("40"));
        check(store.commits == 1, "result is returned after commit");
        check(result.getPaymentId() > 0 && "Test Customer".equals(result.getCustomerName()), "result identifies persisted payment and customer");
        check(result.getAmount().equals(new BigDecimal("40.00")), "result has precise amount");
        check(result.getPreviousBalance().compareTo(new BigDecimal("100")) == 0
                && result.getRemainingBalance().compareTo(new BigDecimal("60")) == 0, "result contains transactional balances");
        check(result.getRecordedAt().equals(Store.TIME), "result uses database timestamp");
        store.failCommit = true;
        try { dao.recordPayment(7, new BigDecimal("10")); throw new AssertionError("expected commit failure"); }
        catch (SQLException expected) { check(expected.getMessage().contains("commit"), "commit error propagated"); }
        check(store.balance.compareTo(new BigDecimal("60")) == 0 && store.rollbacks == 1, "failed payment leaves balance unchanged");
        store.failCommit = false;
        dao.recordPayment(7, new BigDecimal("60"));
        reject(() -> dao.recordPayment(7, BigDecimal.ONE), "no unpaid");
        check(store.balance.signum() == 0, "full settlement never creates negative balance");
    }

    private static void concurrentPaymentsAndReturns() throws Exception {
        Store payments = new Store();
        LedgerDao dao = new LedgerDao(payments::connect);
        AtomicInteger ok = compete(() -> dao.recordPayment(7, new BigDecimal("60")), () -> dao.recordPayment(7, new BigDecimal("60")));
        check(ok.get() == 1 && payments.balance.compareTo(new BigDecimal("40")) == 0, "two payments recheck serialized balance");
        Store returns = new Store();
        LedgerDao second = new LedgerDao(returns::connect);
        ok = compete(() -> second.recordMovement(7, "Returned", 2), () -> second.recordMovement(7, "Returned", 2));
        check(ok.get() == 1 && returns.gallons == 0, "two returns cannot exceed customer containers");
        second.recordMovement(7, "Borrowed", 3);
        check(returns.gallons == 3, "borrow and return share customer serialization");
    }

    private static void accountRules() throws Exception {
        Store store = new Store();
        UserDao dao = new UserDao(store::connect);
        User updated = dao.save(1, 1, "owner", "Owner Renamed", "Owner/Admin", true, null);
        check(updated.getFullName().equals("Owner Renamed") && updated.getPasswordHash().equals("old-hash"), "profile update preserves hash and excludes current identity from duplicate check");
        reject(() -> dao.save(1, 3, "owner", "Staff Three", "Staff", true, null), "username");
        reject(() -> dao.save(1, 3, "staff", "  OWNER   RENAMED ", "Staff", true, null), "full name");
        reject(() -> dao.save(1, 1, "owner", "Owner Renamed", "Staff", true, null), "demote");
        reject(() -> dao.save(1, 1, "owner", "Owner Renamed", "Owner/Admin", false, null), "turn off");
        reject(() -> dao.save(3, 2, "owner2", "Owner Two", "Staff", true, null), "active Owner/Admin");
        reject(() -> dao.save(99, 2, "owner2", "Owner Two", "Staff", true, null), "active Owner/Admin");
        User created = dao.save(1, -1, "new_staff", "New Staff", "Staff", true, "new-hash");
        check(created.getId() > 3 && store.users.containsKey(created.getId()), "new account returns committed identity");
        User reset = dao.resetPassword(1, 3, "changed-hash");
        check(reset.getPasswordHash().equals("changed-hash") && reset.getFullName().equals("Staff Three"), "password reset is separate from profile save");
        store.failCommit = true;
        try { dao.save(1, 3, "staff", "Should Not Persist", "Staff", true, null); throw new AssertionError("expected failed save"); }
        catch (SQLException expected) { check(store.users.get(3).getFullName().equals("Staff Three"), "failed profile commit rolls back"); }
        store.failCommit = false;
        dao.save(1, 2, "owner2", "Owner Two", "Owner/Admin", false, null);
        reject(() -> dao.resetPassword(2, 3, "invalid-change"), "active Owner/Admin");
        check(store.users.get(3).getPasswordHash().equals("changed-hash"), "inactive actor cannot reset passwords");
    }

    private static void concurrentOwners() throws Exception {
        Store store = new Store();
        UserDao dao = new UserDao(store::connect);
        AtomicInteger ok = compete(
                () -> dao.save(1, 2, "owner2", "Owner Two", "Staff", true, null),
                () -> dao.save(2, 1, "owner", "Owner One", "Staff", true, null));
        long owners = store.users.values().stream().filter(u -> u.isOwner() && u.isActive()).count();
        check(ok.get() == 1 && owners == 1, "competing owners cannot demote the last active owner");
        check(store.ownerLockQueries == 2, "all account mutations use deterministic user_id locking");
    }

    private interface Action { void run() throws Exception; }
    private static AtomicInteger compete(Action first, Action second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        List<Throwable> failures = java.util.Collections.synchronizedList(new ArrayList<>());
        Thread[] threads = new Thread[2];
        Action[] actions = {first, second};
        for (int i = 0; i < 2; i++) {
            final Action action = actions[i];
            threads[i] = new Thread(() -> {
                try { start.await(); action.run(); ok.incrementAndGet(); }
                catch (IllegalArgumentException expected) { /* one legitimate stale request must lose */ }
                catch (Throwable e) { failures.add(e); }
            });
            threads[i].start();
        }
        start.countDown();
        for (Thread thread : threads) { thread.join(5000); check(!thread.isAlive(), "transaction must finish without deadlock"); }
        if (!failures.isEmpty()) throw new AssertionError("concurrent operation failed unexpectedly", failures.get(0));
        return ok;
    }
    private static void reject(Action action, String message) throws Exception {
        try { action.run(); throw new AssertionError("Expected rejection containing " + message); }
        catch (IllegalArgumentException expected) { check(expected.getMessage().contains(message), expected.getMessage()); }
    }
    private static synchronized void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }

    /** The double enforces lock-before-read, transaction staging and serialized snapshots, not SQL database semantics. */
    private static final class Store {
        static final LocalDateTime TIME = LocalDateTime.of(2026, 10, 21, 14, 35);
        final ReentrantLock lock = new ReentrantLock(true);
        Map<Integer, User> users = new LinkedHashMap<>();
        BigDecimal balance = new BigDecimal("100");
        int gallons = 2, nextId = 10, opens, commits, rollbacks, ownerLockQueries;
        boolean archived, failCommit;
        Store() {
            users.put(1, new User(1, "owner", "Owner One", "Owner/Admin", "old-hash", true));
            users.put(2, new User(2, "owner2", "Owner Two", "Owner/Admin", "old-hash", true));
            users.put(3, new User(3, "staff", "Staff Three", "Staff", "old-hash", true));
        }
        synchronized Connection connect() { opens++; return new Tx(this).connection(); }
    }
    private static final class Tx {
        final Store store;
        boolean transaction, locked;
        BigDecimal balance;
        int gallons, generated;
        Map<Integer, User> users;
        Tx(Store store) { this.store = store; }
        Connection connection() {
            return proxy(Connection.class, (p, method, args) -> {
                switch (method.getName()) {
                    case "setAutoCommit": transaction = !((Boolean) args[0]); return null;
                    case "prepareStatement": return statement((String) args[0]);
                    case "commit":
                        requireLock();
                        if (store.failCommit) throw new SQLException("Simulated commit failure");
                        store.balance = balance; store.gallons = gallons; store.users = users; store.commits++; unlock(); return null;
                    case "rollback": store.rollbacks++; unlock(); return null;
                    case "close": unlock(); return null;
                    case "isClosed": return false;
                    default: return zero(method.getReturnType());
                }
            });
        }
        void acquire() {
            check(transaction, "FOR UPDATE must run with autocommit disabled");
            store.lock.lock(); locked = true;
            balance = store.balance; gallons = store.gallons; users = new LinkedHashMap<>(store.users);
        }
        void requireLock() { check(locked && transaction, "read/write must use the same locked transaction"); }
        void unlock() { if (locked) { locked = false; store.lock.unlock(); } }
        PreparedStatement statement(String sql) {
            Map<Integer, Object> params = new LinkedHashMap<>();
            return proxy(PreparedStatement.class, (p, method, args) -> {
                String name = method.getName();
                if (name.startsWith("set")) { params.put((Integer) args[0], args[1]); return null; }
                if (name.equals("executeQuery")) {
                    if (sql.contains("FROM users") && sql.endsWith("FOR UPDATE")) {
                        check(sql.endsWith("ORDER BY user_id FOR UPDATE"), "owners lock in deterministic order");
                        acquire(); store.ownerLockQueries++;
                        List<Object[]> rows = new ArrayList<>();
                        for (User user : users.values()) rows.add(row(user));
                        return rows(rows);
                    }
                    if (sql.contains("FROM customers") && sql.endsWith("FOR UPDATE")) {
                        acquire(); return rows(store.archived ? new ArrayList<>() : Arrays.asList(new Object[][] {{7}}));
                    }
                    if (sql.startsWith("SELECT c.customer_id")) {
                        requireLock(); return rows(Arrays.asList(new Object[][] {{7, "Test Customer", "09123456789", gallons, balance, false}}));
                    }
                    if (sql.equals("SELECT CURRENT_TIMESTAMP")) { requireLock(); return rows(Arrays.asList(new Object[][] {{Timestamp.valueOf(Store.TIME)}})); }
                    throw new AssertionError("Unexpected query: " + sql);
                }
                if (name.equals("executeUpdate")) {
                    requireLock();
                    if (sql.startsWith("INSERT INTO payments")) { balance = balance.subtract((BigDecimal) params.get(2)); generated = ++store.nextId; }
                    else if (sql.startsWith("INSERT INTO container_ledger")) gallons += ("Borrowed".equals(params.get(2)) ? 1 : -1) * (Integer) params.get(3);
                    else if (sql.startsWith("INSERT INTO users")) {
                        generated = ++store.nextId;
                        users.put(generated, new User(generated, (String) params.get(1), (String) params.get(3), (String) params.get(4), (String) params.get(2), (Boolean) params.get(5)));
                    } else if (sql.startsWith("UPDATE users SET username")) {
                        int id = (Integer) params.get(5); User old = users.get(id);
                        users.put(id, new User(id, (String) params.get(1), (String) params.get(2), (String) params.get(3), old.getPasswordHash(), (Boolean) params.get(4)));
                    } else if (sql.startsWith("UPDATE users SET password_hash")) {
                        int id = (Integer) params.get(2); User old = users.get(id);
                        users.put(id, new User(id, old.getUsername(), old.getFullName(), old.getRole(), (String) params.get(1), old.isActive()));
                    } else throw new AssertionError("Unexpected write: " + sql);
                    return 1;
                }
                if (name.equals("getGeneratedKeys")) return rows(Arrays.asList(new Object[][] {{generated}}));
                return zero(method.getReturnType());
            });
        }
    }
    private static Object[] row(User user) {
        return new Object[] {user.getId(), user.getUsername(), user.getFullName(), user.getRole(), user.getPasswordHash(), user.isActive()};
    }
    private static ResultSet rows(List<Object[]> values) {
        int[] cursor = {-1};
        return proxy(ResultSet.class, (p, method, args) -> {
            if (method.getName().equals("next")) return ++cursor[0] < values.size();
            if (method.getName().startsWith("get")) return values.get(cursor[0])[(Integer) args[0] - 1];
            return zero(method.getReturnType());
        });
    }
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }
    private static Object zero(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        return null;
    }
}
