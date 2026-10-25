package aquatrack.ui;

import aquatrack.util.PrintDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.print.Book;
import java.awt.print.PageFormat;
import java.awt.print.PrinterAbortException;
import java.awt.print.PrinterJob;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.MediaSizeName;
import javax.print.attribute.standard.Media;
import javax.print.attribute.standard.MediaSize;
import javax.print.attribute.standard.MediaPrintableArea;
import javax.print.attribute.standard.OrientationRequested;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;

/** A paper-accurate preview: the immutable layout is also the printer's Printable. */
public final class PrintPreviewDialog extends JDialog {
    private final PrintDocument document;
    private final PrintPreviewToolbar toolbar;
    private final JComboBox<String> orientation, zoom;
    private final UiKit.ThemedButton previous, next;
    private final UiKit.ThemedButton print = UiKit.button("Print...", UiKit.ACCENT);
    private final UiKit.ThemedButton close = UiKit.button("Close", UiKit.SIDEBAR);
    private final UiKit.ThemedButton cancelPrint = UiKit.button("Cancel printing", UiKit.SIDEBAR);
    private final JLabel pageNumber;
    private final JLabel status = UiKit.muted(" ");
    private final PageCanvas canvas = new PageCanvas();
    private final JScrollPane scroll = new JScrollPane(canvas);
    private final Timer resizeTimer;
    private PageFormat requestedFormat;
    private PrintDocument.Paginated layout;
    private int pageIndex;
    private long requestNumber;
    private SwingWorker<Preview, Void> worker;
    private PrinterJob printerJob;
    private PrintRequestAttributeSet attributes;
    private boolean printing, submitting, cancellationRequested, settingOrientation, closed;

