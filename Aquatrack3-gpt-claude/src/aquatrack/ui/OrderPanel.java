package aquatrack.ui;

import aquatrack.dao.CustomerDao;
import aquatrack.dao.OrderDao;
import aquatrack.dao.ProductDao;
import aquatrack.model.Customer;
import aquatrack.model.CustomerOrder;
import aquatrack.model.OrderLine;
import aquatrack.model.Product;
import aquatrack.util.Fmt;
import aquatrack.util.OrderRules;
import aquatrack.util.Session;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;

/** New Order: choose the customer, order type, payment, gallons, and items, then save to print the receipt. */
public final class OrderPanel extends JPanel implements MainFrame.Screen {

    interface OrderData {
        List<Customer> customers() throws SQLException;
        List<Product> products() throws SQLException;
        int placeOrder(int customerId, String type, String payment, int userId,
                       int borrowed, int returned, List<OrderLine> lines) throws SQLException;
        CustomerOrder receipt(int orderId) throws SQLException;
    }

    interface Feedback {
        void warn(String message);
        void error(SQLException error);
        void receipt(CustomerOrder order);
    }

    private final OrderData data;
    private final Feedback feedback;
    private final List<OrderLine> cart = new ArrayList<>();
    private boolean choicesLoaded;
    private int lastSavedOrderId = -1;

