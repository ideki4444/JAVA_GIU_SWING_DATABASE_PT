package aquatrack.dao;

import aquatrack.db.Database;
import aquatrack.model.Customer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** SQL for the customers table. Every value goes in as a ? parameter. */
public class CustomerDao {

    /** Which customers a list shows. */
    public enum Show {
        ACTIVE, ARCHIVED, ALL
    }

    private static final String SELECT_CUSTOMERS =
            "SELECT customer_id, full_name, address, contact_no, is_archived FROM customers ";

    /** Active customers whose name or contact number contains the text. Empty text returns everyone. */
    public List<Customer> search(String text) throws SQLException {
        return search(text, Show.ACTIVE);
    }

    /** Customers whose name or contact number contains the text, limited to the chosen archive status. */
    public List<Customer> search(String text, Show show) throws SQLException {
        String pattern = "%" + (text == null ? "" : text.trim()) + "%";
        String sql = SELECT_CUSTOMERS + "WHERE " + (show == Show.ALL ? "" : "is_archived = ? AND ")
                + "(full_name LIKE ? OR contact_no LIKE ?) ORDER BY is_archived, full_name";
        List<Customer> customers = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            int next = 1;
            if (show != Show.ALL) {
                ps.setInt(next++, show == Show.ARCHIVED ? 1 : 0);
            }
            ps.setString(next++, pattern);
            ps.setString(next, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    customers.add(read(rs));
                }
            }
        }
        return customers;
    }

    /** The customer, active or archived, already using this name (ignoring case and extra spaces), or null. */
    public Customer findByName(String fullName, int exceptCustomerId) throws SQLException {
        return findOne(SELECT_CUSTOMERS + "WHERE LOWER(TRIM(full_name)) = LOWER(TRIM(?)) AND customer_id <> ? LIMIT 1",
                fullName, exceptCustomerId);
    }

    /** The customer, active or archived, already using this contact number, or null. */
    public Customer findByContact(String contactNo, int exceptCustomerId) throws SQLException {
        return findOne(SELECT_CUSTOMERS + "WHERE contact_no = ? AND customer_id <> ? LIMIT 1",
                contactNo, exceptCustomerId);
    }

    public void insert(String fullName, String address, String contactNo) throws SQLException {
        Database.update("INSERT INTO customers (full_name, address, contact_no) VALUES (?, ?, ?)",
                fullName, address, contactNo);
    }

    public void update(int customerId, String fullName, String address, String contactNo) throws SQLException {
        Database.update("UPDATE customers SET full_name = ?, address = ?, contact_no = ? WHERE customer_id = ?",
                fullName, address, contactNo, customerId);
    }

    /** True when orders, payments, or container records already point at this customer. */
    public boolean hasHistory(int customerId) throws SQLException {
        String sql = "SELECT (SELECT COUNT(*) FROM orders WHERE customer_id = ?) "
                + "+ (SELECT COUNT(*) FROM payments WHERE customer_id = ?) "
                + "+ (SELECT COUNT(*) FROM container_ledger WHERE customer_id = ?)";
        return Database.count(sql, customerId, customerId, customerId) > 0;
    }

    /** Removes a customer who has no history. Callers check hasHistory first. */
    public void delete(int customerId) throws SQLException {
        Database.update("DELETE FROM customers WHERE customer_id = ?", customerId);
    }

    /** Keeps the history but hides the customer from new orders and the default list. */
    public void archive(int customerId) throws SQLException {
        setArchived(customerId, true);
    }

    /** Brings an archived customer back into use. */
    public void restore(int customerId) throws SQLException {
        setArchived(customerId, false);
    }

    private void setArchived(int customerId, boolean archived) throws SQLException {
        Database.update("UPDATE customers SET is_archived = ? WHERE customer_id = ?", archived ? 1 : 0, customerId);
    }

    private Customer findOne(String sql, Object... params) throws SQLException {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? read(rs) : null;
            }
        }
    }

    private static Customer read(ResultSet rs) throws SQLException {
        return new Customer(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5));
    }
}
