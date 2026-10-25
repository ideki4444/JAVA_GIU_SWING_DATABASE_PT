package aquatrack.ui;

import aquatrack.dao.LedgerDao;
import aquatrack.util.PrintDocument;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/** No database/printer: verifies print data follows only successful, current report requests. */
public final class ReportPrintSnapshotTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        FakeSource source = new FakeSource();
        ReportsPanel[] holder = new ReportsPanel[1];
        SwingUtilities.invokeAndWait(() -> holder[0] = new ReportsPanel(source));
        ReportsPanel panel = holder[0];
        check(source.calls == 0, "Constructing a hidden report does not query data");
        SwingUtilities.invokeAndWait(panel::refresh);
        check(source.firstStarted.await(3, TimeUnit.SECONDS), "Daily request starts on a worker");
        SwingUtilities.invokeAndWait(() -> kind(panel).setSelectedIndex(1));
        await(() -> document(panel) != null, "Monthly result loads while old daily query remains blocked");
        PrintDocument newest = onEdt(() -> document(panel));
        check(newest.getTitle().startsWith("Monthly sales - "), "Snapshot title matches loaded selection");
        SwingUtilities.invokeAndWait(() -> {
            JTable table = field(panel, "table", JTable.class);
            check(table.getRowCount() == 12, "Monthly report fills all twelve months");
            for (int col = 0; col < table.getColumnCount(); col++) {
                JLabel label = (JLabel) table.prepareRenderer(table.getCellRenderer(0, col), 0, col);
                check(label.getHorizontalAlignment() == SwingConstants.CENTER, "Report column " + col + " is centered");
            }
        });
        source.releaseFirst.countDown();
        check(source.firstFinished.await(3, TimeUnit.SECONDS), "Cancelled slow source can eventually finish");
        SwingUtilities.invokeAndWait(() -> { });
        check(onEdt(() -> document(panel)) == newest, "Stale daily completion cannot replace the monthly print snapshot");

        source.failUnpaid = true;
        SwingUtilities.invokeAndWait(() -> {
            kind(panel).setSelectedIndex(3);
            check(document(panel) == null, "Starting a request immediately invalidates the old snapshot");
            check(!field(panel, "printButton", UiKit.ThemedButton.class).isEnabled(), "Preview disabled while loading");
        });
        await(() -> field(panel, "status", JLabel.class).getText().startsWith("Could not build"), "Failure is visible");
        check(onEdt(() -> document(panel)) == null, "Failed report cannot print stale data");
        source.failUnpaid = false;
        SwingUtilities.invokeAndWait(panel::refresh);
        await(() -> document(panel) != null, "Empty unpaid report still creates a snapshot");
        PrintDocument empty = onEdt(() -> document(panel));
        check(empty.getTitle().equals("Unpaid balances"), "Empty report has the correct title");
        check(empty.paginate(PrintDocument.a4(false)).getPageCount() == 1, "Empty report previews one page");
        check(!source.usedEventThread, "Every data source call ran outside the Swing event thread");
        System.out.println("ReportPrintSnapshotTest passed: " + checks + " checks.");
    }

    private static final class FakeSource implements ReportsPanel.ReportSource {
        final CountDownLatch firstStarted = new CountDownLatch(1);
        final CountDownLatch releaseFirst = new CountDownLatch(1);
        final CountDownLatch firstFinished = new CountDownLatch(1);
        volatile boolean failUnpaid, usedEventThread;
        volatile int calls;
        private void called() { calls++; usedEventThread |= SwingUtilities.isEventDispatchThread(); }
        public List<Object[]> dailySales(int year, int month) {
            called(); firstStarted.countDown();
            boolean interrupted = false;
            for (;;) {
                try { releaseFirst.await(); break; } catch (InterruptedException e) { interrupted = true; }
            }
            if (interrupted) Thread.currentThread().interrupt();
            firstFinished.countDown();
            return Collections.singletonList(new Object[] {LocalDate.of(year, month, 1), 2,
                    new BigDecimal("50.00"), new BigDecimal("25.00"), new BigDecimal("25.00")});
        }
        public List<Object[]> monthlySales(int year) {
            called();
            return Collections.singletonList(new Object[] {10, 42,
                    new BigDecimal("1250.00"), new BigDecimal("1000.00"), new BigDecimal("250.00")});
        }
        public List<Object[]> customerSales() { called(); return Collections.emptyList(); }
        public List<LedgerDao.UnpaidAccount> unpaidAccounts() throws SQLException {
            called();
            if (failUnpaid) throw new SQLException("Synthetic unavailable source");
            return Collections.emptyList();
        }
    }

    private static JComboBox<?> kind(ReportsPanel panel) { return field(panel, "kindBox", JComboBox.class); }
    private static PrintDocument document(ReportsPanel panel) { return field(panel, "loadedDocument", PrintDocument.class); }
    private static <T> T field(Object owner, String name, Class<T> type) {
        try { Field field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return type.cast(field.get(owner)); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private interface Read<T> { T get(); }
    private static <T> T onEdt(Read<T> read) throws Exception {
        Object[] result = new Object[1];
        SwingUtilities.invokeAndWait(() -> result[0] = read.get());
        @SuppressWarnings("unchecked") T value = (T) result[0];
        return value;
    }
    private static void await(BooleanSupplier ready, String description) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (!onEdt(ready::getAsBoolean) && System.nanoTime() < end) Thread.sleep(10);
        check(onEdt(ready::getAsBoolean), description);
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
