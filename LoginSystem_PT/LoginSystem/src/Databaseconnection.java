import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Handles the JDBC connection to the MySQL database running under XAMPP.
 *
 * Change DB_NAME / USER / PASSWORD below if your setup is different.
 *
 * This version automatically creates the database if missing and
 * initializes required tables/columns through DatabaseSchema.
 */
public class Databaseconnection {

    private static final String DB_NAME = "login_db";

    private static final String URL =
        "jdbc:mysql://localhost:3306/" + DB_NAME
      + "?useSSL=false"
      + "&allowPublicKeyRetrieval=true"
      + "&serverTimezone=Asia/Manila"
      + "&createDatabaseIfNotExist=true";

    private static final String USER = "root";
    private static final String PASSWORD = ""; // default XAMPP MySQL root password is blank

    private static volatile boolean initialized = false;

    public static Connection getConnection() throws SQLException {
        ensureReady();
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    /**
     * Ensures database tables/columns exist once.
     */
    private static void ensureReady() throws SQLException {
        if (initialized) return;

        synchronized (Databaseconnection.class) {
            if (initialized) return;

            try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
                DatabaseSchema.ensureSchema(conn);
            }

            initialized = true;
        }
    }
}