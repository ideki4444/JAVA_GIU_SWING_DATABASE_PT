package aquatrack.ui;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.SwingWorker;

/** Runs I/O off the event thread; callbacks always execute on the event thread. */
final class UiTask {
    private UiTask() { }
    static <T> SwingWorker<T, Void> run(Callable<T> work, Consumer<T> success, Consumer<Exception> failure) {
        SwingWorker<T, Void> worker = new SwingWorker<>() {
            protected T doInBackground() throws Exception { return work.call(); }
            protected void done() {
                try { success.accept(get()); }
                catch (CancellationException ignored) { }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); failure.accept(e); }
                catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    failure.accept(cause instanceof Exception exception ? exception : new Exception(cause));
                }
            }
        };
        worker.execute();
        return worker;
    }
}
