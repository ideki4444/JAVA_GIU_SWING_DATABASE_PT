package aquatrack.dao;

import aquatrack.db.Database;
import aquatrack.model.CustomerOrder;
import aquatrack.model.OrderLine;
import aquatrack.model.Product;
import aquatrack.util.DeliveryRules;
import aquatrack.util.OrderRules;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Sales and deliveries. Each sale is saved in one transaction, so the order, its stock, and its
 * gallons are either all saved or none are.
 */
public final class OrderDao {

    interface Connections {
        Connection open() throws SQLException;
    }

    private final Connections connections;

    public OrderDao() {
        this(Database::getConnection);
    }

    OrderDao(Connections connections) {
        this.connections = connections;
    }

    /** The transaction was rolled back; these prices must be reviewed before submitting again. */
    public static final class PricesChangedException extends IllegalArgumentException {
        private final List<OrderLine> updatedLines;

        public PricesChangedException(List<OrderLine> updatedLines) {
            super("Prices changed. Review the updated items and total, then press Save again.");
            this.updatedLines = Collections.unmodifiableList(new ArrayList<>(updatedLines));
        }

        public List<OrderLine> getUpdatedLines() {
            return updatedLines;
        }
    }

    public static final String WALK_IN = "Walk-in";
    public static final String DELIVERY = "Delivery";

    /** Same 11 columns, in the same order, for every query that builds a CustomerOrder header. */
    private static final String HEADER_SQL = "SELECT o.order_id, o.order_date, c.full_name, c.address, c.contact_no, "
            + "o.order_type, o.payment_type, o.total_amount, o.delivery_status, s.full_name, u.full_name "
            + "FROM orders o JOIN customers c ON c.customer_id = o.customer_id "
            + "LEFT JOIN delivery_staff s ON s.staff_id = o.staff_id "
            + "JOIN users u ON u.user_id = o.created_by ";

