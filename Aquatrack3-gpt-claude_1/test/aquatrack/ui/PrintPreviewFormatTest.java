package aquatrack.ui;

import aquatrack.util.PrintDocument;
import java.awt.Dimension;
import java.awt.print.PageFormat;
import java.awt.print.Paper;

/** Printer setup changes must trigger a fresh preview before any pages are submitted. */
public final class PrintPreviewFormatTest {
    public static void main(String[] args) {
        PageFormat original = PrintDocument.a4(false);
        check(PrintPreviewDialog.isA4(original), "Portrait is A4");
        check(PrintPreviewDialog.isA4(PrintDocument.a4(true)), "Landscape is A4");
        check(PrintPreviewDialog.sameFormat(original, (PageFormat) original.clone()), "Unchanged settings reuse reviewed pages");
        check(!PrintPreviewDialog.sameFormat(original, PrintDocument.a4(true)), "Orientation change requires review");
        PageFormat margins = (PageFormat) original.clone();
        Paper paper = margins.getPaper();
        paper.setImageableArea(42, 42, paper.getWidth() - 84, paper.getHeight() - 84);
        margins.setPaper(paper);
        check(!PrintPreviewDialog.sameFormat(original, margins), "Printer margin change requires review");
        paper.setSize(612, 792);
        margins.setPaper(paper);
        check(!PrintPreviewDialog.isA4(margins), "Letter paper cannot silently replace A4");
        Dimension available = new Dimension(900, 600);
        double pageScale = PrintPreviewDialog.previewScale(0, available, original);
        double widthScale = PrintPreviewDialog.previewScale(1, available, original);
        check(widthScale > pageScale, "Fit width uses horizontal space even when the page must scroll vertically");
        check(Math.abs(widthScale * original.getWidth() - 860) < .01, "Fit width reserves the preview margins");
        check(Math.abs(pageScale * original.getHeight() - 560) < .01, "Fit page keeps the entire paper visible");
        check(PrintPreviewDialog.previewScale(2, available, original) == .5, "50 percent zoom is unchanged");
        check(PrintPreviewDialog.previewScale(6, available, original) == 1.5, "150 percent zoom is unchanged");
        System.out.println("PrintPreviewFormatTest passed: 11 checks.");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
