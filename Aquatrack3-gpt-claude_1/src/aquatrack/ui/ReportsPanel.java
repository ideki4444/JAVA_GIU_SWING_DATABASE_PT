package aquatrack.ui;

import aquatrack.dao.LedgerDao;
import aquatrack.dao.ReportDao;
import aquatrack.model.CustomerAccount;
import aquatrack.model.CustomerTotal;
import aquatrack.util.Fmt;
import aquatrack.util.PrintDocument;
import aquatrack.util.Ranking;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

/** Reports for the Owner: daily and monthly sales, best customers, and unpaid balances. */
public final class ReportsPanel extends JPanel implements MainFrame.Screen {

    private static final String[] KINDS = {"Daily sales", "Monthly sales", "Best customers", "Unpaid balances"};
    private static final String[][] COLUMNS = {
        {"Date", "Orders", "Total sales", "Cash", "Credit"},
        {"Month", "Orders", "Total sales", "Cash", "Credit"},
        {"Rank", "Customer", "Contact", "Orders", "Total spent"},
        {"Customer", "Status", "Contact", "Gallons out", "Unpaid balance"}
    };
    private static final int[][] COLUMN_WIDTHS = {
        {140, 85, 150, 140, 140}, {140, 85, 150, 140, 140},
        {65, 190, 130, 85, 150}, {190, 90, 130, 110, 150}
    };

    /** Keeps database access replaceable for report-flow tests. Called only by background workers. */
    interface ReportSource {
        List<Object[]> dailySales(int year, int month) throws SQLException;
        List<Object[]> monthlySales(int year) throws SQLException;
        List<Object[]> customerSales() throws SQLException;
        List<LedgerDao.UnpaidAccount> unpaidAccounts() throws SQLException;
    }

    private static final class DatabaseReportSource implements ReportSource {
        private final ReportDao reports = new ReportDao();
        private final LedgerDao ledger = new LedgerDao();

        public List<Object[]> dailySales(int year, int month) throws SQLException {
            return reports.dailySales(year, month);
        }

        public List<Object[]> monthlySales(int year) throws SQLException {
            return reports.monthlySales(year);
        }

        public List<Object[]> customerSales() throws SQLException {
            return reports.customerSales();
        }

        public List<LedgerDao.UnpaidAccount> unpaidAccounts() throws SQLException {
            return ledger.unpaidAccounts();
        }
    }

    /** A complete result is built off the event thread before any visible rows are replaced. */
    private static final class ReportData {
        private final List<Object[]> rows = new ArrayList<>();
        private String summary;
        private PrintDocument document;
    }

    private final ReportSource source;
    private final JComboBox<String> kindBox = new JComboBox<>(KINDS);
    private final JComboBox<Integer> yearBox = new JComboBox<>();
    private final JComboBox<String> monthBox = new JComboBox<>();
    private final JLabel yearLabel = UiKit.label("Year");
    private final JLabel monthLabel = UiKit.label("Month");
    private final JTable table = UiKit.table(new DefaultTableModel());
    private final JLabel summary = UiKit.sectionTitle(" ");
    private final JLabel status = UiKit.muted(" ");
    private final UiKit.ThemedButton printButton = UiKit.button("Print preview", UiKit.SIDEBAR);
    private SwingWorker<ReportData, Void> worker;
    private long requestNumber;
    private String loadedTitle;
    private PrintDocument loadedDocument;

    public ReportsPanel() {
        this(new DatabaseReportSource());
    }

