package aquatrack.ui;

import aquatrack.dao.LedgerDao;
import aquatrack.model.CustomerAccount;
import aquatrack.util.Fmt;
import aquatrack.util.Validator;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Box;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

/** Containers & Balances: gallons still with each customer, unpaid credit, payments, and gallon returns. */
public final class ContainersPanel extends JPanel implements MainFrame.Screen {

    private final LedgerDao ledger = new LedgerDao();
    private final JCheckBox openOnly = new JCheckBox("Only customers with gallons out or a balance", true);
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[] {"Customer", "Contact", "Gallons out", "Unpaid balance"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = UiKit.table(model);
    private final JLabel totals = UiKit.sectionTitle(" ");
    private final JLabel chosen = UiKit.sectionTitle("Select a customer");
    private final JTextField paymentField = UiKit.field(12);
    private final JTextField gallonField = UiKit.field(6);
    private final JLabel status = UiKit.muted(" ");
    private List<CustomerAccount> shown = new ArrayList<>();

    public ContainersPanel() {
        super(new BorderLayout(0, 16));
        setBackground(UiKit.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        add(UiKit.header("Containers & Balances", "Gallons still out, unpaid credit, and the payments that clear it."),
                BorderLayout.NORTH);

        UiKit.styleTable(table);
        UiKit.widths(table, 170, 130, 110, 140);
        UiKit.alignRight(table, 2, 3);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showChosen();
            }
        });
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

        UiKit.ThemedButton payButton = UiKit.button("Record payment", UiKit.ACCENT);
        payButton.addActionListener(e -> recordPayment());
        UiKit.ThemedButton returnButton = UiKit.button("Record gallons returned", UiKit.SIDEBAR);
        returnButton.addActionListener(e -> recordGallons("Returned"));
        UiKit.ThemedButton borrowButton = UiKit.button("Record gallons borrowed", UiKit.SIDEBAR);
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
        right.setPreferredSize(new Dimension(340, 0));
        right.add(column, BorderLayout.NORTH);

        add(UiKit.splitFixedRight(left, right, 330), BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        reload(selectedId());
    }

    /** Reloads the balances. keepId re-selects that customer when still listed; the form is left as typed. */
    private void reload(int keepId) {
        try {
            List<CustomerAccount> all = ledger.accounts();
            int gallons = 0;
            BigDecimal unpaid = BigDecimal.ZERO;
            shown = new ArrayList<>();
            for (CustomerAccount a : all) {
                gallons += a.getGallonsOut();
                unpaid = unpaid.add(a.getUnpaid());
                if (!openOnly.isSelected() || a.hasOpenItems()) {
                    shown.add(a);
                }
            }
            model.setRowCount(0);
            int highlight = -1;
            for (int i = 0; i < shown.size(); i++) {
                CustomerAccount a = shown.get(i);
                model.addRow(new Object[] {a.getFullName(), a.getContactNo(), a.getGallonsOut(), Fmt.peso(a.getUnpaid())});
                if (a.getId() == keepId) {
                    highlight = i;
                }
            }
            if (highlight >= 0) {
                table.setRowSelectionInterval(highlight, highlight);
            } else {
                table.clearSelection();
            }
            showChosen();
            totals.setText("All customers: " + gallons + " gallon(s) out, " + Fmt.peso(unpaid) + " unpaid");
            status.setText(shown.size() + " customer(s) listed");
        } catch (SQLException e) {
            status.setText("Could not load balances: " + e.getMessage());
        }
    }

    private CustomerAccount selectedAccount() {
        int row = table.getSelectedRow();
        return row < 0 ? null : shown.get(row);
    }

    private int selectedId() {
        CustomerAccount a = selectedAccount();
        return a == null ? -1 : a.getId();
    }

    private void showChosen() {
        CustomerAccount a = selectedAccount();
        chosen.setText(a == null ? "Select a customer" : a.getFullName() + " (" + a.getContactNo() + ")");
    }

    private void recordPayment() {
        CustomerAccount a = selectedAccount();
        if (a == null) {
            UiKit.warn(this, "Select a customer in the list first.");
            return;
        }
        try {
            BigDecimal amount = Validator.requireMoney(paymentField.getText(), "Amount paid");
            if (amount.signum() == 0) {
                throw new IllegalArgumentException("Amount paid must be more than zero.");
            }
            ledger.recordPayment(a.getId(), amount);
            paymentField.setText("");
            status.setText("Payment of " + Fmt.peso(amount) + " recorded for " + a.getFullName() + ".");
            reload(a.getId());
        } catch (IllegalArgumentException e) {
            UiKit.warn(this, e.getMessage());
        } catch (SQLException e) {
            UiKit.error(this, e);
        }
    }

    private void recordGallons(String movement) {
        CustomerAccount a = selectedAccount();
        if (a == null) {
            UiKit.warn(this, "Select a customer in the list first.");
            return;
        }
        try {
            int count = Validator.requirePositiveInt(gallonField.getText(), "Number of gallons");
            ledger.recordMovement(a.getId(), movement, count);
            gallonField.setText("");
            status.setText(count + " gallon(s) recorded as " + movement.toLowerCase() + " for " + a.getFullName() + ".");
            reload(a.getId());
        } catch (IllegalArgumentException e) {
            UiKit.warn(this, e.getMessage());
        } catch (SQLException e) {
            UiKit.error(this, e);
        }
    }
}
