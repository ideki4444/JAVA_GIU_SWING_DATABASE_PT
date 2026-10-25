package aquatrack.ui;

import aquatrack.dao.DeliveryStaffDao;
import aquatrack.dao.OrderDao;
import aquatrack.model.CustomerOrder;
import aquatrack.model.DeliveryStaff;
import aquatrack.util.DeliveryRules;
import aquatrack.util.Fmt;
import aquatrack.util.Validator;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;

/** Deliveries: assign a rider, send orders out, and mark them delivered. */
public final class DeliveriesPanel extends JPanel implements MainFrame.Screen {

    private final OrderDao orders = new OrderDao();
    private final DeliveryStaffDao riderDao = new DeliveryStaffDao();
    private final JCheckBox openOnly = new JCheckBox("Show open deliveries only", true);
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[] {"Order #", "Date", "Customer", "Address", "Items", "Total", "Rider", "Status"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = UiKit.table(model);
    private final JComboBox<DeliveryStaff> riderBox = new JComboBox<>();
    private final JTextArea details = new JTextArea();
    private final JLabel status = UiKit.muted(" ");
    private List<CustomerOrder> shown = new ArrayList<>();
    private boolean saving;
    private boolean loading;
    private boolean reloading;
    private long loadNumber;
    private javax.swing.SwingWorker<DeliveryData, Void> loader;
    private final UiKit.ThemedButton addRiderButton = UiKit.button("Add rider", UiKit.SIDEBAR);
    private final UiKit.ThemedButton assign = UiKit.button("Assign rider", UiKit.SIDEBAR);
    private final UiKit.ThemedButton out = UiKit.button("Out for delivery", UiKit.ACCENT);
    private final UiKit.ThemedButton delivered = UiKit.button("Mark delivered", UiKit.ACCENT);
    private record DeliveryData(List<CustomerOrder> orders, List<DeliveryStaff> riders) { }

    public DeliveriesPanel() {
        super(new BorderLayout(0, 16));
        setBackground(UiKit.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        add(UiKit.header("Deliveries", "Assign a rider, then move each order from Pending to Out for Delivery to Delivered."),
                BorderLayout.NORTH);

        UiKit.styleTable(table);
        UiKit.widths(table, 80, 160, 150, 170, 210, 110, 120, 120);
        UiKit.statusColumn(table, 7);
        UiKit.alignCenter(table, 5);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !reloading) {
                showDetails();
            }
        });
        openOnly.setOpaque(false);
        openOnly.setFont(UiKit.BODY);
        openOnly.addActionListener(e -> reload(-1));
        addRiderButton.addActionListener(e -> addRider());
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setOpaque(false);
        toolbar.add(openOnly, BorderLayout.WEST);
        toolbar.add(addRiderButton, BorderLayout.EAST);

        JPanel left = UiKit.card(new BorderLayout(0, 10));
        left.add(toolbar, BorderLayout.NORTH);
        left.add(UiKit.scroll(table), BorderLayout.CENTER);
        left.add(status, BorderLayout.SOUTH);

        details.setEditable(false);
        details.setFont(UiKit.BODY);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        UiKit.styleInput(riderBox);
        riderBox.setPreferredSize(new Dimension(200, 30));
        assign.addActionListener(e -> assignRider());
        out.addActionListener(e -> sendOut());
        delivered.addActionListener(e -> markDelivered());

        // The rider box fills the row beside Assign, and each status button gets a full-width row of its own.
        JPanel assignRow = new JPanel(new BorderLayout(8, 0));
        assignRow.setOpaque(false);
        assignRow.add(riderBox, BorderLayout.CENTER);
        assignRow.add(assign, BorderLayout.EAST);
        JPanel actions = new JPanel(new GridLayout(3, 1, 0, 10));
        actions.setOpaque(false);
        actions.add(assignRow);
        actions.add(out);
        actions.add(delivered);

        JPanel right = UiKit.card(new BorderLayout(0, 12));
        right.add(UiKit.sectionTitle("Selected order"), BorderLayout.NORTH);
        right.add(details, BorderLayout.CENTER);
        right.add(actions, BorderLayout.SOUTH);

