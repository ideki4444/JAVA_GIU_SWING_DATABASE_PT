import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Automatically creates/updates the database schema.
 *
 * Creates:
 *  - users table if missing
 *  - climbers CRUD table if missing
 *  - missing columns if tables already exist
 */
public final class DatabaseSchema {

    private DatabaseSchema() {}

    public static void ensureSchema(Connection conn) throws SQLException {
        ensureUsersTable(conn);
        ensureClimbersTable(conn);
    }

    /* ------------------------------------------------------------ */
    /* USERS TABLE                                                  */
    /* ------------------------------------------------------------ */

    private static void ensureUsersTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS users ("
              + "id INT AUTO_INCREMENT PRIMARY KEY, "
              + "username VARCHAR(60) NOT NULL UNIQUE, "
              + "password VARCHAR(255) NOT NULL, "
              + "archived TINYINT(1) NOT NULL DEFAULT 0, "
              + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
              + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
              + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );
        }

        addColumnIfMissing(conn, "users", "archived",
                "TINYINT(1) NOT NULL DEFAULT 0 AFTER password");

        addColumnIfMissing(conn, "users", "created_at",
                "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER archived");

        addColumnIfMissing(conn, "users", "updated_at",
                "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at");

        /*
         * Optional starter account.
         *
         * If the users table is empty, this creates:
         *
         * Username: climber
         * Password: climb123
         *
         * Remove this block if you do not want automatic default login creation.
         */
        try {
            boolean empty = false;

            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users")) {
                if (rs.next()) {
                    empty = rs.getInt(1) == 0;
                }
            }

            if (empty) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO users (username, password, archived) VALUES (?, ?, 0)")) {
                    ps.setString(1, "climber");
                    ps.setString(2, "climb123");
                    ps.executeUpdate();
                }
            }
        } catch (SQLException ex) {
            /*
             * Do not block the app if the optional starter account cannot be inserted.
             * This can happen if an existing users table has extra required columns.
             */
            ex.printStackTrace();
        }
    }

    /* ------------------------------------------------------------ */
    /* CLIMBERS CRUD TABLE                                          */
    /* ------------------------------------------------------------ */

        private static void ensureClimbersTable(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(
                "CREATE TABLE IF NOT EXISTS climbers ("
              + "id INT AUTO_INCREMENT PRIMARY KEY, "
              + "full_name VARCHAR(120) NOT NULL, "
              + "username VARCHAR(60) NOT NULL, "
              + "email VARCHAR(120) NULL, "
              + "role VARCHAR(80) NULL, "
              + "base_camp VARCHAR(120) NULL, "
              + "skill_level VARCHAR(30) NULL, "
              + "status VARCHAR(20) NULL DEFAULT 'Active', "
              + "note VARCHAR(255) NULL, "
              + "archived TINYINT(1) NOT NULL DEFAULT 0, "
              + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
              + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
              + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );
        }

        addColumnIfMissing(conn, "climbers", "full_name",   "VARCHAR(120) NULL AFTER id");
        addColumnIfMissing(conn, "climbers", "username",    "VARCHAR(60) NULL AFTER full_name");
        addColumnIfMissing(conn, "climbers", "email",       "VARCHAR(120) NULL AFTER username");
        addColumnIfMissing(conn, "climbers", "role",        "VARCHAR(80) NULL AFTER email");
        addColumnIfMissing(conn, "climbers", "base_camp",   "VARCHAR(120) NULL AFTER role");
        addColumnIfMissing(conn, "climbers", "skill_level", "VARCHAR(30) NULL AFTER base_camp");
        addColumnIfMissing(conn, "climbers", "status",      "VARCHAR(20) NULL DEFAULT 'Active' AFTER skill_level");
        addColumnIfMissing(conn, "climbers", "note",        "VARCHAR(255) NULL AFTER status");
        addColumnIfMissing(conn, "climbers", "archived",    "TINYINT(1) NOT NULL DEFAULT 0 AFTER note");
        addColumnIfMissing(conn, "climbers", "created_at",  "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER archived");
        addColumnIfMissing(conn, "climbers", "updated_at",  "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at");
    }

    /* ------------------------------------------------------------ */
    /* HELPERS                                                      */
    /* ------------------------------------------------------------ */

    private static void addColumnIfMissing(Connection conn,
                                           String table,
                                           String column,
                                           String definition) throws SQLException {
        if (!columnExists(conn, table, column)) {
            try (Statement st = conn.createStatement()) {
                st.executeUpdate(
                    "ALTER TABLE `" + table + "` ADD COLUMN `" + column + "` " + definition
                );
            }
        }
    }

    private static boolean columnExists(Connection conn,
                                        String table,
                                        String column) throws SQLException {
        String sql =
            "SELECT COUNT(*) "
          + "FROM information_schema.columns "
          + "WHERE table_schema = DATABASE() "
          + "AND table_name = ? "
          + "AND column_name = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, column);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }
}