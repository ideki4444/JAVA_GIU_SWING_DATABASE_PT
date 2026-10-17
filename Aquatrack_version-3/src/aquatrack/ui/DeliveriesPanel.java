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

    public DeliveriesPanel() {
        super(new BorderLayout(0, 16));
        setBackground(UiKit.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        add(UiKit.header("Deliveries", "Assign a rider, then move each order from Pending to Out for Delivery to Delivered."),
                BorderLayout.NORTH);

        UiKit.styleTable(table);
        UiKit.widths(table, 80, 160, 150, 170, 210, 110, 120, 120);
        UiKit.statusColumn(table, 7);
        UiKit.alignRight(table, 5);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showDetails();
            }
        });
        openOnly.setOpaque(false);
        openOnly.setFont(UiKit.BODY);
        openOnly.addActionListener(e -> reload(-1));
        UiKit.ThemedButton addRider = UiKit.button("Add rider", UiKit.SIDEBAR);
        addRider.addActionListener(e -> addRider());
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setOpaque(false);
        toolbar.add(openOnly, BorderLayout.WEST);
        toolbar.add(addRider, BorderLayout.EAST);

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
        UiKit.ThemedButton assign = UiKit.button("Assign rider", UiKit.SIDEBAR);
        assign.addActionListener(e -> assignRider());
        UiKit.ThemedButton out = UiKit.button("Out for delivery", UiKit.ACCENT);
        out.addActionListener(e -> sendOut());
        UiKit.ThemedButton delivered = UiKit.button("Mark delivered", UiKit.ACCENT);
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
        right.setPreferredSize(new Dimension(360, 0));
        right.add(UiKit.sectionTitle("Selected order"), BorderLayout.NORTH);
        right.add(details, BorderLayout.CENTER);
        right.add(actions, BorderLayout.SOUTH);

        add(UiKit.splitFixedRight(left, right, 340), BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        reload(-1);
    }

    /** Reloads the list and riders. keepOrderId re-selects that order when it is still listed. */
    private void reload(int keepOrderId) {
        try {
            shown = orders.deliveries(openOnly.isSelected());
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
            riderBox.setModel(new DefaultComboBoxModel<>(riderDao.findAll(true).toArray(new DeliveryStaff[0])));
            if (highlight >= 0) {
                table.setRowSelectionInterval(highlight, highlight);
            } else {
                table.clearSelection();
            }
            showDetails();
            status.setText(shown.size() + " delivery order(s) listed");
        } catch (SQLException e) {
            status.setText("Could not load deliveries: " + e.getMessage());
        }
    }

    private CustomerOrder selectedOrder() {
        int row = table.getSelectedRow();
        return row < 0 ? null : shown.get(row);
    }

    private void showDetails() {
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
        try {
            orders.assignRider(order.getId(), rider.getId());
            status.setText(rider.getFullName() + " assigned to order #" + order.getId() + ".");
            reload(order.getId());
        } catch (SQLException e) {
            UiKit.error(this, e);
        }
    }

    private void sendOut() {
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
        try {
            if (orders.setStatus(order.getId(), order.getStatus(), next) == 0) {
                UiKit.warn(this, "This order was changed elsewhere. The list has been refreshed.");
            } else {
                status.setText("Order #" + order.getId() + " is now " + next + ".");
            }
            reload(order.getId());
        } catch (SQLException e) {
            UiKit.error(this, e);
        }
    }

    private void addRider() {
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
        try {
            riderDao.insert(name, number);
            status.setText("Rider " + name + " added.");
            reload(-1);
        } catch (SQLException e) {
            UiKit.error(this, e);
        }
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
