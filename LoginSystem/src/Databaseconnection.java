import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Handles the JDBC connection to the MySQL "users" database running under XAMPP.
 * Change DB_NAME / USER / PASSWORD below if your setup is different.
 */
public class Databaseconnection {

    private static final String DB_NAME = "login_db";

    // useSSL=false + allowPublicKeyRetrieval=true avoid the SSL warning/handshake
    // error that newer MySQL Connector/J versions throw against a default XAMPP server.
    // serverTimezone is set explicitly to avoid timezone-mismatch errors on some setups.
    private static final String URL =
            "jdbc:mysql://localhost:3306/" + DB_NAME
                    + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Manila";

    private static final String USER = "root";
    private static final String PASSWORD = ""; // default XAMPP MySQL root password is blank

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}