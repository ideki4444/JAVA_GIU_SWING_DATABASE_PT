package aquatrack.util;

import aquatrack.model.User;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.SwingUtilities;

/** Remembers who is signed in while the program runs. */
public final class Session {

    private static volatile User current;
    private static final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();

    private Session() {
    }

    public static void start(User user) {
        current = user;
        notifyListeners();
    }

    public static void end() {
        current = null;
        notifyListeners();
    }

    /** Refresh saved details without reviving an ended session or replacing a different login. */
    public static void refresh(User user) {
        User signedIn = current;
        if (signedIn != null && user != null && signedIn.getId() == user.getId()) {
            current = user;
            notifyListeners();
        }
    }

    public static void addChangeListener(Runnable listener) { listeners.addIfAbsent(listener); }
    public static void removeChangeListener(Runnable listener) { listeners.remove(listener); }

    private static void notifyListeners() {
        Runnable notify = () -> listeners.forEach(Runnable::run);
        if (SwingUtilities.isEventDispatchThread()) notify.run();
        else SwingUtilities.invokeLater(notify);
    }

    public static User user() {
        return current;
    }

    public static boolean isOwner() {
        User signedIn = current;
        return signedIn != null && signedIn.isActive() && signedIn.isOwner();
    }
}