    /**
     * Saves a whole sale and returns its order number. Prices and stock are re-read and locked
     * inside the transaction. A changed price rolls back the sale so the updated total can be reviewed.
     */
    public int placeOrder(int customerId, String orderType, String paymentType, int createdBy,
                          int borrowed, int returned, List<OrderLine> requested) throws SQLException {
        if (requested.isEmpty()) {
            throw new IllegalArgumentException("Add at least one item to the order.");
        }
        try (Connection con = connections.open()) {
            con.setAutoCommit(false);
            try {
                lockCustomer(con, customerId);
                List<OrderLine> lines = priceAndTakeStock(con, requested);
                String problem = OrderRules.gallonProblem(gallonsOut(con, customerId), borrowed, returned);
                if (problem != null) {
                    throw new IllegalArgumentException(problem);
                }
                String status = DELIVERY.equals(orderType) ? DeliveryRules.PENDING : DeliveryRules.DELIVERED;
                int orderId = insertOrder(con, customerId, orderType, paymentType, createdBy,
                        OrderRules.total(lines), status);
                insertLines(con, orderId, lines);
                insertMovement(con, customerId, orderId, "Borrowed", borrowed);
                insertMovement(con, customerId, orderId, "Returned", returned);
                con.commit();
                return orderId;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /** One order with its lines and gallons, for the receipt. Returns null if the order does not exist. */
    public CustomerOrder findById(int orderId) throws SQLException {
        try (Connection con = connections.open()) {
            CustomerOrder order;
            try (PreparedStatement ps = con.prepareStatement(HEADER_SQL + "WHERE o.order_id = ?")) {
                ps.setInt(1, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return null;
                    }
                    order = readHeader(rs);
                }
            }
            List<OrderLine> lines = new ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT p.product_name, oi.quantity, oi.unit_price, oi.line_total FROM order_items oi "
                    + "JOIN products p ON p.product_id = oi.product_id WHERE oi.order_id = ? ORDER BY oi.item_id")) {
                ps.setInt(1, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        lines.add(new OrderLine(0, rs.getString(1), rs.getInt(2), rs.getBigDecimal(3), rs.getBigDecimal(4)));
                    }
                }
            }
            order.setLines(lines);
            int borrowed = 0;
            int returned = 0;
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT movement, quantity FROM container_ledger WHERE order_id = ?")) {
                ps.setInt(1, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        if ("Borrowed".equals(rs.getString(1))) {
                            borrowed += rs.getInt(2);
                        } else {
                            returned += rs.getInt(2);
                        }
                    }
                }
            }
            order.setGallons(borrowed, returned);
            return order;
        }
    }

    /** Delivery orders, newest first. With openOnly, delivered orders are left out. */
    public List<CustomerOrder> deliveries(boolean openOnly) throws SQLException {
        String sql = "SELECT o.order_id, o.order_date, c.full_name, c.address, c.contact_no, o.order_type, "
                + "o.payment_type, o.total_amount, o.delivery_status, s.full_name, u.full_name, "
                + "(SELECT GROUP_CONCAT(CONCAT(oi.quantity, ' x ', p.product_name) ORDER BY oi.item_id SEPARATOR ', ') "
                + "FROM order_items oi JOIN products p ON p.product_id = oi.product_id WHERE oi.order_id = o.order_id) "
                + "FROM orders o JOIN customers c ON c.customer_id = o.customer_id "
                + "LEFT JOIN delivery_staff s ON s.staff_id = o.staff_id "
                + "JOIN users u ON u.user_id = o.created_by "
                + "WHERE o.order_type = 'Delivery'" + (openOnly ? " AND o.delivery_status <> 'Delivered'" : "")
                + " ORDER BY o.order_date DESC, o.order_id DESC";
        List<CustomerOrder> list = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                CustomerOrder order = readHeader(rs);
                order.setItemsSummary(rs.getString(12));
                list.add(order);
            }
        }
        return list;
    }

    public int assignRider(int orderId, int staffId) throws SQLException {
        return Database.update("UPDATE orders SET staff_id = ? WHERE order_id = ? "
                + "AND order_type = 'Delivery' AND delivery_status <> 'Delivered'", staffId, orderId);
    }

    /** Moves an order to a new status only if it still has the status the screen showed. */
    public int setStatus(int orderId, String fromStatus, String toStatus) throws SQLException {
        return Database.update("UPDATE orders SET delivery_status = ? WHERE order_id = ? AND delivery_status = ?",
                toStatus, orderId, fromStatus);
    }

    private static CustomerOrder readHeader(ResultSet rs) throws SQLException {
        return new CustomerOrder(rs.getInt(1), rs.getTimestamp(2).toLocalDateTime(), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getBigDecimal(8),
                rs.getString(9), rs.getString(10), rs.getString(11));
    }

    /** Locks the customer row so two sales for the same customer cannot check gallons at the same time. */
    private static void lockCustomer(Connection con, int customerId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT customer_id FROM customers WHERE customer_id = ? AND is_archived = 0 FOR UPDATE")) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("This customer is archived or no longer exists.");
                }
            }
        }
    }

    /** Re-reads each product's price and stock (locked), checks the quantity, and takes the stock out. */
    private static List<OrderLine> priceAndTakeStock(Connection con, List<OrderLine> requested) throws SQLException {
        List<OrderLine> priced = new ArrayList<>();
        boolean pricesChanged = false;
        try (PreparedStatement lock = con.prepareStatement(
                "SELECT product_name, category, unit_price, stock_qty, is_active FROM products WHERE product_id = ? FOR UPDATE");
             PreparedStatement take = con.prepareStatement(
                "UPDATE products SET stock_qty = stock_qty - ? WHERE product_id = ?")) {
            for (OrderLine wanted : requested) {
                lock.setInt(1, wanted.getProductId());
                try (ResultSet rs = lock.executeQuery()) {
                    if (!rs.next() || !rs.getBoolean("is_active")) {
                        throw new IllegalArgumentException(
                                "A product in this order is no longer available. Remove it and try again.");
                    }
                    String name = rs.getString("product_name");
                    String category = rs.getString("category");
                    int stock = rs.getInt("stock_qty");
                    boolean tracked = !Product.REFILL.equals(category);
                    if (tracked && wanted.getQuantity() > stock) {
                        throw new IllegalArgumentException("Not enough stock for " + name + ". Only " + stock + " left.");
                    }
                    if (tracked) {
                        take.setInt(1, wanted.getQuantity());
                        take.setInt(2, wanted.getProductId());
                        take.executeUpdate();
                    }
                    BigDecimal currentPrice = rs.getBigDecimal("unit_price");
                    pricesChanged |= currentPrice.compareTo(wanted.getUnitPrice()) != 0;
                    priced.add(OrderLine.priced(wanted.getProductId(), name, wanted.getQuantity(), currentPrice));
                }
            }
        }
        if (pricesChanged) {
            // placeOrder rolls back any stock changes before returning the new prices to the screen.
            throw new PricesChangedException(priced);
        }
        return priced;
    }

    /** Gallons with the customer before this sale. */
    private static int gallonsOut(Connection con, int customerId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COALESCE(SUM(CASE WHEN movement = 'Borrowed' THEN quantity ELSE -quantity END), 0) "
                + "FROM container_ledger WHERE customer_id = ?")) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static int insertOrder(Connection con, int customerId, String orderType, String paymentType,
                                   int createdBy, BigDecimal total, String status) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO orders (customer_id, order_type, payment_type, total_amount, delivery_status, created_by) "
                + "VALUES (?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, customerId);
            ps.setString(2, orderType);
            ps.setString(3, paymentType);
            ps.setBigDecimal(4, total);
            ps.setString(5, status);
            ps.setInt(6, createdBy);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    private static void insertLines(Connection con, int orderId, List<OrderLine> lines) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO order_items (order_id, product_id, quantity, unit_price, line_total) VALUES (?, ?, ?, ?, ?)")) {
            for (OrderLine line : lines) {
                ps.setInt(1, orderId);
                ps.setInt(2, line.getProductId());
                ps.setInt(3, line.getQuantity());
                ps.setBigDecimal(4, line.getUnitPrice());
                ps.setBigDecimal(5, line.getLineTotal());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void insertMovement(Connection con, int customerId, int orderId, String movement, int quantity)
            throws SQLException {
        if (quantity <= 0) {
            return;
        }
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO container_ledger (customer_id, movement, quantity, order_id) VALUES (?, ?, ?, ?)")) {
            ps.setInt(1, customerId);
            ps.setString(2, movement);
            ps.setInt(3, quantity);
            ps.setInt(4, orderId);
            ps.executeUpdate();
        }
    }
}
