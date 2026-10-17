package aquatrack.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Connections and small query helpers for the local XAMPP MySQL server.
 * Each DAO method opens its own connection and closes it with try-with-resources.
 * Change the URL, user, or password here only if your XAMPP setup is different.
 */
public final class Database {

    private static final String URL = "jdbc:mysql://localhost:3306/aquatrack?serverTimezone=Asia/Manila";
    private static final String USER = "root";
    private static final String PASSWORD = "";

    private Database() {
    }

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("The MySQL connector JAR is missing. Put mysql-connector-j in the project's lib folder.", e);
        }
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    /** Runs an INSERT, UPDATE, or DELETE and returns how many rows changed. Values always go in as parameters. */
    public static int update(String sql, Object... params) throws SQLException {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, params);
            return ps.executeUpdate();
        }
    }

    /** Runs a query that returns a single number, such as COUNT or SUM. */
    public static int count(String sql, Object... params) throws SQLException {
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            if (params[i] == null) {
                ps.setNull(i + 1, java.sql.Types.VARCHAR);
            } else {
                ps.setObject(i + 1, params[i]);
            }
        }
    }
}
