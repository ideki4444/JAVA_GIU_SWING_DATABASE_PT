package aquatrack.dao;

import aquatrack.db.Database;
import aquatrack.model.CustomerAccount;
import aquatrack.util.Fmt;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Gallons still with customers, unpaid credit balances, payments, and gallon returns or borrows that are not part of a sale. */
public final class LedgerDao {

    private static final String ACCOUNT_SELECT = "SELECT c.customer_id, c.full_name, c.contact_no, "
            + "(SELECT COALESCE(SUM(CASE WHEN l.movement = 'Borrowed' THEN l.quantity ELSE -l.quantity END), 0) "
            + "FROM container_ledger l WHERE l.customer_id = c.customer_id) AS gallons_out, "
            + "(SELECT COALESCE(SUM(o.total_amount), 0) FROM orders o "
            + "WHERE o.customer_id = c.customer_id AND o.payment_type = 'Credit') "
            + "- (SELECT COALESCE(SUM(p.amount), 0) FROM payments p WHERE p.customer_id = c.customer_id) AS unpaid, "
            + "c.is_archived FROM customers c ";
    private static final String ACCOUNT_SQL = ACCOUNT_SELECT + "WHERE c.is_archived = 0 ";

    /** An unpaid report row retains archive status without changing active-customer transaction rules. */
    public static final class UnpaidAccount {
        private final CustomerAccount account;
        private final boolean archived;

        public UnpaidAccount(CustomerAccount account, boolean archived) {
            this.account = account;
            this.archived = archived;
        }

        public CustomerAccount getAccount() { return account; }
        public boolean isArchived() { return archived; }
    }

    /** Debtors, including archived customers, ordered by balance and then name. */
    public List<UnpaidAccount> unpaidAccounts() throws SQLException {
        try (Connection con = Database.getConnection()) {
            return unpaidAccounts(con);
        }
    }

    // The connection overload supports deterministic report-query tests without a running database.
    List<UnpaidAccount> unpaidAccounts(Connection con) throws SQLException {
        String sql = "SELECT * FROM (" + ACCOUNT_SELECT + ") AS balances "
                + "WHERE unpaid > 0 ORDER BY unpaid DESC, full_name, customer_id";
        List<UnpaidAccount> accounts = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                accounts.add(new UnpaidAccount(read(rs), rs.getBoolean(6)));
            }
        }
        return accounts;
    }

    /** Every active customer with gallons out and their unpaid balance, sorted by name. */
    public List<CustomerAccount> accounts() throws SQLException {
        List<CustomerAccount> accounts = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(ACCOUNT_SQL + "ORDER BY c.full_name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                accounts.add(read(rs));
            }
        }
        return accounts;
    }

    /** One customer's position, or null if there is no such active customer. */
    public CustomerAccount account(int customerId) throws SQLException {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(ACCOUNT_SQL + "AND c.customer_id = ?")) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? read(rs) : null;
            }
        }
    }

    /** Records money paid toward a credit balance. The payment cannot be more than what is owed. */
    public void recordPayment(int customerId, BigDecimal amount) throws SQLException {
        CustomerAccount account = account(customerId);
        if (account == null) {
            throw new IllegalArgumentException("That customer no longer exists.");
        }
        if (account.getUnpaid().signum() <= 0) {
            throw new IllegalArgumentException(account.getFullName() + " has no unpaid balance.");
        }
        if (amount.compareTo(account.getUnpaid()) > 0) {
            throw new IllegalArgumentException("The payment is more than the unpaid balance of "
                    + Fmt.peso(account.getUnpaid()) + ".");
        }
        Database.update("INSERT INTO payments (customer_id, amount) VALUES (?, ?)", customerId, amount);
    }

    /** Records gallons returned or borrowed outside a sale. Returns cannot exceed what the customer has out. */
    public void recordMovement(int customerId, String movement, int quantity) throws SQLException {
        CustomerAccount account = account(customerId);
        if (account == null) {
            throw new IllegalArgumentException("That customer no longer exists.");
        }
        if ("Returned".equals(movement) && quantity > account.getGallonsOut()) {
            throw new IllegalArgumentException(account.getFullName() + " has " + account.getGallonsOut()
                    + " gallon(s) out, so " + quantity + " cannot be returned.");
        }
        Database.update("INSERT INTO container_ledger (customer_id, movement, quantity) VALUES (?, ?, ?)",
                customerId, movement, quantity);
    }

    private static CustomerAccount read(ResultSet rs) throws SQLException {
        return new CustomerAccount(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getBigDecimal(5));
    }
}
