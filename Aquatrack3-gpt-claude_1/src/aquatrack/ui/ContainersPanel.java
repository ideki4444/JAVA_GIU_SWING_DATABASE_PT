package aquatrack.ui;

import aquatrack.dao.LedgerDao;
import aquatrack.dao.LedgerDao.PaymentResult;
import aquatrack.model.CustomerAccount;
import aquatrack.util.Fmt;
import aquatrack.util.Validator;
import java.awt.BorderLayout;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.swing.Box;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

/** Customer balances and container obligations, with committed payment acknowledgements. */
public final class ContainersPanel extends JPanel implements MainFrame.Screen {
    interface Data {
        List<CustomerAccount> accounts() throws SQLException;
        PaymentResult recordPayment(int id, BigDecimal amount) throws SQLException;
        void recordMovement(int id, String movement, int quantity) throws SQLException;
    }
    interface Dialogs { void payment(PaymentResult result); void problem(String message); }
    private final Data data;
    private final Dialogs dialogs;
    private final JCheckBox openOnly = new JCheckBox("Only customers with gallons out or a balance", true);
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[] {"Customer", "Contact", "Gallons out", "Unpaid balance"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = UiKit.table(model);
    private final JLabel totals = UiKit.sectionTitle(" ");
    private final JLabel chosen = UiKit.sectionTitle("Select a customer");
    private final JTextField paymentField = UiKit.field(12);
    private final JTextField gallonField = UiKit.field(6);
    private final JLabel status = UiKit.muted(" ");
    private final UiKit.ThemedButton payButton = UiKit.button("Record payment", UiKit.ACCENT);
    private final UiKit.ThemedButton returnButton = UiKit.button("Record gallons returned", UiKit.SIDEBAR);
    private final UiKit.ThemedButton borrowButton = UiKit.button("Record gallons borrowed", UiKit.SIDEBAR);
    private List<CustomerAccount> shown = new ArrayList<>();
    private boolean busy, recordsLoaded;
    private long generation;
    private String unrefreshedSave;

    public ContainersPanel() { this(defaultData(), null); }
    ContainersPanel(Data data, Dialogs dialogs) {
        super(new BorderLayout(0, 16));
        this.data = data;
        this.dialogs = dialogs == null ? new Dialogs() {
            @Override public void payment(PaymentResult result) {
                JOptionPane.showMessageDialog(ContainersPanel.this, paymentMessage(result), "Payment recorded", JOptionPane.INFORMATION_MESSAGE);
            }
            @Override public void problem(String message) { UiKit.warn(ContainersPanel.this, message); }
        } : dialogs;
        setBackground(UiKit.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        add(UiKit.header("Containers & Balances", "Gallons still out, unpaid credit, and the payments that clear it."), BorderLayout.NORTH);
        UiKit.styleTable(table);
        UiKit.widths(table, 170, 130, 110, 140);
        table.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) showChosen(); });
        openOnly.setOpaque(false);
        openOnly.setFont(UiKit.BODY);
        openOnly.addActionListener(e -> reload(selectedId()));
        JPanel left = UiKit.card(new BorderLayout(0, 10));
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(openOnly, BorderLayout.WEST);
        top.add(totals, BorderLayout.SOUTH);
        left.add(top, BorderLayout.NORTH);
        left.add(UiKit.scroll(table), BorderLayout.CENTER);
        left.add(status, BorderLayout.SOUTH);
        payButton.addActionListener(e -> recordPayment());
        returnButton.addActionListener(e -> recordGallons("Returned"));
        borrowButton.addActionListener(e -> recordGallons("Borrowed"));
        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setOpaque(false);
        column.add(chosen);
        column.add(Box.createVerticalStrut(18));
        column.add(UiKit.label("Payment received (PHP)"));
        column.add(Box.createVerticalStrut(6));
        column.add(UiKit.fullWidth(paymentField));
        column.add(Box.createVerticalStrut(8));
        column.add(UiKit.fullWidth(payButton));
        column.add(Box.createVerticalStrut(26));
        column.add(UiKit.label("Gallons outside a sale"));
        column.add(Box.createVerticalStrut(6));
        column.add(UiKit.fullWidth(gallonField));
        column.add(Box.createVerticalStrut(8));
        column.add(UiKit.fullWidth(returnButton));
        column.add(Box.createVerticalStrut(8));
        column.add(UiKit.fullWidth(borrowButton));
        JPanel right = UiKit.card(new BorderLayout());
        right.add(column, BorderLayout.NORTH);
        add(UiKit.splitFixedRight(left, right, 470), BorderLayout.CENTER);
        updateControls();
    }

    private static Data defaultData() {
        LedgerDao ledger = new LedgerDao();
        return new Data() {
            @Override public List<CustomerAccount> accounts() throws SQLException { return ledger.accounts(); }
            @Override public PaymentResult recordPayment(int id, BigDecimal amount) throws SQLException { return ledger.recordPayment(id, amount); }
            @Override public void recordMovement(int id, String movement, int count) throws SQLException { ledger.recordMovement(id, movement, count); }
        };
    }

    static String paymentMessage(PaymentResult result) {
        return "Payment reference: " + result.getPaymentId()
                + "\nCustomer: " + result.getCustomerName()
                + "\nAmount received: " + Fmt.peso(result.getAmount())
                + "\nPrevious balance: " + Fmt.peso(result.getPreviousBalance())
                + "\nRemaining balance: " + Fmt.peso(result.getRemainingBalance())
                + (result.getRemainingBalance().signum() == 0 ? "\nFully paid" : "")
                + "\nRecorded: " + result.getRecordedAt().format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a"))
                + "\n\nYour payment has been saved.";
    }

    @Override public void refresh() { if (!busy) reload(selectedId()); }
    @Override public boolean hasPendingOperation() { return busy; }

    private void reload(int keepId) {
        long request = ++generation;
        recordsLoaded = false;
        updateControls();
        status.setText(unrefreshedSave == null ? "Loading balances..." : unrefreshedSave + " Refreshing balances...");
        new SwingWorker<List<CustomerAccount>, Void>() {
            @Override protected List<CustomerAccount> doInBackground() throws Exception { return data.accounts(); }
            @Override protected void done() {
                if (request != generation) return;
                try {
                    List<CustomerAccount> all = get();
                    int gallons = 0;
                    BigDecimal unpaid = BigDecimal.ZERO;
                    shown = new ArrayList<>();
                    for (CustomerAccount a : all) {
                        gallons += a.getGallonsOut();
                        unpaid = unpaid.add(a.getUnpaid());
                        if (!openOnly.isSelected() || a.hasOpenItems()) shown.add(a);
                    }
                    model.setRowCount(0);
                    int highlight = -1;
                    for (int i = 0; i < shown.size(); i++) {
                        CustomerAccount a = shown.get(i);
                        model.addRow(new Object[] {a.getFullName(), a.getContactNo(), a.getGallonsOut(), Fmt.peso(a.getUnpaid())});
                        if (a.getId() == keepId) highlight = i;
                    }
                    if (highlight >= 0) {
                        int view = table.convertRowIndexToView(highlight);
                        if (view >= 0) table.setRowSelectionInterval(view, view);
                    } else table.clearSelection();
                    totals.setText("All customers: " + gallons + " gallon(s) out, " + Fmt.peso(unpaid) + " unpaid");
                    recordsLoaded = true;
                    status.setText((unrefreshedSave == null ? "" : unrefreshedSave + " ") + shown.size() + " customer(s) listed");
                    unrefreshedSave = null;
                    showChosen();
                } catch (Exception e) {
                    String message = unrefreshedSave == null
                            ? "Could not load balances. Try opening Containers & Balances again."
                            : unrefreshedSave + " Balances could not be refreshed. Do not record this transaction again. Reopen Containers & Balances to refresh.";
                    status.setText(message);
                    dialogs.problem(message + "\n\n" + problem(e));
                }
                updateControls();
            }
        }.execute();
    }

    private CustomerAccount selectedAccount() {
        int row = table.getSelectedRow();
        int index = row < 0 ? -1 : table.convertRowIndexToModel(row);
        return index < 0 || index >= shown.size() ? null : shown.get(index);
    }
    private int selectedId() { CustomerAccount a = selectedAccount(); return a == null ? -1 : a.getId(); }
    private void showChosen() {
        CustomerAccount a = selectedAccount();
        chosen.setText(a == null ? "Select a customer" : a.getFullName() + " (" + a.getContactNo() + ")");
        updateControls();
    }
    private void updateControls() {
        boolean ready = !busy && recordsLoaded && selectedAccount() != null;
        payButton.setEnabled(ready);
        returnButton.setEnabled(ready);
        borrowButton.setEnabled(ready);
        paymentField.setEnabled(!busy);
        gallonField.setEnabled(!busy);
        table.setEnabled(!busy && recordsLoaded);
        openOnly.setEnabled(!busy);
    }

    private void recordPayment() {
        if (busy || !recordsLoaded) return;
        CustomerAccount account = selectedAccount();
        if (account == null) { dialogs.problem("Select a customer in the list first."); return; }
        try {
            BigDecimal amount = Validator.requireMoney(paymentField.getText(), "Amount paid");
            if (amount.signum() <= 0) throw new IllegalArgumentException("Amount paid must be more than zero.");
            busy = true;
            generation++;
            updateControls();
            status.setText("Recording payment...");
            new SwingWorker<PaymentResult, Void>() {
                @Override protected PaymentResult doInBackground() throws Exception { return data.recordPayment(account.getId(), amount); }
                @Override protected void done() {
                    busy = false;
                    PaymentResult result;
                    try {
                        result = get();
                    } catch (Exception e) {
                        dialogs.problem(problem(e));
                        status.setText("Payment was not recorded. Your entry is still here.");
                        updateControls();
                        return;
                    }
                    paymentField.setText("");
                    unrefreshedSave = "Payment #" + result.getPaymentId() + " was saved for " + result.getCustomerName() + ".";
                    status.setText(unrefreshedSave);
                    dialogs.payment(result);
                    reload(account.getId());
                    updateControls();
                }
            }.execute();
        } catch (IllegalArgumentException e) { dialogs.problem(e.getMessage()); }
    }

    private void recordGallons(String movement) {
        if (busy || !recordsLoaded) return;
        CustomerAccount account = selectedAccount();
        if (account == null) { dialogs.problem("Select a customer in the list first."); return; }
        try {
            int count = Validator.requirePositiveInt(gallonField.getText(), "Number of gallons");
            busy = true;
            generation++;
            updateControls();
            status.setText("Recording containers...");
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() throws Exception { data.recordMovement(account.getId(), movement, count); return null; }
                @Override protected void done() {
                    busy = false;
                    try {
                        get();
                    } catch (Exception e) {
                        dialogs.problem(problem(e));
                        status.setText("Containers were not recorded. Your entry is still here.");
                        updateControls();
                        return;
                    }
                    gallonField.setText("");
                    unrefreshedSave = count + " gallon(s) saved as " + movement.toLowerCase() + ".";
                    status.setText(unrefreshedSave);
                    reload(account.getId());
                    updateControls();
                }
            }.execute();
        } catch (IllegalArgumentException e) { dialogs.problem(e.getMessage()); }
    }

    private static String problem(Exception exception) {
        Throwable cause = exception instanceof ExecutionException ? exception.getCause() : exception;
        return cause.getMessage() == null ? "The operation could not be completed." : cause.getMessage();
    }
}
