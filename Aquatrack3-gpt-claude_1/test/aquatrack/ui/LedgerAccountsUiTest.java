package aquatrack.ui;

import aquatrack.dao.LedgerDao.PaymentResult;
import aquatrack.model.CustomerAccount;
import aquatrack.model.User;
import aquatrack.util.PasswordUtil;
import aquatrack.util.Session;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPasswordField;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.TableRowSorter;

/** Headless Swing workflows using injected data and dialogs; never opens a database. */
public final class LedgerAccountsUiTest {
    private static int checks;
    private static final User OWNER = new User(1, "owner", "Owner One", "Owner/Admin", "hash");
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        edt(() -> Session.start(OWNER));
        accountSelectionAndSave();
        accountSaveDiscardCancel();
        staleAccountReload();
        paymentConfirmationAndFailure();
        fullyPaidAndRefreshFailure();
        staleBalanceReload();
        edt(Session::end);
        System.out.println("LedgerAccountsUiTest passed: " + checks + " checks.");
    }

    private static void accountSelectionAndSave() throws Exception {
        AccountData data = new AccountData();
        AccountDialogs dialogs = new AccountDialogs();
        AccountsPanel[] box = new AccountsPanel[1];
        edt(() -> { box[0] = new AccountsPanel(data, dialogs); box[0].refresh(); });
        AccountsPanel panel = box[0];
        await(() -> bool(panel, "recordsLoaded"));
        edt(() -> {
            JTable table = field(panel, "table", JTable.class);
            TableRowSorter<?> sorter = new TableRowSorter<>(table.getModel());
            table.setRowSorter(sorter);
            sorter.toggleSortOrder(0); sorter.toggleSortOrder(0);
            table.setRowSelectionInterval(0, 0);
            check(text(panel, "usernameField").equals("staff"), "sorted selection maps to correct account identity");
            check(text(panel, "fullNameField").equals("Staff Three"), "selection immediately populates editable details");
            check(!field(panel, "passwordField", JPasswordField.class).isVisible(), "profile form never exposes password input");
            field(panel, "fullNameField", JTextField.class).setText("Updated Staff");
            data.saveGate = new CountDownLatch(1);
            field(panel, "saveButton", JButton.class).doClick();
            field(panel, "saveButton", JButton.class).doClick();
        });
        await(() -> data.saves.get() == 1);
        check(data.onBackground, "account reads/writes stay off the EDT");
        edt(() -> check(!field(panel, "table", JTable.class).isEnabled(), "selection disabled during save"));
        data.saveGate.countDown();
        await(() -> !bool(panel, "busy") && bool(panel, "recordsLoaded"));
        check(data.saves.get() == 1 && data.lastSaved.getId() == 3, "duplicate save prevented and sorted target preserved");
        check(data.lastHash == null && data.resets.get() == 0, "profile save does not reset password");
        AtomicInteger changed = new AtomicInteger();
        Runnable listener = () -> { check(SwingUtilities.isEventDispatchThread(), "session listener runs on EDT"); changed.incrementAndGet(); };
        Session.addChangeListener(listener);
        edt(() -> {
            JTable table = field(panel, "table", JTable.class);
            int ownerView = table.convertRowIndexToView(0);
            table.setRowSelectionInterval(ownerView, ownerView);
            check(!field(panel, "roleBox", JComboBox.class).isEnabled(), "self-demotion disabled in UI");
            field(panel, "fullNameField", JTextField.class).setText("Updated Owner");
            field(panel, "saveButton", JButton.class).doClick();
        });
        await(() -> "Updated Owner".equals(Session.user().getFullName()));
        check(changed.get() == 1, "saved signed-in details refresh session once");
        Session.removeChangeListener(listener);
        await(() -> bool(panel, "recordsLoaded"));
        edt(() -> {
            field(panel, "newButton", JButton.class).doClick();
            check(text(panel, "usernameField").isEmpty() && field(panel, "passwordField", JPasswordField.class).isVisible(), "New account clears fields and shows initial password");
            field(panel, "usernameField", JTextField.class).setText("newuser");
            field(panel, "fullNameField", JTextField.class).setText("New User");
            field(panel, "passwordField", JPasswordField.class).setText("test-pass");
            field(panel, "saveButton", JButton.class).doClick();
        });
        await(() -> data.saves.get() == 3 && !bool(panel, "busy"));
        check(data.lastId == -1 && PasswordUtil.verify("test-pass", data.lastHash), "new account hashes its initial password off EDT");
    }

    private static void accountSaveDiscardCancel() throws Exception {
        AccountData data = new AccountData();
        AccountDialogs dialogs = new AccountDialogs();
        AccountsPanel[] box = new AccountsPanel[1];
        edt(() -> { box[0] = new AccountsPanel(data, dialogs); box[0].refresh(); });
        AccountsPanel panel = box[0];
        await(() -> bool(panel, "recordsLoaded"));
        edt(() -> {
            JTable table = field(panel, "table", JTable.class);
            table.setRowSelectionInterval(0, 0);
            field(panel, "fullNameField", JTextField.class).setText("Unsaved Name");
            dialogs.choice = 2;
            table.setRowSelectionInterval(1, 1);
            check(table.getSelectedRow() == 0 && text(panel, "fullNameField").equals("Unsaved Name"), "Cancel preserves row and edits");
            dialogs.choice = 1;
            table.setRowSelectionInterval(1, 1);
            check(text(panel, "fullNameField").equals("Staff Three"), "Discard selects requested account");
            field(panel, "fullNameField", JTextField.class).setText("Saved Before Leave");
            dialogs.choice = 0;
            AtomicInteger exits = dialogs.exits;
            check(!panel.requestLeave(exits::incrementAndGet), "Save defers navigation");
        });
        await(() -> dialogs.exits.get() == 1);
        check(data.saves.get() == 1, "Save navigation callback occurs once after persistence");
        await(() -> bool(panel, "recordsLoaded"));
        edt(() -> {
            field(panel, "fullNameField", JTextField.class).setText("Saved Before Reset");
            dialogs.password = "reset-pass";
            field(panel, "resetButton", JButton.class).doClick();
        });
        await(() -> data.resets.get() == 1 && !bool(panel, "busy"));
        check(data.saves.get() == 2 && PasswordUtil.verify("reset-pass", data.lastResetHash), "dirty Save then separate reset both complete");
        edt(() -> {
            JTable table = field(panel, "table", JTable.class);
            check("Saved Before Reset".equals(table.getValueAt(table.getSelectedRow(), 1)),
                    "saved profile appears in table even when a password reset delays reload");
            field(panel, "fullNameField", JTextField.class).setText("Keep After Failure");
            data.failSave = true;
            check(!panel.requestLeave(dialogs.exits::incrementAndGet), "failed save still defers leaving");
        });
        await(() -> !bool(panel, "busy") && !dialogs.problems.isEmpty());
        edt(() -> check(text(panel, "fullNameField").equals("Keep After Failure"), "save failure preserves input"));
        check(dialogs.exits.get() == 1, "failed save does not invoke navigation callback");
    }

    private static void staleAccountReload() throws Exception {
        AccountData data = new AccountData();
        data.firstLoadGate = new CountDownLatch(1);
        AccountsPanel[] box = new AccountsPanel[1];
        edt(() -> { box[0] = new AccountsPanel(data, new AccountDialogs()); box[0].refresh(); });
        await(() -> data.loads.get() == 1);
        data.rows = Arrays.asList(new User(9, "fresh", "Fresh Account", "Staff", "hash"));
        edt(box[0]::refresh);
        await(() -> bool(box[0], "recordsLoaded"));
        data.firstLoadGate.countDown();
        Thread.sleep(100);
        edt(() -> check(field(box[0], "table", JTable.class).getValueAt(0, 0).equals("fresh"), "old account reload cannot overwrite newer data"));
    }

    private static void paymentConfirmationAndFailure() throws Exception {
        BalanceData data = new BalanceData();
        BalanceDialogs dialogs = new BalanceDialogs();
        ContainersPanel[] box = new ContainersPanel[1];
        edt(() -> { box[0] = new ContainersPanel(data, dialogs); box[0].refresh(); });
        ContainersPanel panel = box[0];
        await(() -> bool(panel, "recordsLoaded"));
        edt(() -> {
            JTable table = field(panel, "table", JTable.class);
            TableRowSorter<?> sorter = new TableRowSorter<>(table.getModel());
            table.setRowSorter(sorter); sorter.toggleSortOrder(0); sorter.toggleSortOrder(0);
            table.setRowSelectionInterval(0, 0);
            field(panel, "paymentField", JTextField.class).setText("40.00");
            data.paymentGate = new CountDownLatch(1);
            field(panel, "payButton", JButton.class).doClick();
            field(panel, "payButton", JButton.class).doClick();
        });
        await(() -> data.payments.get() == 1);
        check(dialogs.confirmations.get() == 0, "no payment confirmation before successful DAO result");
        check(data.onBackground, "balance reads/writes run off EDT");
        data.paymentGate.countDown();
        await(() -> dialogs.confirmations.get() == 1 && bool(panel, "recordsLoaded"));
        check(data.lastId == 8, "payment uses selected sorted customer's stable identity");
        check(dialogs.message.contains("40.00") && dialogs.message.contains("60.00") && dialogs.message.contains("Payment reference"), "confirmation shows amount, balance and reference");
        edt(() -> {
            check(text(panel, "paymentField").isEmpty(), "committed payment clears entry");
            field(panel, "paymentField", JTextField.class).setText("10.00");
            data.failPayment = true;
            field(panel, "payButton", JButton.class).doClick();
        });
        await(() -> !bool(panel, "busy") && !dialogs.problems.isEmpty());
        check(dialogs.confirmations.get() == 1, "failed payment shows no success popup");
        edt(() -> check(text(panel, "paymentField").equals("10.00"), "failed payment retains amount for correction"));
    }

    private static void staleBalanceReload() throws Exception {
        BalanceData data = new BalanceData(); data.firstLoadGate = new CountDownLatch(1);
        ContainersPanel[] box = new ContainersPanel[1];
        edt(() -> { box[0] = new ContainersPanel(data, new BalanceDialogs()); box[0].refresh(); });
        await(() -> data.loads.get() == 1);
        data.rows = Arrays.asList(new CustomerAccount(10, "Fresh Balance", "09111111111", 1, BigDecimal.ONE));
        edt(box[0]::refresh);
        await(() -> bool(box[0], "recordsLoaded"));
        data.firstLoadGate.countDown(); Thread.sleep(100);
        edt(() -> check(field(box[0], "table", JTable.class).getValueAt(0, 0).equals("Fresh Balance"), "old balance reload cannot overwrite newer data"));
    }

    private static void fullyPaidAndRefreshFailure() throws Exception {
        BalanceData data = new BalanceData();
        BalanceDialogs dialogs = new BalanceDialogs();
        ContainersPanel[] box = new ContainersPanel[1];
        edt(() -> { box[0] = new ContainersPanel(data, dialogs); box[0].refresh(); });
        ContainersPanel panel = box[0];
        await(() -> bool(panel, "recordsLoaded"));
        edt(() -> {
            field(panel, "table", JTable.class).setRowSelectionInterval(1, 1);
            field(panel, "paymentField", JTextField.class).setText("100.00");
            data.failLoads = true;
            field(panel, "payButton", JButton.class).doClick();
        });
        await(() -> dialogs.confirmations.get() == 1 && !dialogs.problems.isEmpty());
        check(dialogs.message.contains("Fully paid"), "full settlement explicitly confirms Fully paid");
        check(dialogs.problems.get(0).contains("was saved")
                && dialogs.problems.get(0).contains("Do not record this transaction again"),
                "refresh failure distinguishes committed payment from save failure");
        edt(() -> {
            check(text(panel, "paymentField").isEmpty(), "refresh failure does not restore a committed amount");
            check(!field(panel, "payButton", JButton.class).isEnabled(), "stale balance disables a second payment");
            check(field(panel, "status", JLabel.class).getText().contains("was saved"), "saved payment context remains visible after refresh failure");
            data.failLoads = false;
            panel.refresh();
        });
        await(() -> bool(panel, "recordsLoaded"));
        check(data.payments.get() == 1 && dialogs.confirmations.get() == 1, "refresh retry never repeats payment or confirmation");
    }

    private static final class AccountData implements AccountsPanel.Data {
        volatile List<User> rows = Arrays.asList(OWNER, new User(3, "staff", "Staff Three", "Staff", "old-hash"));
        final AtomicInteger loads = new AtomicInteger(), saves = new AtomicInteger(), resets = new AtomicInteger();
        volatile CountDownLatch saveGate, firstLoadGate;
        volatile User lastSaved;
        volatile String lastHash, lastResetHash;
        volatile int lastId;
        volatile boolean failSave, onBackground = true;
        @Override public List<User> findAll() throws SQLException {
            onBackground &= !SwingUtilities.isEventDispatchThread();
            List<User> snapshot = new ArrayList<>(rows);
            if (loads.incrementAndGet() == 1) block(firstLoadGate);
            return snapshot;
        }
        @Override public User save(int actor, int id, String username, String name, String role, boolean active, String hash) throws SQLException {
            onBackground &= !SwingUtilities.isEventDispatchThread();
            saves.incrementAndGet(); block(saveGate);
            if (failSave) throw new SQLException("Simulated save failure");
            lastId = id; lastHash = hash;
            lastSaved = new User(id < 0 ? 8 : id, username, name, role, hash == null ? "old-hash" : hash, active);
            List<User> changed = new ArrayList<>(rows); changed.removeIf(u -> u.getId() == lastSaved.getId()); changed.add(lastSaved);
            changed.sort(java.util.Comparator.comparing(User::getUsername)); rows = changed;
            return lastSaved;
        }
        @Override public User resetPassword(int actor, int id, String hash) {
            resets.incrementAndGet(); lastResetHash = hash;
            return rows.stream().filter(u -> u.getId() == id).findFirst().orElseThrow();
        }
    }
    private static final class AccountDialogs implements AccountsPanel.Dialogs {
        volatile int choice = 2;
        volatile String password;
        final AtomicInteger exits = new AtomicInteger();
        final List<String> problems = new java.util.concurrent.CopyOnWriteArrayList<>();
        @Override public int unsaved() { check(SwingUtilities.isEventDispatchThread(), "unsaved dialog on EDT"); return choice; }
        @Override public String newPassword(String username) { return password; }
        @Override public void problem(String message) { problems.add(message); }
    }
    private static final class BalanceData implements ContainersPanel.Data {
        volatile List<CustomerAccount> rows = Arrays.asList(
                new CustomerAccount(7, "Alpha", "09111111111", 2, new BigDecimal("100")),
                new CustomerAccount(8, "Zulu", "09222222222", 1, new BigDecimal("100")));
        final AtomicInteger loads = new AtomicInteger(), payments = new AtomicInteger();
        volatile CountDownLatch paymentGate, firstLoadGate;
        volatile boolean failPayment, failLoads, onBackground = true;
        volatile int lastId;
        @Override public List<CustomerAccount> accounts() throws SQLException {
            onBackground &= !SwingUtilities.isEventDispatchThread();
            List<CustomerAccount> snapshot = new ArrayList<>(rows);
            if (loads.incrementAndGet() == 1) block(firstLoadGate);
            if (failLoads) throw new SQLException("Simulated refresh failure");
            return snapshot;
        }
        @Override public PaymentResult recordPayment(int id, BigDecimal amount) throws SQLException {
            onBackground &= !SwingUtilities.isEventDispatchThread();
            payments.incrementAndGet(); lastId = id; block(paymentGate);
            if (failPayment) throw new SQLException("Simulated payment failure");
            return new PaymentResult(20, "Zulu", amount, new BigDecimal("100"), new BigDecimal("100").subtract(amount), LocalDateTime.of(2026, 10, 21, 14, 35));
        }
        @Override public void recordMovement(int id, String movement, int quantity) { }
    }
    private static final class BalanceDialogs implements ContainersPanel.Dialogs {
        final AtomicInteger confirmations = new AtomicInteger();
        final List<String> problems = new java.util.concurrent.CopyOnWriteArrayList<>();
        volatile String message;
        @Override public void payment(PaymentResult result) {
            check(SwingUtilities.isEventDispatchThread(), "payment confirmation on EDT");
            message = ContainersPanel.paymentMessage(result); confirmations.incrementAndGet();
        }
        @Override public void problem(String message) { problems.add(message); }
    }
    private static void block(CountDownLatch gate) throws SQLException {
        if (gate == null) return;
        try { if (!gate.await(5, TimeUnit.SECONDS)) throw new SQLException("Test timed out"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new SQLException(e); }
    }
    private static void await(BooleanSupplier ready) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(7);
        while (System.nanoTime() < deadline) {
            boolean[] result = {false}; edt(() -> result[0] = ready.getAsBoolean());
            if (result[0]) return; Thread.sleep(15);
        }
        throw new AssertionError("Timed out waiting for background operation");
    }
    private static boolean bool(Object target, String name) { return field(target, name, Boolean.class); }
    private static String text(Object target, String name) { return field(target, name, JTextField.class).getText(); }
    private static <T> T field(Object target, String name, Class<T> type) {
        try { Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return type.cast(field.get(target)); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private static void edt(Runnable action) throws Exception { SwingUtilities.invokeAndWait(action); }
    private static synchronized void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
