package aquatrack.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Connections and small query helpers for the local XAMPP MySQL server.
 * Each DAO method opens its own connection and closes it with try-with-resources.
 * Override connection settings using aquatrack.db.* properties or AQUATRACK_DB_* environment variables.
 */
public final class Database {

    private static String setting(String property, String environment, String fallback) {
        String configured = System.getProperty(property);
        if (configured == null) configured = System.getenv(environment);
        return configured == null ? fallback : configured;
    }

    private Database() {
    }

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("The MySQL connector JAR is missing. Put mysql-connector-j in the project's lib folder.", e);
        }
        String url = setting("aquatrack.db.url", "AQUATRACK_DB_URL",
                "jdbc:mysql://localhost:3306/aquatrack?serverTimezone=Asia/Manila&connectTimeout=5000&socketTimeout=15000");
        return DriverManager.getConnection(url, setting("aquatrack.db.user", "AQUATRACK_DB_USER", "root"),
                setting("aquatrack.db.password", "AQUATRACK_DB_PASSWORD", ""));
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