    private PrintPreviewDialog(Window owner, PrintDocument document, boolean fitWidth) {
        super(owner, "Print preview - " + document.getTitle(), Dialog.ModalityType.APPLICATION_MODAL);
        this.document = document;
        requestedFormat = document.defaultPageFormat();
        toolbar = new PrintPreviewToolbar(document.isReceipt());
        orientation = toolbar.orientation;
        zoom = toolbar.zoom;
        previous = toolbar.previous;
        next = toolbar.next;
        pageNumber = toolbar.pageNumber;
        zoom.setSelectedIndex(document.isReceipt() ? 4 : fitWidth ? 1 : 0);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        footer.setBackground(UiKit.SURFACE);
        footer.add(status, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(cancelPrint);
        actions.add(print);
        actions.add(close);
        footer.add(actions, BorderLayout.EAST);
        scroll.getViewport().setBackground(new Color(213, 222, 228));
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        setLayout(new BorderLayout());
        add(toolbar, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
        orientation.addActionListener(e -> {
            if (settingOrientation) return;
            requestedFormat = PrintDocument.a4(orientation.getSelectedIndex() == 1);
            printerJob = null;
            attributes = null;
            pageIndex = 0;
            layout = null;
            prepare("Preparing pages...");
        });
        zoom.addActionListener(e -> prepare("Preparing preview..."));
        toolbar.zoomOut.addActionListener(e -> adjustZoom(-1));
        toolbar.zoomIn.addActionListener(e -> adjustZoom(1));
        previous.addActionListener(e -> { pageIndex--; prepare("Preparing preview..."); });
        next.addActionListener(e -> { pageIndex++; prepare("Preparing preview..."); });
        print.addActionListener(e -> choosePrinter());
        cancelPrint.addActionListener(e -> {
            if (!submitting || cancellationRequested || printerJob == null) return;
            cancellationRequested = true;
            cancelPrint.setEnabled(false);
            printerJob.cancel();
            setStatus("Cancellation requested. Pages already sent may remain in the printer queue.");
        });
        close.addActionListener(e -> dispose());
        resizeTimer = new Timer(180, e -> { if (zoom.getSelectedIndex() <= 1 && !printing) prepare(null); });
        resizeTimer.setRepeats(false);
        scroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { resizeTimer.restart(); }
        });
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent e) {
                closed = true;
                requestNumber++;
                resizeTimer.stop();
                if (worker != null) worker.cancel(true);
            }
        });
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(document.isReceipt() ? 620 : 1080, screen.width - 30), Math.min(900, screen.height - 30));
        setMinimumSize(new Dimension(Math.min(document.isReceipt() ? 440 : 720, screen.width - 30), Math.min(500, screen.height - 30)));
        setLocationRelativeTo(owner);
        controls(false);
        prepare("Preparing pages...");
    }

    public static void showPreview(Component parent, PrintDocument document) {
        showPreview(parent, document, false);
    }

    /** Reports may use fit-to-width; receipts start at 100% and scroll along the roll. */
    public static void showPreview(Component parent, PrintDocument document, boolean fitWidth) {
        if (document == null) return;
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> showPreview(parent, document, fitWidth));
            return;
        }
        Window owner = parent instanceof Window ? (Window) parent : parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        new PrintPreviewDialog(owner, document, fitWidth).setVisible(true);
    }

    private void controls(boolean ready) {
        orientation.setEnabled(!printing);
        zoom.setEnabled(!printing);
        double scale = currentScale();
        toolbar.zoomOut.setEnabled(!printing && PrintPreviewToolbar.zoomStep(scale, -1) >= 0);
        toolbar.zoomIn.setEnabled(!printing && PrintPreviewToolbar.zoomStep(scale, 1) >= 0);
        previous.setEnabled(ready && !printing && pageIndex > 0);
        next.setEnabled(ready && !printing && layout != null && pageIndex + 1 < layout.getPageCount());
        print.setEnabled(ready && !printing);
        close.setEnabled(!printing);
        cancelPrint.setVisible(submitting);
        cancelPrint.setEnabled(submitting && !cancellationRequested);
        setDefaultCloseOperation(printing ? DO_NOTHING_ON_CLOSE : DISPOSE_ON_CLOSE);
    }

    private double currentScale() {
        return previewScale(zoom.getSelectedIndex(), scroll.getViewport().getExtentSize(),
                layout == null ? requestedFormat : layout.getPageFormat());
    }

    private void adjustZoom(int direction) {
        int index = PrintPreviewToolbar.zoomStep(currentScale(), direction);
        if (index >= 0) zoom.setSelectedIndex(index);
    }

    private void prepare(String message) {
        if (closed || printing) return;
        final long request = ++requestNumber;
        if (worker != null) worker.cancel(true);
        final PrintDocument.Paginated current = layout;
        final PageFormat format = (PageFormat) requestedFormat.clone();
        final int selectedPage = pageIndex;
        final int zoomIndex = zoom.getSelectedIndex();
        final Dimension available = scroll.getViewport().getExtentSize();
        controls(false);
        if (message != null) setStatus(message);
        worker = new SwingWorker<Preview, Void>() {
            @Override protected Preview doInBackground() {
                PrintDocument.Paginated result = current == null ? document.paginate(format) : current;
                int index = Math.min(selectedPage, result.getPageCount() - 1);
                PageFormat paper = result.getPageFormat();
                double scale = previewScale(zoomIndex, available, paper);
                return new Preview(result, index, scale);
            }
            @Override protected void done() {
                if (request != requestNumber || closed) return;
                worker = null;
                try {
                    Preview result = get();
                    layout = result.layout;
                    pageIndex = result.index;
                    canvas.show(result.layout, result.index, result.scale);
                    pageNumber.setText("Page " + (pageIndex + 1) + " of " + layout.getPageCount());
                    controls(true);
                    if (message != null && message.startsWith("Preparing")) setStatus(document.isReceipt()
                            ? "Review the receipt, then choose Print." : "Review the pages, then choose Print.");
                } catch (CancellationException e) {
                    setStatus("Preview cancelled.");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    setStatus("Preview interrupted. Change the page settings to try again.");
                } catch (ExecutionException e) {
                    setStatus("Could not prepare preview: " + e.getCause().getMessage());
                }
            }
        };
        worker.execute();
    }

    private void choosePrinter() {
        if (layout == null || printing || !print.isEnabled()) return;
        printing = true;
        controls(false);
        setStatus("Checking available printers...");
        final PrinterJob existing = printerJob;
        new SwingWorker<PrinterJob, Void>() {
            @Override protected PrinterJob doInBackground() {
                PrinterJob job = existing == null ? PrinterJob.getPrinterJob() : existing;
                return job.getPrintService() == null && PrinterJob.lookupPrintServices().length == 0 ? null : job;
            }
            @Override protected void done() {
                if (closed) return;
                try {
                    printerJob = get();
                    if (printerJob == null) {
                        finishPrinting("No printer is available. Connect a printer or install a PDF printer, then try again.");
                        return;
                    }
                    showPrintDialog();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    finishPrinting("Printer check interrupted.");
                } catch (ExecutionException | RuntimeException e) {
                    finishPrinting("Could not open the printer: " + rootMessage(e));
                }
            }
        }.execute();
    }

    private void showPrintDialog() {
        final PrintDocument.Paginated reviewed = layout;
        final PageFormat format = reviewed.getPageFormat();
        if (attributes == null) attributes = new HashPrintRequestAttributeSet();
        if (document.isReceipt()) attributes.remove(Media.class);
        else attributes.add(MediaSizeName.ISO_A4);
        attributes.add(format.getOrientation() == PageFormat.PORTRAIT
                ? OrientationRequested.PORTRAIT : OrientationRequested.LANDSCAPE);
        java.awt.print.Paper paper = format.getPaper();
        attributes.add(new MediaPrintableArea((float) (paper.getImageableX() / 72),
                (float) (paper.getImageableY() / 72), (float) (paper.getImageableWidth() / 72),
                (float) (paper.getImageableHeight() / 72), MediaPrintableArea.INCH));
        printerJob.setJobName(document.getTitle());
        installPrintable(reviewed, format);
        if (!printerJob.printDialog(attributes)) {
            finishPrinting("Printing cancelled. The saved data is unchanged.");
            return;
        }
        if (document.isReceipt()) {
            Media media = (Media) attributes.get(Media.class);
            if (media instanceof MediaSizeName name) {
                MediaSize size = MediaSize.getMediaSizeForName(name);
                if (size != null && Math.abs(size.getX(MediaSize.MM) - 80) > 1) {
                    finishPrinting("Choose 80 mm receipt paper in the printer properties to match this preview.");
                    return;
                }
            }
            // A pageable job keeps the custom roll size; a standard Media attribute would replace it.
            attributes.remove(Media.class);
            attributes.add(OrientationRequested.PORTRAIT);
        }
        final PageFormat chosen = printerJob.validatePage(document.isReceipt() ? format : printerJob.getPageFormat(attributes));
        // Both preview choices use standard landscape, so normalize reverse-landscape driver settings.
        if (chosen.getOrientation() == PageFormat.REVERSE_LANDSCAPE) chosen.setOrientation(PageFormat.LANDSCAPE);
        attributes.add(chosen.getOrientation() == PageFormat.PORTRAIT
                ? OrientationRequested.PORTRAIT : OrientationRequested.LANDSCAPE);
        if (document.isReceipt() && !supportsReceipt(format, chosen)) {
            finishPrinting("This printer's paper settings cannot fit the receipt. Choose an 80 mm roll with sufficient paper length in the printer properties.");
            return;
        }
        if (!document.isReceipt() && !isA4(chosen)) {
            finishPrinting("Choose A4 paper in the printer settings to match this preview.");
            return;
        }
        if (!sameFormat(format, chosen)) {
            requestedFormat = chosen;
            settingOrientation = true;
            orientation.setSelectedIndex(chosen.getOrientation() == PageFormat.PORTRAIT ? 0 : 1);
            settingOrientation = false;
            layout = null;
            pageIndex = 0;
            printing = false;
            prepare("Printer settings changed the layout. Review these pages, then choose Print again.");
            return;
        }
        installPrintable(reviewed, format);
        submitting = true;
        cancellationRequested = false;
        controls(false);
        setStatus("Sending pages to the printer...");
        final PrinterJob job = printerJob;
        final PrintRequestAttributeSet submitted = new HashPrintRequestAttributeSet(attributes);
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                job.print(submitted);
                return null;
            }
            @Override protected void done() {
                try {
                    get();
                    finishPrinting(cancellationRequested || job.isCancelled()
                            ? "Printing cancelled. Pages already sent may remain in the printer queue."
                            : "Pages sent to the printer.");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    finishPrinting("Printing interrupted. Check the printer queue before trying again.");
                } catch (ExecutionException e) {
                    finishPrinting(e.getCause() instanceof PrinterAbortException ? "Printing cancelled."
                            : "Printing failed: " + rootMessage(e));
                }
            }
        }.execute();
    }

    private void installPrintable(PrintDocument.Paginated reviewed, PageFormat format) {
        if (document.isReceipt()) {
            Book roll = new Book();
            roll.append(reviewed, format, 1);
            printerJob.setPageable(roll);
        } else printerJob.setPrintable(reviewed, format);
    }

    static boolean supportsReceipt(PageFormat required, PageFormat chosen) {
        return chosen.getOrientation() == PageFormat.PORTRAIT
                && Math.abs(chosen.getWidth() - 80 * 72 / 25.4) < 2
                && chosen.getHeight() + .1 >= required.getHeight();
    }

    private void finishPrinting(String message) {
        if (cancellationRequested || printerJob != null && printerJob.isCancelled()) printerJob = null;
        printing = false;
        submitting = false;
        cancellationRequested = false;
        controls(layout != null);
        setStatus(message);
    }
    private void setStatus(String message) {
        int actionWidth = print.getParent() == null ? 240 : print.getParent().getPreferredSize().width;
        status.setText(UiKit.wrapped(UiKit.escapeHtml(message), Math.max(120, getWidth() - actionWidth - 54)));
    }
    private static String rootMessage(Exception e) {
        Throwable cause = e.getCause() == null ? e : e.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
    static boolean isA4(PageFormat format) {
        return Math.abs(format.getPaper().getWidth() - 210 * 72 / 25.4) < 2
                && Math.abs(format.getPaper().getHeight() - 297 * 72 / 25.4) < 2;
    }
    static boolean sameFormat(PageFormat a, PageFormat b) {
        return a.getOrientation() == b.getOrientation() && near(a.getWidth(), b.getWidth())
                && near(a.getHeight(), b.getHeight()) && near(a.getImageableX(), b.getImageableX())
                && near(a.getImageableY(), b.getImageableY()) && near(a.getImageableWidth(), b.getImageableWidth())
                && near(a.getImageableHeight(), b.getImageableHeight());
    }
    private static boolean near(double a, double b) { return Math.abs(a - b) < .1; }

    static double previewScale(int zoomIndex, Dimension available, PageFormat paper) {
        double width = Math.max(200, available.width - 40) / paper.getWidth();
        double scale = zoomIndex == 0 ? Math.min(width, Math.max(200, available.height - 40) / paper.getHeight())
                : zoomIndex == 1 ? width : new double[] {.5, .75, 1, 1.25, 1.5}[zoomIndex - 2];
        return Math.min(2, Math.max(.0001, scale));
    }

    private static final class Preview {
        final PrintDocument.Paginated layout;
        final int index;
        final double scale;
        Preview(PrintDocument.Paginated layout, int index, double scale) {
            this.layout = layout; this.index = index; this.scale = scale;
        }
    }
    static final class PageCanvas extends JPanel {
        private PrintDocument.Paginated layout;
        private int page;
        private double scale;
        private int paperWidth, paperHeight;
        PageCanvas() { setBackground(new Color(213, 222, 228)); }
        void show(PrintDocument.Paginated layout, int page, double scale) {
            this.layout = layout;
            this.page = page;
            this.scale = scale;
            PageFormat format = layout.getPageFormat();
            paperWidth = (int) Math.min(Integer.MAX_VALUE - 40, Math.ceil(format.getWidth() * scale));
            paperHeight = (int) Math.min(Integer.MAX_VALUE - 40, Math.ceil(format.getHeight() * scale));
            setPreferredSize(new Dimension(paperWidth + 40, paperHeight + 40));
            revalidate(); repaint();
        }
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (layout == null) return;
            int x = Math.max(20, (getWidth() - paperWidth) / 2);
            graphics.setColor(new Color(155, 169, 177));
            graphics.fillRect(x + 4, 24, paperWidth, paperHeight);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(x, 20, paperWidth, paperHeight);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.translate(x, 20);
                g.scale(scale, scale);
                // Paint only through the viewport clip, without a full-length roll bitmap.
                layout.print(g, layout.getPageFormat(), page);
            } finally { g.dispose(); }
        }
    }
}
