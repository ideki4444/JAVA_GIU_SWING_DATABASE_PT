package aquatrack.dao;

import aquatrack.db.Database;
import aquatrack.model.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** SQL for the users table. Values always go in as ? parameters, never joined into the SQL text. */
public final class UserDao {

    /** The active account with this username, or null when there is none. */
    public User findByUsername(String username) throws SQLException {
        String sql = "SELECT user_id, username, full_name, role, password_hash FROM users "
                + "WHERE username = ? AND is_active = 1";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new User(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5));
            }
        }
    }

    /** Every account, active or not, for the Accounts screen. */
    public List<User> findAll() throws SQLException {
        String sql = "SELECT user_id, username, full_name, role, password_hash, is_active FROM users ORDER BY username";
        List<User> users = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                users.add(new User(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getBoolean(6)));
            }
        }
        return users;
    }

    /** The account, active or off, already using this full name (ignoring case and extra spaces), or null. */
    public User findByFullName(String fullName) throws SQLException {
        String sql = "SELECT user_id, username, full_name, role, password_hash, is_active FROM users "
                + "WHERE LOWER(TRIM(full_name)) = LOWER(TRIM(?)) LIMIT 1";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, fullName);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new User(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getBoolean(6));
            }
        }
    }

    public void insert(String username, String passwordHash, String fullName, String role) throws SQLException {
        Database.update("INSERT INTO users (username, password_hash, full_name, role) VALUES (?, ?, ?, ?)",
                username, passwordHash, fullName, role);
    }

    public void setActive(int userId, boolean active) throws SQLException {
        Database.update("UPDATE users SET is_active = ? WHERE user_id = ?", active ? 1 : 0, userId);
    }

    public void setPasswordHash(int userId, String passwordHash) throws SQLException {
        Database.update("UPDATE users SET password_hash = ? WHERE user_id = ?", passwordHash, userId);
    }

    public int countActiveOwners() throws SQLException {
        return Database.count("SELECT COUNT(*) FROM users WHERE role = 'Owner/Admin' AND is_active = 1");
    }
}