    ReportsPanel(ReportSource source) {
        super(new BorderLayout(0, 16));
        this.source = source;
        setBackground(UiKit.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        add(UiKit.header("Reports", "Owner only. Reports update when you change the report or its dates."),
                BorderLayout.NORTH);

        LocalDate today = LocalDate.now();
        for (int y = today.getYear() - 5; y <= today.getYear() + 1; y++) {
            yearBox.addItem(y);
        }
        yearBox.setSelectedItem(today.getYear());
        for (Month m : Month.values()) {
            monthBox.addItem(m.getDisplayName(TextStyle.FULL, Locale.ENGLISH));
        }
        monthBox.setSelectedIndex(today.getMonthValue() - 1);
        UiKit.styleInput(kindBox);
        UiKit.styleInput(yearBox);
        UiKit.styleInput(monthBox);
        UiKit.styleTable(table);
        // Install listeners after setting initial values, so constructing a hidden screen does no queries.
        kindBox.addActionListener(e -> {
            applyFades();
            showReport();
        });
        yearBox.addActionListener(e -> {
            if (kindBox.getSelectedIndex() <= 1) {
                showReport();
            }
        });
        monthBox.addActionListener(e -> {
            if (kindBox.getSelectedIndex() == 0) {
                showReport();
            }
        });
        printButton.setEnabled(false);
        printButton.addActionListener(e -> print());

        // Separate report selection from date filters so every control fits at the minimum window width.
        JPanel toolbar = new JPanel(new GridLayout(0, 1, 0, 8));
        toolbar.setOpaque(false);
        JPanel reportRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        reportRow.setOpaque(false);
        reportRow.add(UiKit.label("Report"));
        reportRow.add(kindBox);
        reportRow.add(printButton);
        JPanel dateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        dateRow.setOpaque(false);
        dateRow.add(yearLabel);
        dateRow.add(yearBox);
        dateRow.add(monthLabel);
        dateRow.add(monthBox);
        toolbar.add(reportRow);
        toolbar.add(dateRow);

        JPanel footer = new JPanel(new BorderLayout(0, 4));
        footer.setOpaque(false);
        footer.add(summary, BorderLayout.NORTH);
        footer.add(status, BorderLayout.SOUTH);

        JPanel card = UiKit.card(new BorderLayout(0, 12));
        card.add(toolbar, BorderLayout.NORTH);
        card.add(UiKit.scroll(table), BorderLayout.CENTER);
        card.add(footer, BorderLayout.SOUTH);
        add(card, BorderLayout.CENTER);
        applyFades();
    }

    @Override
    public void refresh() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::refresh);
            return;
        }
        showReport();
    }

    private void showReport() {
        final int kind = kindBox.getSelectedIndex();
        final int year = (Integer) yearBox.getSelectedItem();
        final int month = monthBox.getSelectedIndex() + 1;
        final long request = ++requestNumber;
        if (worker != null) {
            worker.cancel(true);
        }
        loadedTitle = null;
        loadedDocument = null;
        printButton.setEnabled(false);
        useTable(kind);
        summary.setText(" ");
        status.setText("Loading report...");
        worker = new SwingWorker<ReportData, Void>() {
            @Override
            protected ReportData doInBackground() throws SQLException {
                ReportData result;
                switch (kind) {
                    case 0: result = daily(year, month); break;
                    case 1: result = monthly(year); break;
                    case 2: result = bestCustomers(); break;
                    default: result = unpaid(); break;
                }
                String title = KINDS[kind] + (kind == 0 ? " - " + monthName(month) + " " + year
                        : kind == 1 ? " - " + year : "");
                List<List<String>> rows = new ArrayList<>();
                for (Object[] row : result.rows) {
                    List<String> cells = new ArrayList<>();
                    for (Object cell : row) cells.add(cell == null ? "" : cell.toString());
                    rows.add(cells);
                }
                double[] widths = new double[COLUMN_WIDTHS[kind].length];
                for (int i = 0; i < widths.length; i++) widths[i] = COLUMN_WIDTHS[kind][i];
                result.document = new PrintDocument(title, Collections.emptyList(), Arrays.asList(COLUMNS[kind]),
                        rows, Collections.singletonList(result.summary), widths);
                return result;
            }

            @Override
            protected void done() {
                // JDBC may finish after cancellation. Only the newest selection may update the screen.
                if (request != requestNumber) {
                    return;
                }
                worker = null;
                try {
                    ReportData result = get();
                    DefaultTableModel model = useTable(kind);
                    for (Object[] row : result.rows) {
                        model.addRow(row);
                    }
                    summary.setText(result.summary);
                    status.setText(" ");
                    loadedTitle = KINDS[kind] + (kind == 0 ? " - " + monthName(month) + " " + year
                            : kind == 1 ? " - " + year : "");
                    loadedDocument = result.document;
                    printButton.setEnabled(true);
                } catch (CancellationException e) {
                    status.setText("Report loading cancelled. Choose a report to try again.");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    status.setText("Report loading interrupted. Choose a report to try again.");
                } catch (ExecutionException e) {
                    status.setText("Could not build the report: " + e.getCause().getMessage());
                }
            }
        };
        worker.execute();
    }

    /** Replaces the table with a read-only model and centers every displayed column. */
    private DefaultTableModel useTable(int kind) {
        DefaultTableModel model = new DefaultTableModel(COLUMNS[kind], 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table.setModel(model);
        UiKit.widths(table, COLUMN_WIDTHS[kind]);
        UiKit.styleTable(table);
        return model;
    }

    private ReportData daily(int year, int month) throws SQLException {
        ReportData data = new ReportData();
        int orders = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (Object[] r : source.dailySales(year, month)) {
            data.rows.add(new Object[] {Fmt.date((LocalDate) r[0]), r[1], Fmt.peso((BigDecimal) r[2]),
                    Fmt.peso((BigDecimal) r[3]), Fmt.peso((BigDecimal) r[4])});
            orders += (Integer) r[1];
            total = total.add((BigDecimal) r[2]);
        }
        data.summary = monthName(month) + " " + year + ": " + orders + " order(s), "
                + Fmt.peso(total) + " in sales";
        return data;
    }

    private ReportData monthly(int year) throws SQLException {
        ReportData data = new ReportData();
        Map<Integer, Object[]> byMonth = new HashMap<>();
        for (Object[] r : source.monthlySales(year)) {
            byMonth.put((Integer) r[0], r);
        }
        int orders = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (int month = 1; month <= 12; month++) {
            Object[] r = byMonth.get(month);
            int count = r == null ? 0 : (Integer) r[1];
            BigDecimal sales = r == null ? BigDecimal.ZERO : (BigDecimal) r[2];
            BigDecimal cash = r == null ? BigDecimal.ZERO : (BigDecimal) r[3];
            BigDecimal credit = r == null ? BigDecimal.ZERO : (BigDecimal) r[4];
            data.rows.add(new Object[] {monthName(month), count, Fmt.peso(sales), Fmt.peso(cash), Fmt.peso(credit)});
            orders += count;
            total = total.add(sales);
        }
        data.summary = year + ": " + orders + " order(s), " + Fmt.peso(total) + " in sales";
        return data;
    }

    private ReportData bestCustomers() throws SQLException {
        ReportData data = new ReportData();
        List<CustomerTotal> top = Ranking.topCustomers(source.customerSales(), 10);
        for (int i = 0; i < top.size(); i++) {
            CustomerTotal c = top.get(i);
            data.rows.add(new Object[] {i + 1, c.getFullName(), c.getContactNo(), c.getOrderCount(),
                    Fmt.peso(c.getTotalSpent())});
        }
        data.summary = top.isEmpty() ? "No sales yet."
                : "Top " + top.size() + " customers by total spent, all time.";
        return data;
    }

    private ReportData unpaid() throws SQLException {
        ReportData data = new ReportData();
        BigDecimal total = BigDecimal.ZERO;
        for (LedgerDao.UnpaidAccount row : source.unpaidAccounts()) {
            CustomerAccount account = row.getAccount();
            data.rows.add(new Object[] {account.getFullName(), row.isArchived() ? "Archived" : "Active",
                    account.getContactNo(), account.getGallonsOut(), Fmt.peso(account.getUnpaid())});
            total = total.add(account.getUnpaid());
        }
        data.summary = data.rows.size() + " customer(s) owe " + Fmt.peso(total) + " in total.";
        return data;
    }

    private static String monthName(int month) {
        return Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    private void applyFades() {
        int kind = kindBox.getSelectedIndex();
        fade(yearLabel, yearBox, kind == 0 || kind == 1);
        fade(monthLabel, monthBox, kind == 0);
    }

    /** A picker that the current report does not use is greyed out and cannot be changed. */
    private static void fade(JLabel label, JComboBox<?> box, boolean inUse) {
        label.setForeground(inUse ? UiKit.TEXT : UiKit.DISABLED);
        box.setEnabled(inUse);
        box.setBackground(inUse ? UiKit.SURFACE : UiKit.BACKGROUND);
    }

    private void print() {
        if (loadedDocument == null || !printButton.isEnabled()) {
            return;
        }
        PrintPreviewDialog.showPreview(this, loadedDocument, true);
    }
}
