package aquatrack.ui;

import aquatrack.model.User;
import aquatrack.util.PasswordUtil;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/** Standalone regression checks; no database, desktop display, or test library required. */
public final class LoginValidationTest {

    private LoginValidationTest() {
    }

    public static void main(String[] args) throws Exception {
        String correctPassword = "Example password 123";
        User active = new User(1, "owner", "Example Owner", "Owner/Admin",
                PasswordUtil.hash(correctPassword));
        User inactive = new User(2, "inactive", "Inactive Owner", "Owner/Admin",
                active.getPasswordHash(), false);
        AtomicInteger lookups = new AtomicInteger();
        LoginAuthenticator authenticator = new LoginAuthenticator(username -> {
            lookups.incrementAndGet();
            if ("owner".equals(username)) {
                return active;
            }
            return "inactive".equals(username) ? inactive : null;
        });

        // The password cannot identify an account when its username was not found.
        verifyAttempt(authenticator, "unknown", correctPassword,
                LoginAuthenticator.Outcome.ACCOUNT_NOT_FOUND, "Account doesn't exist", "", "", null);
        verifyAttempt(authenticator, "unknown", "wrong password",
                LoginAuthenticator.Outcome.ACCOUNT_NOT_FOUND, "Account doesn't exist", "", "", null);
        verifyAttempt(authenticator, "owner", "wrong password",
                LoginAuthenticator.Outcome.INCORRECT_PASSWORD, "Password is incorrect", "owner", "", null);
        verifyAttempt(authenticator, "owner", correctPassword,
                LoginAuthenticator.Outcome.SUCCESS, "Log-in Successfully", "owner", correctPassword, active);
        verifyAttempt(authenticator, "  owner  ", correctPassword,
                LoginAuthenticator.Outcome.SUCCESS, "Log-in Successfully", "  owner  ", correctPassword, active);
        verifyAttempt(authenticator, "inactive", correctPassword,
                LoginAuthenticator.Outcome.ACCOUNT_NOT_FOUND, "Account doesn't exist", "", "", null);
        check(lookups.get() == 6, "Each complete attempt should look up only its username");

        verifyAttempt(authenticator, "   ", correctPassword,
                LoginAuthenticator.Outcome.INCOMPLETE, "Enter both your username and password.",
                "   ", correctPassword, null);
        verifyAttempt(authenticator, "owner", "",
                LoginAuthenticator.Outcome.INCOMPLETE, "Enter both your username and password.",
                "owner", "", null);
        check(lookups.get() == 6, "Incomplete attempts should not query accounts");

        SQLException unavailable = new SQLException("Database unavailable");
        LoginAuthenticator failing = new LoginAuthenticator(username -> {
            throw unavailable;
        });
        try {
            failing.authenticate("owner", correctPassword);
            throw new AssertionError("A database error must not become an invalid-account result");
        } catch (SQLException expected) {
            check(expected == unavailable, "Preserve the database error for the window to report");
        }
        System.out.println("PASS: login messages, credential clearing, successful user, inactive account, "
                + "username trimming, incomplete input, and database errors");
    }

    private static void verifyAttempt(LoginAuthenticator authenticator, String username, String password,
                                      LoginAuthenticator.Outcome expectedOutcome, String expectedMessage,
                                      String expectedUsername, String expectedPassword, User expectedUser)
            throws Exception {
        LoginAuthenticator.Result result = authenticator.authenticate(username, password);
        check(result.getOutcome() == expectedOutcome, "Unexpected outcome for " + username);
        check(expectedMessage.equals(result.getOutcome().getMessage()), "Exact login message changed");
        check(result.getUser() == expectedUser, "Only successful authentication should return a user");

        // Exercise the same clearing method used by the SwingWorker completion callback.
        SwingUtilities.invokeAndWait(() -> {
            JTextField usernameField = new JTextField(username);
            JPasswordField passwordField = new JPasswordField(password);
            LoginFrame.clearRejectedCredentials(result.getOutcome(), usernameField, passwordField);
            check(expectedUsername.equals(usernameField.getText()), "Unexpected username field contents");
            char[] remainingPassword = passwordField.getPassword();
            try {
                check(Arrays.equals(expectedPassword.toCharArray(), remainingPassword),
                        "Unexpected password field contents");
            } finally {
                Arrays.fill(remainingPassword, '\0');
            }
        });
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