    private final JComboBox<Customer> customerBox = new JComboBox<>();
    private final JComboBox<Product> productBox = new JComboBox<>();
    private final JRadioButton walkIn = UiKit.radio("Walk-in (served now)", true);
    private final JRadioButton delivery = UiKit.radio("Delivery (rider assigned later)", false);
    private final JRadioButton cash = UiKit.radio("Cash, paid now", true);
    private final JRadioButton credit = UiKit.radio("Credit, add to balance", false);
    private final JSpinner borrowed = new JSpinner(new SpinnerNumberModel(0, 0, 500, 1));
    private final JSpinner returned = new JSpinner(new SpinnerNumberModel(0, 0, 500, 1));
    private final JSpinner quantity = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
    private final DefaultTableModel cartModel = new DefaultTableModel(
            new Object[] {"Item", "Qty", "Price", "Subtotal"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable cartTable = UiKit.table(cartModel);
    private final JLabel totalLabel = new JLabel(Fmt.peso(BigDecimal.ZERO));
    private final JLabel status = UiKit.muted(" ");
    private final JLabel savedStatus = UiKit.muted(" ");
    private final UiKit.ThemedButton receiptButton = UiKit.button("View last receipt", UiKit.SIDEBAR);

    public OrderPanel() {
        this(new OrderData() {
            private final CustomerDao customers = new CustomerDao();
            private final ProductDao products = new ProductDao();
            private final OrderDao orders = new OrderDao();

            public List<Customer> customers() throws SQLException { return customers.search(""); }
            public List<Product> products() throws SQLException { return products.findAll(true); }
            public int placeOrder(int customerId, String type, String payment, int userId,
                                  int borrowed, int returned, List<OrderLine> lines) throws SQLException {
                return orders.placeOrder(customerId, type, payment, userId, borrowed, returned, lines);
            }
            public CustomerOrder receipt(int orderId) throws SQLException { return orders.findById(orderId); }
        }, null);
    }

    OrderPanel(OrderData data, Feedback feedback) {
        super(new BorderLayout(0, 16));
        this.data = data;
        this.feedback = feedback == null ? new Feedback() {
            public void warn(String message) { UiKit.warn(OrderPanel.this, message); }
            public void error(SQLException error) { UiKit.error(OrderPanel.this, error); }
            public void receipt(CustomerOrder order) { ReceiptDialog.showReceipt(OrderPanel.this, order); }
        } : feedback;
        setBackground(UiKit.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        add(UiKit.header("New Order", "Record a sale. Walk-in orders are served now; delivery orders wait for a rider."),
                BorderLayout.NORTH);

        ButtonGroup types = new ButtonGroup();
        types.add(walkIn);
        types.add(delivery);
        ButtonGroup payments = new ButtonGroup();
        payments.add(cash);
        payments.add(credit);
        UiKit.styleInput(customerBox);
        UiKit.styleInput(productBox);
        customerBox.setPreferredSize(new Dimension(300, 30));
        productBox.setPreferredSize(new Dimension(280, 30));
        quantity.setPreferredSize(new Dimension(70, 28));
        UiKit.styleTable(cartTable);
        UiKit.widths(cartTable, 230, 70, 120, 130);
        UiKit.alignRight(cartTable, 1, 2, 3);
        totalLabel.setFont(UiKit.TITLE);
        totalLabel.setForeground(UiKit.TEXT);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        UiKit.formRow(fields, 0, "Customer", customerBox);
        UiKit.formRow(fields, 1, "Order type", stack(walkIn, delivery));
        UiKit.formRow(fields, 2, "Payment", stack(cash, credit));
        UiKit.formRow(fields, 3, "Gallons borrowed", borrowed);
        UiKit.formRow(fields, 4, "Gallons returned", returned);

        JPanel details = UiKit.card(new BorderLayout(0, 12));
        details.setPreferredSize(new Dimension(360, 0));
        details.add(UiKit.sectionTitle("Order details"), BorderLayout.NORTH);
        details.add(fields, BorderLayout.CENTER);

        // The product box gets a row of its own; Qty and the Add button share the row below it.
        JPanel productRow = new JPanel(new BorderLayout(0, 4));
        productRow.setOpaque(false);
        productRow.add(UiKit.label("Product"), BorderLayout.NORTH);
        productRow.add(productBox, BorderLayout.CENTER);
        JPanel qtyBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        qtyBox.setOpaque(false);
        qtyBox.add(UiKit.label("Qty"));
        qtyBox.add(quantity);
        UiKit.ThemedButton addButton = UiKit.button("Add to order", UiKit.SIDEBAR);
        addButton.addActionListener(e -> addItem());
        JPanel qtyRow = new JPanel(new BorderLayout(8, 0));
        qtyRow.setOpaque(false);
        qtyRow.add(qtyBox, BorderLayout.WEST);
        qtyRow.add(addButton, BorderLayout.EAST);
        JPanel addRow = new JPanel(new GridLayout(2, 1, 0, 8));
        addRow.setOpaque(false);
        addRow.add(productRow);
        addRow.add(qtyRow);

        UiKit.ThemedButton removeButton = UiKit.button("Remove item", UiKit.DANGER);
        removeButton.addActionListener(e -> removeSelected());
        UiKit.ThemedButton clearButton = UiKit.button("Clear order", UiKit.SIDEBAR);
        clearButton.addActionListener(e -> clearOrder());
        UiKit.ThemedButton saveButton = UiKit.button("Save and print receipt", UiKit.ACCENT);
        saveButton.addActionListener(e -> save());
        receiptButton.setEnabled(false);
        receiptButton.addActionListener(e -> showLastReceipt());

        // Save gets a row of its own, so it is never pushed out of sight on a narrow window.
        JPanel removeRow = new JPanel(new GridLayout(1, 2, 8, 0));
        removeRow.setOpaque(false);
        removeRow.add(removeButton);
        removeRow.add(clearButton);
        JPanel buttons = new JPanel(new GridLayout(3, 1, 0, 8));
        buttons.setOpaque(false);
        buttons.add(removeRow);
        buttons.add(saveButton);
        buttons.add(receiptButton);

        JPanel totals = new JPanel(new BorderLayout());
        totals.setOpaque(false);
        totals.add(UiKit.label("Order total"), BorderLayout.WEST);
        totals.add(totalLabel, BorderLayout.EAST);

        JPanel south = new JPanel();
        south.setLayout(new BoxLayout(south, BoxLayout.Y_AXIS));
        south.setOpaque(false);
        south.add(UiKit.fullWidth(totals));
        south.add(Box.createVerticalStrut(8));
        south.add(UiKit.fullWidth(buttons));
        south.add(Box.createVerticalStrut(8));
        south.add(status);
        south.add(savedStatus);

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setOpaque(false);
        top.add(UiKit.sectionTitle("Items"));
        top.add(Box.createVerticalStrut(10));
        top.add(UiKit.fullWidth(addRow));

        JPanel items = UiKit.card(new BorderLayout(0, 12));
        items.add(top, BorderLayout.NORTH);
        items.add(UiKit.scroll(cartTable), BorderLayout.CENTER);
        items.add(south, BorderLayout.SOUTH);

        add(UiKit.splitFixedLeft(details, items, 400), BorderLayout.CENTER);
    }

    /** Reloads the customer and product lists. The current order stays on screen. */
    @Override
    public void refresh() {
        Customer selectedCustomer = (Customer) customerBox.getSelectedItem();
        Product selectedProduct = (Product) productBox.getSelectedItem();
        try {
            // Fetch both lists before replacing either model so a failed load leaves a coherent draft.
            List<Customer> customers = data.customers();
            List<Product> products = data.products();
            customerBox.setModel(new DefaultComboBoxModel<>(customers.toArray(new Customer[0])));
            productBox.setModel(new DefaultComboBoxModel<>(products.toArray(new Product[0])));
            if (choicesLoaded) {
                customerBox.setSelectedIndex(-1);
                productBox.setSelectedIndex(-1);
                for (Customer customer : customers) {
                    if (selectedCustomer != null && customer.getId() == selectedCustomer.getId()) {
                        customerBox.setSelectedItem(customer);
                        break;
                    }
                }
                for (Product product : products) {
                    if (selectedProduct != null && product.getId() == selectedProduct.getId()) {
                        productBox.setSelectedItem(product);
                        break;
                    }
                }
            }
            choicesLoaded = true;
            status.setText(customerBox.getSelectedItem() == null ? "Choose a customer before saving."
                    : productBox.getSelectedItem() == null ? "Choose a product before adding items." : " ");
        } catch (SQLException e) {
            status.setText("Could not load customers or products: " + e.getMessage());
        }
    }

    private static JPanel stack(JRadioButton first, JRadioButton second) {
        JPanel panel = new JPanel(new GridLayout(2, 1, 0, 2));
        panel.setOpaque(false);
        panel.add(first);
        panel.add(second);
        return panel;
    }

    private void addItem() {
        Product product = (Product) productBox.getSelectedItem();
        if (product == null) {
            feedback.warn(productBox.getItemCount() == 0
                    ? "There are no active products. Add one on the Products & Prices screen."
                    : "Choose a product first.");
            return;
        }
        if (!commitSpinner(quantity, "Quantity")) {
            return;
        }
        int wanted = (Integer) quantity.getValue();
        int alreadyInOrder = quantityInCart(product.getId());
        String problem = OrderRules.stockProblem(product, alreadyInOrder, wanted);
        if (problem != null) {
            feedback.warn(problem);
            return;
        }
        removeFromCart(product.getId());
        cart.add(OrderLine.priced(product.getId(), product.getName(), alreadyInOrder + wanted, product.getUnitPrice()));
        quantity.setValue(1);
        redrawCart();
    }

    private void removeSelected() {
        int row = cartTable.getSelectedRow();
        if (row < 0) {
            feedback.warn("Select an item in the order first.");
            return;
        }
        cart.remove(row);
        redrawCart();
    }

    private void clearOrder() {
        cart.clear();
        borrowed.setValue(0);
        returned.setValue(0);
        quantity.setValue(1);
        walkIn.setSelected(true);
        cash.setSelected(true);
        redrawCart();
    }

    private void save() {
        Customer customer = (Customer) customerBox.getSelectedItem();
        if (customer == null) {
            feedback.warn("Choose a customer first.");
            return;
        }
        if (cart.isEmpty()) {
            feedback.warn("Add at least one item to the order.");
            return;
        }
        if (!commitSpinner(borrowed, "Gallons borrowed") || !commitSpinner(returned, "Gallons returned")) {
            return;
        }
        String type = delivery.isSelected() ? OrderDao.DELIVERY : OrderDao.WALK_IN;
        String payment = credit.isSelected() ? "Credit" : "Cash";
        try {
            int orderId = data.placeOrder(customer.getId(), type, payment, Session.user().getId(),
                    (Integer) borrowed.getValue(), (Integer) returned.getValue(), cart);
            // The sale is committed. Never leave its draft available for accidental resubmission.
            lastSavedOrderId = orderId;
            clearOrder();
            savedStatus.setText("Order #" + orderId + " saved.");
            receiptButton.setEnabled(true);
            refresh();
            showLastReceipt();
        } catch (OrderDao.PricesChangedException e) {
            cart.clear();
            cart.addAll(e.getUpdatedLines());
            redrawCart();
            refresh();
            status.setText("Prices updated. Review the total before saving.");
            feedback.warn(e.getMessage());
        } catch (IllegalArgumentException e) {
            feedback.warn(e.getMessage());
        } catch (SQLException e) {
            feedback.error(e);
        }
    }

    private void showLastReceipt() {
        if (lastSavedOrderId < 0) {
            return;
        }
        try {
            CustomerOrder receipt = data.receipt(lastSavedOrderId);
            if (receipt == null) {
                throw new SQLException("The saved receipt is not available yet.");
            }
            savedStatus.setText("Order #" + lastSavedOrderId + " saved.");
            receiptButton.setText("View receipt #" + lastSavedOrderId);
            feedback.receipt(receipt);
        } catch (SQLException e) {
            savedStatus.setText("Order #" + lastSavedOrderId + " saved. Receipt unavailable.");
            receiptButton.setText("Retry receipt #" + lastSavedOrderId);
            feedback.warn("Order #" + lastSavedOrderId + " was saved successfully. "
                    + "The receipt could not be loaded. Use Retry receipt when the database is available.");
        }
    }

    private boolean commitSpinner(JSpinner spinner, String name) {
        try {
            spinner.commitEdit();
            return true;
        } catch (ParseException e) {
            feedback.warn(name + " must be a whole number within the allowed range.");
            spinner.requestFocusInWindow();
            return false;
        }
    }

    private int quantityInCart(int productId) {
        for (OrderLine line : cart) {
            if (line.getProductId() == productId) {
                return line.getQuantity();
            }
        }
        return 0;
    }

    private void removeFromCart(int productId) {
        Iterator<OrderLine> lines = cart.iterator();
        while (lines.hasNext()) {
            if (lines.next().getProductId() == productId) {
                lines.remove();
            }
        }
    }

    private void redrawCart() {
        cartModel.setRowCount(0);
        for (OrderLine line : cart) {
            cartModel.addRow(new Object[] {line.getProductName(), line.getQuantity(),
                    Fmt.peso(line.getUnitPrice()), Fmt.peso(line.getLineTotal())});
        }
        totalLabel.setText(Fmt.peso(OrderRules.total(cart)));
    }
}
