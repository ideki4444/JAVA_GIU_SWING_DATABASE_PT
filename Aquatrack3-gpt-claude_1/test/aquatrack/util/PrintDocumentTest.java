package aquatrack.util;

import aquatrack.model.CustomerOrder;
import aquatrack.model.OrderLine;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;

/** Standalone, headless checks. Optional argument writes sample page PNGs for visual inspection. */
public final class PrintDocumentTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        emptyReport();
        wrapsWithoutLoss();
        immutableSnapshot();
        multiPageRows();
        longDetailsAndHugeRow();
        pageFormatIsolation();
        printMatchesPreview();
        receiptRoll();
        receiptSnapshot(args.length == 0 ? null : Path.of(args[0]));
        System.out.println("PrintDocumentTest passed: " + checks + " checks.");
    }

    private static PrintDocument report(List<List<String>> rows) {
        return new PrintDocument("Daily sales - October 2026", Collections.emptyList(),
                Arrays.asList("Date", "Orders", "Total sales", "Cash", "Credit"), rows,
                Collections.singletonList("Total: PHP 1,200.00"), new double[] {2, 1, 2, 2, 2});
    }

    private static void emptyReport() {
        PrintDocument.Paginated pages = report(Collections.emptyList()).paginate(PrintDocument.a4(false));
        check(pages.getPageCount() == 1, "Empty report should have one informative page");
        check(pages.bodyText(0).contains("No records to display."), "Empty message is printed");
        check(pages.bodyText(0).contains("Total: PHP 1,200.00"), "Summary is printed even for empty data");
        check(pages.withinMargins(), "Empty report stays in the printable area");
    }

    private static void wrapsWithoutLoss() {
        Font font = new Font("SansSerif", Font.PLAIN, 10);
        String identifier = "LongIdentifier0123456789".repeat(90) + "\uD83D\uDE00";
        List<String> wrapped = PrintDocument.wrap(identifier, font, 95);
        check(String.join("", wrapped).equals(identifier), "Hard wrapping retains every code point");
        String phrase = "Barangay San Isidro, Tagbilaran City, Bohol 6300 Philippines";
        check(String.join(" ", PrintDocument.wrap(phrase, font, 100)).equals(phrase), "Word wrapping retains full address");
        check(PrintDocument.wrap("one\ntwo\n", font, 100).equals(Arrays.asList("one", "two", "")), "Explicit newlines retained");
    }

    private static void immutableSnapshot() {
        List<String> row = new ArrayList<>(Arrays.asList("Oct 1, 2026", "2", "PHP 50.00", "PHP 25.00", "PHP 25.00"));
        List<List<String>> rows = new ArrayList<>();
        rows.add(row);
        PrintDocument captured = report(rows);
        row.set(0, "CHANGED");
        rows.clear();
        PrintDocument.Paginated pages = captured.paginate(PrintDocument.a4(false));
        check(pages.bodyText(0).contains("Oct 1, 2026"), "Source row changes cannot change snapshot");
        check(!pages.bodyText(0).contains("CHANGED"), "No changed input leaks into snapshot");
    }

    private static void multiPageRows() {
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < 220; i++) rows.add(Arrays.asList("ROW_" + i, "2", "PHP 50.00", "PHP 25.00", "PHP 25.00"));
        for (boolean landscape : new boolean[] {false, true}) {
            PrintDocument.Paginated pages = report(rows).paginate(PrintDocument.a4(landscape));
            check(pages.getPageCount() > 2, "Large report produces multiple pages");
            List<String> printed = allText(pages);
            for (int i = 0; i < 220; i++) check(Collections.frequency(printed, "ROW_" + i) == 1, "Every row printed exactly once: " + i);
            for (int p = 0; p < pages.getPageCount() - 1; p++) {
                check(pages.bodyText(p).contains("Date") && pages.bodyText(p).contains("Orders"), "Table header repeats on page " + p);
            }
            check(Collections.frequency(printed, "Total: PHP 1,200.00") == 1, "Report summary appears exactly once");
            check(pages.withinMargins(), "All rows respect page margins");
        }
    }

    private static void longDetailsAndHugeRow() {
        String address = "ADDRESS_TOKEN ".repeat(300) + "ADDRESS_END";
        String product = "X".repeat(18000) + "PRODUCT_END";
        PrintDocument document = new PrintDocument("Long details", Collections.singletonList(address),
                Arrays.asList("Product", "Quantity"), Collections.singletonList(Arrays.asList(product, "19")),
                Collections.singletonList("SUMMARY_END"), new double[] {4, 1});
        for (boolean landscape : new boolean[] {false, true}) {
            PrintDocument.Paginated pages = document.paginate(PrintDocument.a4(landscape));
            String text = String.join("", allText(pages));
            check(text.contains("ADDRESS_END") && text.contains("PRODUCT_END") && text.contains("SUMMARY_END"), "Long details and final summary retained");
            long count = text.chars().filter(c -> c == 'X').count();
            check(count == 18000, "Oversized row splitting never truncates content");
            check(pages.getPageCount() > 4, "Oversized single row spans pages safely");
            check(pages.withinMargins(), "Oversized rows never cross footer");
        }
    }

    private static void pageFormatIsolation() {
        PageFormat input = PrintDocument.a4(false);
        PrintDocument.Paginated pages = report(Collections.emptyList()).paginate(input);
        input.setOrientation(PageFormat.LANDSCAPE);
        check(pages.getPageFormat().getOrientation() == PageFormat.PORTRAIT, "Layout copies source format");
        PageFormat returned = pages.getPageFormat();
        returned.setOrientation(PageFormat.LANDSCAPE);
        check(pages.getPageFormat().getOrientation() == PageFormat.PORTRAIT, "Layout protects returned format");
        check(PrintDocument.a4(true).getWidth() > PrintDocument.a4(true).getHeight(), "Landscape dimensions swap correctly");
        Paper tiny = new Paper();
        tiny.setImageableArea(0, 0, 50, 50);
        input.setPaper(tiny);
        boolean rejected = false;
        try { report(Collections.emptyList()).paginate(input); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Unsupported tiny pages fail cleanly instead of looping");
    }

    private static void printMatchesPreview() {
        for (boolean landscape : new boolean[] {false, true}) {
            PrintDocument.Paginated pages = report(Collections.singletonList(
                    Arrays.asList("Oct 21, 2026", "42", "PHP 1,250.00", "PHP 1,000.00", "PHP 250.00")))
                    .paginate(PrintDocument.a4(landscape));
            BufferedImage preview = pages.renderPage(0, 1);
            BufferedImage printed = new BufferedImage(preview.getWidth(), preview.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = printed.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, printed.getWidth(), printed.getHeight());
                check(pages.print(graphics, pages.getPageFormat(), 0) == Printable.PAGE_EXISTS, "First page prints");
                check(pages.print(graphics, pages.getPageFormat(), pages.getPageCount()) == Printable.NO_SUCH_PAGE, "Printer stops at last page");
                check(pages.print(graphics, pages.getPageFormat(), -1) == Printable.NO_SUCH_PAGE, "Negative pages rejected");
            } finally { graphics.dispose(); }
            check(Arrays.equals(preview.getRGB(0, 0, preview.getWidth(), preview.getHeight(), null, 0, preview.getWidth()),
                    printed.getRGB(0, 0, printed.getWidth(), printed.getHeight(), null, 0, printed.getWidth())),
                    "Preview pixels equal the actual Printable at the same scale");
            check(pages.renderPage(0, .5).getWidth() == (int) Math.ceil(pages.getPageFormat().getWidth() * .5), "Zoom scales paper without repagination");
        }
    }

    private static void receiptSnapshot(Path output) throws Exception {
        CustomerOrder order = new CustomerOrder(1042, LocalDateTime.of(2026, 10, 21, 14, 30),
                "Maria Teresa Dela Cruz", "Purok 4, Barangay San Isidro, Tagbilaran City, Bohol, Philippines",
                "0917 123 4567", "Delivery", "Credit", new BigDecimal("425.00"), "Pending", "Juan Santos", "Ana Reyes");
        List<OrderLine> lines = new ArrayList<>();
        lines.add(OrderLine.priced(1, "Purified water refill - 5 gallon slim container", 5, new BigDecimal("30.00")));
        lines.add(OrderLine.priced(2, "New reusable 5 gallon round water container", 1, new BigDecimal("150.00")));
        lines.add(OrderLine.priced(3, "Alkaline water refill - 5 gallon", 3, new BigDecimal("40.00")));
        lines.add(OrderLine.priced(4, "Container cap and tamper seal", 1, new BigDecimal("5.00")));
        order.setLines(lines);
        order.setGallons(5, 2);
        PrintDocument document = Receipt.document(order);
        lines.clear();
        order.setGallons(999, 999);
        PrintDocument.Paginated portrait = document.paginate(document.defaultPageFormat());
        List<String> text = allText(portrait);
        check(String.join(" ", text).contains("Containers borrowed: 5 | Returned: 2"), "Receipt snapshots container values");
        check(text.contains("TOTAL: \u20B1425.00"), "Receipt retains authoritative saved total");
        check(text.contains("Payment: Charged to account"), "Receipt distinguishes credit from cash");
        check(text.stream().anyMatch(s -> s.contains("Purified water refill")), "Receipt snapshots item lines");
        check(portrait.withinMargins(), "Receipt content fits");
        String legacyReceipt = Receipt.format(order);
        check(legacyReceipt.contains("Philippines"), "Plain-text receipt retains the end of a long address");
        String longProduct = "Z".repeat(150) + "PRODUCT_END";
        order.setLines(Collections.singletonList(OrderLine.priced(1, longProduct, 1, new BigDecimal("25.00"))));
        legacyReceipt = Receipt.format(order);
        check(legacyReceipt.chars().filter(c -> c == 'Z').count() == 150,
                "Plain-text receipt wraps long product tokens without truncating");
        if (output != null) {
            Files.createDirectories(output);
            ImageIO.write(portrait.renderPage(0, 2), "png", output.resolve("receipt-roll.png").toFile());
            List<List<String>> rows = new ArrayList<>();
            for (int i = 0; i < 55; i++) rows.add(Arrays.asList(Fmt.date(LocalDate.of(2026, 1, 1).plusDays(i)),
                    "42", "\u20B11,250.00", "\u20B11,000.00", "\u20B1250.00"));
            PrintDocument.Paginated report = new PrintDocument("Daily sales - Jan 1 to Feb 24, 2026", Collections.emptyList(),
                    Arrays.asList("Date", "Orders", "Total sales", "Cash", "Credit"), rows,
                    Collections.singletonList("2,310 orders | Total sales: \u20B168,750.00"), new double[] {2, 1, 2, 2, 2})
                    .paginate(PrintDocument.a4(false));
            ImageIO.write(report.renderPage(0, 1.5), "png", output.resolve("report-first.png").toFile());
            ImageIO.write(report.renderPage(report.getPageCount() - 1, 1.5), "png", output.resolve("report-last.png").toFile());
            ImageIO.write(new PrintDocument("Daily sales - October 2026", Collections.emptyList(),
                    Arrays.asList("Date", "Orders", "Total sales", "Cash", "Credit"), Collections.emptyList(),
                    Collections.singletonList("0 orders | Total sales: \u20B10.00"), new double[] {2, 1, 2, 2, 2})
                    .paginate(PrintDocument.a4(false)).renderPage(0, 1.5),
                    "png", output.resolve("report-empty.png").toFile());
        }
    }

    private static void receiptRoll() {
        PrintDocument empty = PrintDocument.receipt("Sales receipt #1", List.of(), List.of(), List.of("TOTAL: PHP 0.00"));
        check(empty.isReceipt(), "Receipt factory identifies receipt documents");
        check(!report(List.of()).isReceipt(), "Reports keep their separate document mode");
        PageFormat roll = empty.defaultPageFormat();
        check(Math.abs(roll.getWidth() - 80 * 72 / 25.4) < .001, "Default receipt paper is 80 mm wide");
        check(roll.getOrientation() == PageFormat.PORTRAIT, "Receipt roll has portrait orientation");
        PrintDocument.Paginated emptyPages = empty.paginate(roll);
        check(emptyPages.getPageCount() == 1 && emptyPages.bodyText(0).contains("No items to display."), "Empty receipt remains a single informative roll");
        check(roll.getHeight() < 200, "Empty receipt cuts shortly after the content");
        check(emptyPages.withinMargins(), "Empty roll preserves its margins");
        check(Math.abs(report(List.of()).defaultPageFormat().getWidth() - PrintDocument.a4(false).getWidth()) < .001,
                "Default report paper remains A4");

        List<String> row = new ArrayList<>(List.of("Refill - 5 gallon", "6", "PHP 25.00", "PHP 150.00"));
        List<List<String>> rows = new ArrayList<>();
        rows.add(row);
        PrintDocument receipt = PrintDocument.receipt("Sales receipt #24", List.of("Customer: Ana Lim"), rows,
                List.of("TOTAL: PHP 150.00", "Payment: Cash", "Thank you for your business!"));
        row.set(0, "CHANGED");
        rows.clear();
        PrintDocument.Paginated one = receipt.paginate(receipt.defaultPageFormat());
        check(one.getPageCount() == 1, "A normal receipt is one continuous page");
        check(one.bodyText(0).contains("Refill - 5 gallon") && !one.bodyText(0).contains("CHANGED"), "Receipt rows remain an immutable sale snapshot");
        check(one.bodyText(0).containsAll(List.of("6", "PHP 25.00", "PHP 150.00", "TOTAL: PHP 150.00")), "Receipt retains quantity, unit price, amount and total");
        check(one.getPageFormat().getHeight() > roll.getHeight(), "Receipt paper grows with its content");
        check(one.withinMargins(), "Single receipt stays inside paper margins");

        PageFormat requested = one.getPageFormat();
        Paper longer = requested.getPaper();
        longer.setSize(longer.getWidth(), longer.getHeight() + 40);
        longer.setImageableArea(longer.getImageableX(), longer.getImageableY(), longer.getImageableWidth(), longer.getImageableHeight() + 40);
        requested.setPaper(longer);
        PrintDocument.Paginated driverPaper = receipt.paginate(requested);
        check(Math.abs(driverPaper.getPageFormat().getHeight() - requested.getHeight()) < .001,
                "Longer printer-approved paper is retained to avoid validation loops");
        Paper narrow = requested.getPaper();
        double narrowWidth = 58 * 72 / 25.4;
        narrow.setSize(narrowWidth, 20);
        narrow.setImageableArea(6, 6, narrowWidth - 12, 8);
        requested.setPaper(narrow);
        PrintDocument.Paginated narrower = receipt.paginate(requested);
        check(Math.abs(narrower.getPageFormat().getWidth() - narrowWidth) < .001 && narrower.withinMargins(),
                "Requested narrower printable width wraps without clipping");
        check(Math.abs(narrower.getPageFormat().getImageableX() - 6) < .001,
                "Printer margins are honored");

        String longName = "Z".repeat(1500) + "NAME_END";
        String address = "LongAddress0123456789".repeat(45) + "ADDRESS_END";
        PrintDocument hugeText = PrintDocument.receipt("Sales receipt #25", List.of(address),
                List.of(List.of(longName, "123456", "PHP 123,456.78", "PHP 9,876,543.21")), List.of("END_TOTAL"));
        PrintDocument.Paginated wrapped = hugeText.paginate(hugeText.defaultPageFormat());
        String captured = String.join("", wrapped.bodyText(0));
        check(captured.contains(longName) && captured.contains(address), "Long words in item names and details are never truncated");
        check(captured.replace(" ", "").contains("PHP9,876,543.21") && captured.contains("END_TOTAL"), "Long numeric values and final totals remain present");
        check(wrapped.getPageCount() == 1 && wrapped.withinMargins(), "Very long details extend the roll instead of creating pages");

        List<List<String>> manyRows = new ArrayList<>();
        for (int i = 0; i < 1000; i++) manyRows.add(List.of("ITEM_" + i, "2", "PHP 25.00", "PHP 50.00"));
        PrintDocument many = PrintDocument.receipt("Sales receipt #26", List.of(), manyRows, List.of("TOTAL: PHP 50,000.00"));
        PrintDocument.Paginated longRoll = many.paginate(many.defaultPageFormat());
        List<String> longText = longRoll.bodyText(0);
        check(longRoll.getPageCount() == 1 && longRoll.withinMargins(), "A thousand items still occupy one complete roll");
        check(longText.stream().filter(s -> s.startsWith("ITEM_")).distinct().count() == 1000,
                "Every item appears exactly once on the long roll");
        check(Collections.frequency(longText, "PHP 50.00") == 1000 && longText.contains("TOTAL: PHP 50,000.00"),
                "The complete set of line amounts and saved total is preserved");
        boolean rejected = false;
        try { longRoll.renderPage(0, 3); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Oversized bitmap allocation fails safely; direct Printable remains available");
        BufferedImage viewport = new BufferedImage(240, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D viewportGraphics = viewport.createGraphics();
        try {
            viewportGraphics.setClip(0, 0, viewport.getWidth(), viewport.getHeight());
            check(longRoll.print(viewportGraphics, longRoll.getPageFormat(), 0) == Printable.PAGE_EXISTS,
                    "A clipped viewport can paint a long roll without a full-length bitmap");
        } finally { viewportGraphics.dispose(); }

        BufferedImage preview = one.renderPage(0, 1);
        BufferedImage printed = new BufferedImage(preview.getWidth(), preview.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = printed.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, printed.getWidth(), printed.getHeight());
            check(one.print(graphics, one.getPageFormat(), 0) == Printable.PAGE_EXISTS, "Receipt prints once");
            check(one.print(graphics, one.getPageFormat(), 1) == Printable.NO_SUCH_PAGE, "Receipt has no second page");
        } finally { graphics.dispose(); }
        int[] pixels = preview.getRGB(0, 0, preview.getWidth(), preview.getHeight(), null, 0, preview.getWidth());
        check(Arrays.equals(pixels, printed.getRGB(0, 0, printed.getWidth(), printed.getHeight(), null, 0, printed.getWidth())),
                "Receipt preview and Printable produce the same pixels");
        check(Arrays.stream(pixels).allMatch(rgb -> ((rgb >>> 16) & 255) == ((rgb >>> 8) & 255) && ((rgb >>> 8) & 255) == (rgb & 255)),
                "Receipt is monochrome for thermal and dot-matrix printers");
    }

    private static List<String> allText(PrintDocument.Paginated pages) {
        List<String> result = new ArrayList<>();
        for (int p = 0; p < pages.getPageCount(); p++) result.addAll(pages.bodyText(p));
        return result;
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
