package aquatrack.dao;

import aquatrack.db.Database;
import aquatrack.model.User;
import aquatrack.util.Validator;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Account mutations recheck the actor and owner protections under one deterministic lock. */
public final class UserDao {
    interface Connections { Connection open() throws SQLException; }
    private final Connections connections;
    private static final String COLUMNS = "user_id, username, full_name, role, password_hash, is_active";

    public UserDao() { this(Database::getConnection); }
    UserDao(Connections connections) { this.connections = connections; }

    public User findByUsername(String username) throws SQLException {
        try (Connection con = connections.open();
             PreparedStatement ps = con.prepareStatement("SELECT " + COLUMNS
                     + " FROM users WHERE username = ? AND is_active = 1")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? read(rs) : null; }
        }
    }

    public List<User> findAll() throws SQLException {
        try (Connection con = connections.open()) {
            return readAll(con, "SELECT " + COLUMNS + " FROM users ORDER BY username");
        }
    }

    /** Creates an account when userId is -1; editing never changes its password. */
    public User save(int actorId, int userId, String username, String fullName, String role,
            boolean active, String initialPasswordHash) throws SQLException {
        String cleanUsername = username == null ? "" : username.trim();
        if (!cleanUsername.matches("[A-Za-z0-9_]{3,50}")) {
            throw new IllegalArgumentException("Username must be 3 to 50 letters, numbers, or underscores.");
        }
        String cleanName = Validator.requireText(fullName, "Full name", 100).replaceAll("\\s+", " ");
        if (!"Staff".equals(role) && !"Owner/Admin".equals(role)) {
            throw new IllegalArgumentException("Choose Staff or Owner/Admin.");
        }
        boolean creating = userId == -1;
        if (creating && (initialPasswordHash == null || initialPasswordHash.isEmpty())) {
            throw new IllegalArgumentException("A password is required for a new account.");
        }
        try (Connection con = connections.open()) {
            con.setAutoCommit(false);
            try {
                List<User> locked = lockAndAuthorize(con, actorId);
                User previous = creating ? null : requireUser(locked, userId);
                if (!creating && actorId == userId && (!active || !"Owner/Admin".equals(role))) {
                    throw new IllegalArgumentException("You cannot turn off or demote the account you are signed in with.");
                }
                int ownersRemaining = 0;
                for (User user : locked) {
                    if (user.getId() != userId) {
                        if (user.isActive() && user.isOwner()) ownersRemaining++;
                        if (user.getUsername().equalsIgnoreCase(cleanUsername)) {
                            throw new IllegalArgumentException("That username is already taken.");
                        }
                        if (normalizedName(user.getFullName()).equals(normalizedName(cleanName))) {
                            throw new IllegalArgumentException("That full name already belongs to account "
                                    + user.getUsername() + ". Each account needs its own full name.");
                        }
                    }
                }
                if (active && "Owner/Admin".equals(role)) ownersRemaining++;
                if (ownersRemaining == 0) throw new IllegalArgumentException("Keep at least one active Owner/Admin account.");
                int savedId = userId;
                if (creating) {
                    try (PreparedStatement ps = con.prepareStatement("INSERT INTO users "
                            + "(username, password_hash, full_name, role, is_active) VALUES (?, ?, ?, ?, ?)",
                            Statement.RETURN_GENERATED_KEYS)) {
                        ps.setString(1, cleanUsername);
                        ps.setString(2, initialPasswordHash);
                        ps.setString(3, cleanName);
                        ps.setString(4, role);
                        ps.setBoolean(5, active);
                        ps.executeUpdate();
                        try (ResultSet keys = ps.getGeneratedKeys()) {
                            if (!keys.next()) throw new SQLException("The new account reference was not returned.");
                            savedId = keys.getInt(1);
                        }
                    }
                } else {
                    try (PreparedStatement ps = con.prepareStatement("UPDATE users SET username = ?, full_name = ?, "
                            + "role = ?, is_active = ? WHERE user_id = ?")) {
                        ps.setString(1, cleanUsername);
                        ps.setString(2, cleanName);
                        ps.setString(3, role);
                        ps.setBoolean(4, active);
                        ps.setInt(5, userId);
                        ps.executeUpdate();
                    }
                }
                User saved = new User(savedId, cleanUsername, cleanName, role,
                        creating ? initialPasswordHash : previous.getPasswordHash(), active);
                con.commit();
                return saved;
            } catch (SQLException | RuntimeException e) {
                rollback(con, e);
                throw e;
            }
        }
    }

    /** A separate action prevents profile edits from accidentally resetting a password. */
    public User resetPassword(int actorId, int userId, String passwordHash) throws SQLException {
        if (passwordHash == null || passwordHash.isEmpty()) throw new IllegalArgumentException("A new password is required.");
        try (Connection con = connections.open()) {
            con.setAutoCommit(false);
            try {
                User user = requireUser(lockAndAuthorize(con, actorId), userId);
                try (PreparedStatement ps = con.prepareStatement("UPDATE users SET password_hash = ? WHERE user_id = ?")) {
                    ps.setString(1, passwordHash);
                    ps.setInt(2, userId);
                    ps.executeUpdate();
                }
                User updated = new User(user.getId(), user.getUsername(), user.getFullName(), user.getRole(), passwordHash, user.isActive());
                con.commit();
                return updated;
            } catch (SQLException | RuntimeException e) {
                rollback(con, e);
                throw e;
            }
        }
    }

    private static List<User> lockAndAuthorize(Connection con, int actorId) throws SQLException {
        List<User> users = readAll(con, "SELECT " + COLUMNS + " FROM users ORDER BY user_id FOR UPDATE");
        User actor = null;
        for (User user : users) if (user.getId() == actorId) actor = user;
        if (actor == null || !actor.isActive() || !actor.isOwner()) {
            throw new IllegalArgumentException("Your account is no longer an active Owner/Admin. Sign in again before changing accounts.");
        }
        return users;
    }

    private static User requireUser(List<User> users, int userId) {
        for (User user : users) if (user.getId() == userId) return user;
        throw new IllegalArgumentException("That account no longer exists. Refresh the list and try again.");
    }

    private static String normalizedName(String name) {
        return name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static List<User> readAll(Connection con, String sql) throws SQLException {
        List<User> users = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) users.add(read(rs));
        }
        return users;
    }

    private static User read(ResultSet rs) throws SQLException {
        return new User(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getBoolean(6));
    }

    private static void rollback(Connection con, Exception failure) {
        try { con.rollback(); } catch (SQLException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
    }
}
