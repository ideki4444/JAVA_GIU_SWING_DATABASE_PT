package aquatrack.dao;

import aquatrack.db.Database;
import aquatrack.model.CustomerAccount;
import aquatrack.util.Fmt;
import aquatrack.util.Validator;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Gallons still with customers, unpaid credit balances, payments, and gallon returns or borrows that are not part of a sale. */
public final class LedgerDao {

    interface Connections { Connection open() throws SQLException; }
    private final Connections connections;

    public LedgerDao() { this(Database::getConnection); }
    LedgerDao(Connections connections) { this.connections = connections; }

    /** Immutable acknowledgement returned only after the payment transaction commits. */
    public static final class PaymentResult {
        private final int paymentId;
        private final String customerName;
        private final BigDecimal amount, previousBalance, remainingBalance;
        private final LocalDateTime recordedAt;

        public PaymentResult(int paymentId, String customerName, BigDecimal amount,
                BigDecimal previousBalance, BigDecimal remainingBalance, LocalDateTime recordedAt) {
            this.paymentId = paymentId;
            this.customerName = customerName;
            this.amount = amount;
            this.previousBalance = previousBalance;
            this.remainingBalance = remainingBalance;
            this.recordedAt = recordedAt;
        }
        public int getPaymentId() { return paymentId; }
        public String getCustomerName() { return customerName; }
        public BigDecimal getAmount() { return amount; }
        public BigDecimal getPreviousBalance() { return previousBalance; }
        public BigDecimal getRemainingBalance() { return remainingBalance; }
        public LocalDateTime getRecordedAt() { return recordedAt; }
    }

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
        try (Connection con = connections.open()) {
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
        try (Connection con = connections.open();
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
        try (Connection con = connections.open()) {
            return account(con, customerId);
        }
    }

    private CustomerAccount account(Connection con, int customerId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(ACCOUNT_SQL + "AND c.customer_id = ?")) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? read(rs) : null;
            }
        }
    }

    /** Records money paid toward a credit balance. The payment cannot be more than what is owed. */
    public PaymentResult recordPayment(int customerId, BigDecimal amount) throws SQLException {
        BigDecimal checked = Validator.requireMoney(amount == null ? null : amount.toPlainString(), "Amount paid");
        if (checked.signum() <= 0) throw new IllegalArgumentException("Amount paid must be more than zero.");
        try (Connection con = connections.open()) {
            con.setAutoCommit(false);
            try {
                lockCustomer(con, customerId);
                CustomerAccount account = account(con, customerId);
                if (account == null) throw new IllegalArgumentException("That customer no longer exists.");
                if (account.getUnpaid().signum() <= 0) {
                    throw new IllegalArgumentException(account.getFullName() + " has no unpaid balance.");
                }
                if (checked.compareTo(account.getUnpaid()) > 0) {
                    throw new IllegalArgumentException("The payment is more than the unpaid balance of "
                            + Fmt.peso(account.getUnpaid()) + ".");
                }
                int paymentId;
                LocalDateTime recordedAt;
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO payments (customer_id, amount) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, customerId);
                    ps.setBigDecimal(2, checked);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("The payment reference was not returned.");
                        paymentId = keys.getInt(1);
                    }
                }
                // Read the database clock in the same transaction for a consistent receipt timestamp.
                try (PreparedStatement ps = con.prepareStatement("SELECT CURRENT_TIMESTAMP");
                     ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new SQLException("The payment time was not returned.");
                    recordedAt = rs.getTimestamp(1).toLocalDateTime();
                }
                PaymentResult result = new PaymentResult(paymentId, account.getFullName(), checked,
                        account.getUnpaid(), account.getUnpaid().subtract(checked), recordedAt);
                con.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                rollback(con, e);
                throw e;
            }
        }
    }

    /** Records gallons returned or borrowed outside a sale. Returns cannot exceed what the customer has out. */
    public void recordMovement(int customerId, String movement, int quantity) throws SQLException {
        if (!"Returned".equals(movement) && !"Borrowed".equals(movement)) {
            throw new IllegalArgumentException("Choose Returned or Borrowed for the container movement.");
        }
        if (quantity <= 0) throw new IllegalArgumentException("Number of gallons must be more than zero.");
        try (Connection con = connections.open()) {
            con.setAutoCommit(false);
            try {
                lockCustomer(con, customerId);
                CustomerAccount account = account(con, customerId);
                if (account == null) throw new IllegalArgumentException("That customer no longer exists.");
                if ("Returned".equals(movement) && quantity > account.getGallonsOut()) {
                    throw new IllegalArgumentException(account.getFullName() + " has " + account.getGallonsOut()
                            + " gallon(s) out, so " + quantity + " cannot be returned.");
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO container_ledger (customer_id, movement, quantity) VALUES (?, ?, ?)")) {
                    ps.setInt(1, customerId);
                    ps.setString(2, movement);
                    ps.setInt(3, quantity);
                    ps.executeUpdate();
                }
                con.commit();
            } catch (SQLException | RuntimeException e) {
                rollback(con, e);
                throw e;
            }
        }
    }

    private static void lockCustomer(Connection con, int customerId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT customer_id FROM customers WHERE customer_id = ? AND is_archived = 0 FOR UPDATE")) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("This customer is archived or no longer exists.");
            }
        }
    }

    private static void rollback(Connection con, Exception failure) {
        try { con.rollback(); } catch (SQLException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
    }

    private static CustomerAccount read(ResultSet rs) throws SQLException {
        return new CustomerAccount(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getBigDecimal(5));
    }
}
