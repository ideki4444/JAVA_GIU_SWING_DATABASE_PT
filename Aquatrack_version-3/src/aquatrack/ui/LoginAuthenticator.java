package aquatrack.ui;

import aquatrack.model.User;
import aquatrack.util.PasswordUtil;
import java.sql.SQLException;

/** Resolves sign-in outcomes independently of the window and database connection. */
final class LoginAuthenticator {

    interface UserLookup {
        User findByUsername(String username) throws SQLException;
    }

    enum Outcome {
        INCOMPLETE("Enter both your username and password."),
        ACCOUNT_NOT_FOUND("Account doesn't exist"),
        INCORRECT_PASSWORD("Password is incorrect"),
        SUCCESS("Log-in Successfully");

        private final String message;

        Outcome(String message) {
            this.message = message;
        }

        String getMessage() {
            return message;
        }
    }

    static final class Result {
        private final Outcome outcome;
        private final User user;

        Result(Outcome outcome, User user) {
            this.outcome = outcome;
            this.user = user;
        }

        Outcome getOutcome() {
            return outcome;
        }

        User getUser() {
            return user;
        }
    }

    private final UserLookup users;

    LoginAuthenticator(UserLookup users) {
        this.users = users;
    }

    Result authenticate(String username, String password) throws SQLException {
        String trimmedUsername = username.trim();
        if (trimmedUsername.isEmpty() || password.isEmpty()) {
            return new Result(Outcome.INCOMPLETE, null);
        }
        User user = users.findByUsername(trimmedUsername);
        if (user == null || !user.isActive()) {
            return new Result(Outcome.ACCOUNT_NOT_FOUND, null);
        }
        if (!PasswordUtil.verify(password, user.getPasswordHash())) {
            return new Result(Outcome.INCORRECT_PASSWORD, null);
        }
        return new Result(Outcome.SUCCESS, user);
    }
}
