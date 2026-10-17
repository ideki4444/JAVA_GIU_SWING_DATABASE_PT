package aquatrack.util;

import aquatrack.model.User;

/** Remembers who is signed in while the program runs. */
public final class Session {

    private static User current;

    private Session() {
    }

    public static void start(User user) {
        current = user;
    }

    public static void end() {
        current = null;
    }

    public static User user() {
        return current;
    }

    public static boolean isOwner() {
        return current != null && current.isOwner();
    }
}
