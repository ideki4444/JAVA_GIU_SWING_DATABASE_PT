package aquatrack.dao;

import aquatrack.db.Database;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only summaries for the dashboard and the reports screen. */
public final class ReportDao {

    /** Count and the sales totals, cash and credit, that follow a grouping column. */
    private static final String SALE_TOTALS = "COUNT(*), SUM(total_amount), "
            + "SUM(CASE WHEN payment_type = 'Cash' THEN total_amount ELSE 0 END), "
            + "SUM(CASE WHEN payment_type = 'Credit' THEN total_amount ELSE 0 END) ";

    public BigDecimal salesOn(LocalDate day) throws SQLException {
        String sql = "SELECT COALESCE(SUM(total_amount), 0) FROM orders WHERE order_date >= ? AND order_date < ?";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(day.atStartOfDay()));
            ps.setTimestamp(2, Timestamp.valueOf(day.plusDays(1).atStartOfDay()));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
            }
        }
    }

    /** Sales per day from first to last (both included). Days without sales are left out. */
    public Map<LocalDate, BigDecimal> salesBetween(LocalDate first, LocalDate last) throws SQLException {
        String sql = "SELECT DATE(order_date), SUM(total_amount) FROM orders "
                + "WHERE order_date >= ? AND order_date < ? GROUP BY DATE(order_date)";
        Map<LocalDate, BigDecimal> totals = new LinkedHashMap<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(first.atStartOfDay()));
            ps.setTimestamp(2, Timestamp.valueOf(last.plusDays(1).atStartOfDay()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    totals.put(rs.getDate(1).toLocalDate(), rs.getBigDecimal(2));
                }
            }
        }
        return totals;
    }

    public int countPendingDeliveries() throws SQLException {
        return Database.count("SELECT COUNT(*) FROM orders "
                + "WHERE order_type = 'Delivery' AND delivery_status <> 'Delivered'");
    }

    /** Gallons with customers right now: everything borrowed minus everything returned. */
    public int gallonsOut() throws SQLException {
        return Database.count("SELECT COALESCE(SUM(CASE WHEN movement = 'Borrowed' "
                + "THEN quantity ELSE -quantity END), 0) FROM container_ledger");
    }

    public int countLowStock() throws SQLException {
        return Database.count("SELECT COUNT(*) FROM products "
                + "WHERE is_active = 1 AND category <> 'Refill' AND stock_qty <= reorder_level");
    }

    /** Latest orders as rows: id, date, customer, type, payment, total, status. */
    public List<Object[]> recentOrders(int limit) throws SQLException {
        String sql = "SELECT o.order_id, o.order_date, c.full_name, o.order_type, o.payment_type, "
                + "o.total_amount, o.delivery_status FROM orders o "
                + "JOIN customers c ON c.customer_id = o.customer_id "
                + "ORDER BY o.order_date DESC, o.order_id DESC LIMIT ?";
        List<Object[]> rows = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Object[] {rs.getInt(1), rs.getTimestamp(2).toLocalDateTime(), rs.getString(3),
                            rs.getString(4), rs.getString(5), rs.getBigDecimal(6), rs.getString(7)});
                }
            }
        }
        return rows;
    }

    /** Each day with sales in the month: {LocalDate day, orders, total, cash, credit}. */
    public List<Object[]> dailySales(int year, int month) throws SQLException {
        LocalDate first = LocalDate.of(year, month, 1);
        String sql = "SELECT DATE(order_date), " + SALE_TOTALS
                + "FROM orders WHERE order_date >= ? AND order_date < ? "
                + "GROUP BY DATE(order_date) ORDER BY DATE(order_date)";
        return salesRows(sql, first, first.plusMonths(1), true);
    }

    /** Each month of the year that has sales: {Integer month, orders, total, cash, credit}. */
    public List<Object[]> monthlySales(int year) throws SQLException {
        String sql = "SELECT MONTH(order_date), " + SALE_TOTALS
                + "FROM orders WHERE order_date >= ? AND order_date < ? "
                + "GROUP BY MONTH(order_date) ORDER BY MONTH(order_date)";
        return salesRows(sql, LocalDate.of(year, 1, 1), LocalDate.of(year + 1, 1, 1), false);
    }

    /** Every sale as {customer id (Integer), name, contact, total}. The Ranking code totals them per customer. */
    public List<Object[]> customerSales() throws SQLException {
        String sql = "SELECT o.customer_id, c.full_name, c.contact_no, o.total_amount "
                + "FROM orders o JOIN customers c ON c.customer_id = o.customer_id";
        List<Object[]> rows = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new Object[] {rs.getInt(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4)});
            }
        }
        return rows;
    }

    private static List<Object[]> salesRows(String sql, LocalDate from, LocalDate to, boolean byDay)
            throws SQLException {
        List<Object[]> rows = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(from.atStartOfDay()));
            ps.setTimestamp(2, Timestamp.valueOf(to.atStartOfDay()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Object key = byDay ? rs.getDate(1).toLocalDate() : Integer.valueOf(rs.getInt(1));
                    rows.add(new Object[] {key, rs.getInt(2), rs.getBigDecimal(3), rs.getBigDecimal(4), rs.getBigDecimal(5)});
                }
            }
        }
        return rows;
    }
}
