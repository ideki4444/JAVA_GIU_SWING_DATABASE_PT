package aquatrack.ui;

import aquatrack.util.PrintDocument;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.util.List;
import javax.swing.SwingUtilities;

/** Receipt control visibility, zoom stepping, printer-size checks and shared preview painting. */
public final class ReceiptPreviewTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PrintPreviewToolbar receipt = new PrintPreviewToolbar(true);
            check(!contains(receipt, receipt.orientation), "receipt has no paper orientation control");
            check(!contains(receipt, receipt.previous) && !contains(receipt, receipt.next), "receipt has no page buttons");
            check(!contains(receipt, receipt.pageNumber), "continuous receipt needs no page counter");
            Container settings = receipt.zoom.getParent();
            check(settings.getComponent(1) == receipt.zoomOut && settings.getComponent(2) == receipt.zoom
                    && settings.getComponent(3) == receipt.zoomIn, "minus, combo, plus order");
            check(receipt.zoom.getItemCount() == 7, "existing zoom choices preserved");
            PrintPreviewToolbar report = new PrintPreviewToolbar(false);
            check(contains(report, report.orientation) && contains(report, report.previous) && contains(report, report.next),
                    "reports retain A4 orientation and pagination");
            check(PrintPreviewToolbar.zoomStep(1, -1) == 3, "minus goes from100 to75");
            check(PrintPreviewToolbar.zoomStep(1, 1) == 5, "plus goes from100 to125");
            check(PrintPreviewToolbar.zoomStep(.63, -1) == 2 && PrintPreviewToolbar.zoomStep(.63, 1) == 3,
                    "fit mode steps to adjacent percentage");
            check(PrintPreviewToolbar.zoomStep(.5, -1) == -1 && PrintPreviewToolbar.zoomStep(1.5, 1) == -1,
                    "zoom endpoints cannot overflow choices");
            PrintDocument doc = PrintDocument.receipt("Sales receipt #100", List.of("Customer: Demo customer"),
                    List.of(List.of("5-gallon refill", "6", "PHP25.00", "PHP150.00")), List.of("TOTAL: PHP150.00"));
            PrintDocument.Paginated pages = doc.paginate(doc.defaultPageFormat());
            PageFormat format = pages.getPageFormat();
            check(PrintPreviewDialog.supportsReceipt(format, format), "80mm roll accepted");
            check(!PrintPreviewDialog.supportsReceipt(format, PrintDocument.a4(false)), "A4 cannot replace receipt silently");
            PageFormat shortPaper = (PageFormat) format.clone();
            Paper paper = shortPaper.getPaper();
            paper.setSize(paper.getWidth(), paper.getHeight() - 20);
            shortPaper.setPaper(paper);
            check(!PrintPreviewDialog.supportsReceipt(format, shortPaper), "short printer paper rejected");
            PrintPreviewDialog.PageCanvas canvas = new PrintPreviewDialog.PageCanvas();
            canvas.show(pages, 0, 1);
            canvas.setSize(canvas.getPreferredSize());
            BufferedImage actual = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = actual.createGraphics();
            canvas.paint(g);
            g.dispose();
            BufferedImage expected = pages.renderPage(0, 1);
            boolean identical = true;
            for (int y = 0; y < expected.getHeight() && identical; y++) {
                for (int x = 0; x < expected.getWidth(); x++) {
                    if (expected.getRGB(x, y) != actual.getRGB(x + 20, y + 20)) { identical = false; break; }
                }
            }
            check(identical, "preview paper matches Printable raster pixel for pixel");
            try {
                javax.imageio.ImageIO.write(actual, "png", java.nio.file.Path.of("build/qa/receipt-preview-canvas.png").toFile());
                LoginAndThemeTest.render(receipt, 600, 60, 1, "receipt-preview-controls.png");
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        System.out.println("ReceiptPreviewTest passed: " + checks + " checks.");
    }
    private static boolean contains(Container parent, Component target) {
        for (Component child : parent.getComponents()) {
            if (child == target || child instanceof Container nested && contains(nested, target)) return true;
        }
        return false;
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