        add(UiKit.splitFixedRight(left, right, 460), BorderLayout.CENTER);
        showDetails();
    }

    @Override
    public void refresh() {
        reload(-1);
    }
    @Override public boolean hasPendingOperation() { return saving; }

    /** Reloads the list and riders. keepOrderId re-selects that order when it is still listed. */
    private void reload(int keepOrderId) {
        if (saving) return;
        long request = ++loadNumber;
        if (loader != null) loader.cancel(true);
        boolean open = openOnly.isSelected();
        loading = true;
        updateButtons();
        status.setText("Loading deliveries…");
        loader = UiTask.run(() -> new DeliveryData(orders.deliveries(open), riderDao.findAll(true)), data -> {
            if (request != loadNumber || saving) return;
            reloading = true;
            shown = data.orders();
            model.setRowCount(0);
            int highlight = -1;
            for (int i = 0; i < shown.size(); i++) {
                CustomerOrder o = shown.get(i);
                model.addRow(new Object[] {o.getId(), Fmt.dateTime(o.getDate()), o.getCustomerName(),
                        o.getCustomerAddress(), o.getItemsSummary(), Fmt.peso(o.getTotal()),
                        o.getRiderName() == null ? "-" : o.getRiderName(), o.getStatus()});
                if (o.getId() == keepOrderId) {
                    highlight = i;
                }
            }
            riderBox.setModel(new DefaultComboBoxModel<>(data.riders().toArray(new DeliveryStaff[0])));
            if (highlight >= 0) {
                table.setRowSelectionInterval(highlight, highlight);
            } else {
                table.clearSelection();
            }
            reloading = false;
            loading = false;
            showDetails();
            status.setText(shown.size() + " delivery order(s) listed");
        }, e -> {
            if (request != loadNumber) return;
            loading = false;
            updateButtons();
            status.setText("Could not load deliveries: " + e.getMessage());
        });
    }

    private CustomerOrder selectedOrder() {
        int row = table.getSelectedRow();
        return row < 0 || row >= shown.size() ? null : shown.get(row);
    }

    private void showDetails() {
        updateButtons();
        CustomerOrder o = selectedOrder();
        if (o == null) {
            details.setText("Select a delivery order in the list to see its details.");
            return;
        }
        details.setText("Order #" + o.getId() + "\n"
                + "Customer: " + o.getCustomerName() + "\n"
                + "Contact:  " + o.getCustomerContact() + "\n"
                + "Address:  " + o.getCustomerAddress() + "\n"
                + "Items:    " + o.getItemsSummary() + "\n"
                + "Total:    " + Fmt.peso(o.getTotal())
                + ("Credit".equals(o.getPaymentType()) ? " (on credit)" : " (cash)") + "\n"
                + "Rider:    " + (o.getRiderName() == null ? "not assigned" : o.getRiderName()) + "\n"
                + "Status:   " + o.getStatus());
    }

    private void assignRider() {
        if (saving || loading) return;
        CustomerOrder order = selectedOrder();
        DeliveryStaff rider = (DeliveryStaff) riderBox.getSelectedItem();
        if (order == null) {
            UiKit.warn(this, "Select a delivery order first.");
            return;
        }
        if (rider == null) {
            UiKit.warn(this, "Add a rider first with Add rider.");
            return;
        }
        String problem = DeliveryRules.canAssignRider(order.getStatus());
        if (problem != null) {
            UiKit.warn(this, problem);
            return;
        }
        write(() -> {
            orders.assignRider(order.getId(), rider.getId());
            return rider.getFullName() + " assigned to order #" + order.getId() + ".";
        }, order.getId());
    }

    private void sendOut() {
        if (saving || loading) return;
        CustomerOrder order = selectedOrder();
        if (order == null) {
            UiKit.warn(this, "Select a delivery order first.");
            return;
        }
        String problem = DeliveryRules.canGoOut(order.getStatus(), order.getRiderName() != null);
        if (problem != null) {
            UiKit.warn(this, problem);
            return;
        }
        moveStatus(order, DeliveryRules.OUT);
    }

    private void markDelivered() {
        if (saving || loading) return;
        CustomerOrder order = selectedOrder();
        if (order == null) {
            UiKit.warn(this, "Select a delivery order first.");
            return;
        }
        String problem = DeliveryRules.canDeliver(order.getStatus());
        if (problem != null) {
            UiKit.warn(this, problem);
            return;
        }
        moveStatus(order, DeliveryRules.DELIVERED);
    }

    private void moveStatus(CustomerOrder order, String next) {
        write(() -> {
            if (orders.setStatus(order.getId(), order.getStatus(), next) == 0) {
                throw new IllegalArgumentException("This order was changed elsewhere. Reopen Deliveries to refresh it.");
            }
            return "Order #" + order.getId() + " is now " + next + ".";
        }, order.getId());
    }

    private void addRider() {
        if (saving || loading) return;
        String name = askUntilValid("Rider's full name:", "",
                text -> Validator.requireText(text, "Rider's name", 100));
        if (name == null) {
            return;
        }
        String number = askUntilValid("Contact number (11 digits, starting with 09):", "",
                text -> Validator.requireMobile(text, "Contact number"));
        if (number == null) {
            return;
        }
        write(() -> {
            riderDao.insert(name, number);
            return "Rider " + name + " added.";
        }, -1);
    }

    private void updateButtons() {
        boolean ready = !saving && !loading;
        CustomerOrder order = selectedOrder();
        table.setEnabled(ready);
        openOnly.setEnabled(!saving);
        riderBox.setEnabled(ready);
        addRiderButton.setEnabled(ready);
        assign.setEnabled(ready && order != null && DeliveryRules.canAssignRider(order.getStatus()) == null);
        out.setEnabled(ready && order != null && DeliveryRules.canGoOut(order.getStatus(), order.getRiderName() != null) == null);
        delivered.setEnabled(ready && order != null && DeliveryRules.canDeliver(order.getStatus()) == null);
    }

    private void write(java.util.concurrent.Callable<String> work, int keepId) {
        saving = true;
        ++loadNumber;
        updateButtons();
        status.setText("Saving…");
        UiTask.run(work, message -> {
            saving = false;
            UiKit.info(this, message);
            reload(keepId);
        }, error -> {
            saving = false;
            updateButtons();
            if (error instanceof IllegalArgumentException) UiKit.warn(this, error.getMessage());
            else UiKit.error(this, error);
            status.setText("The change was not saved. Reopen this page to refresh.");
        });
    }

    /**
     * Asks for one value until it passes the check. A blank or wrong entry shows a warning, then the same
     * input box comes back with the text still in it. Returns null if the user presses Cancel.
     */
    private String askUntilValid(String question, String start, UnaryOperator<String> check) {
        String text = start;
        while (true) {
            String typed = JOptionPane.showInputDialog(this, question, text);
            if (typed == null) {
                return null;
            }
            try {
                return check.apply(typed);
            } catch (IllegalArgumentException e) {
                text = typed;
                UiKit.warn(this, e.getMessage());
            }
        }
    }
}
