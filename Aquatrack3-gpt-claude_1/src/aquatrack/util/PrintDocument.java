package aquatrack.util;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable print data. Pagination and painting use no Swing components or database access. */
public final class PrintDocument {
    private static final Font BODY = new Font("SansSerif", Font.PLAIN, 10);
    private static final Font BOLD = BODY.deriveFont(Font.BOLD);
    private static final Font TITLE = BODY.deriveFont(Font.BOLD, 16f);
    private static final Font SMALL = BODY.deriveFont(8f);
    private static final Font RECEIPT_BODY = new Font("Monospaced", Font.PLAIN, 9);
    private static final Font RECEIPT_BOLD = RECEIPT_BODY.deriveFont(Font.BOLD);
    private static final Font RECEIPT_TITLE = RECEIPT_BOLD.deriveFont(10f);
    private static final double RECEIPT_LINE = 12;
    private static final FontRenderContext METRICS = new FontRenderContext(null, true, true);
    private static final double LINE = 14, PAD = 7;
    private final String title;
    private final List<String> details;
    private final List<String> columns;
    private final List<List<String>> rows;
    private final List<String> summary;
    private final double[] weights;
    private final boolean receipt;

    public PrintDocument(String title, List<String> details, List<String> columns,
                         List<? extends List<String>> rows, List<String> summary, double[] weights) {
        this(title, details, columns, rows, summary, weights, false);
    }

    private PrintDocument(String title, List<String> details, List<String> columns,
                          List<? extends List<String>> rows, List<String> summary, double[] weights,
                          boolean receipt) {
        this.receipt = receipt;
        this.title = clean(title);
        this.details = copy(details);
        this.columns = copy(columns);
        this.summary = copy(summary);
        if (columns.isEmpty() || weights.length != columns.size()) {
            throw new IllegalArgumentException("A print table needs columns and matching widths.");
        }
        this.weights = weights.clone();
        for (double weight : this.weights) {
            if (!Double.isFinite(weight) || weight <= 0) {
                throw new IllegalArgumentException("Column widths must be positive.");
            }
        }
        List<List<String>> copied = new ArrayList<>();
        for (List<String> row : rows) {
            if (row.size() != columns.size()) {
                throw new IllegalArgumentException("A print row must match the column count.");
            }
            copied.add(copy(row));
        }
        this.rows = Collections.unmodifiableList(copied);
    }

    private static List<String> copy(List<String> values) {
        List<String> copied = new ArrayList<>();
        for (String value : values) copied.add(clean(value));
        return Collections.unmodifiableList(copied);
    }

    private static String clean(String text) { return text == null ? "" : text.replace('\t', ' '); }
    public String getTitle() { return title; }
    public boolean isReceipt() { return receipt; }

    /** A receipt has a full-width item name followed by centered quantity, price and amount. */
    public static PrintDocument receipt(String title, List<String> details,
                                        List<? extends List<String>> rows, List<String> summary) {
        return new PrintDocument(title, details, List.of("Item", "Qty", "Unit price", "Amount"),
                rows, summary, new double[] {4, 1, 2, 2}, true);
    }

    /** Reports use A4. Receipts use an 80 mm roll whose length fits this captured sale. */
    public PageFormat defaultPageFormat() {
        if (!receipt) return a4(false);
        double width = 80 * 72 / 25.4, margin = 3 * 72 / 25.4;
        Paper paper = new Paper();
        paper.setSize(width, 20);
        paper.setImageableArea(margin, margin, width - margin * 2, 20 - margin * 2);
        PageFormat seed = new PageFormat();
        seed.setPaper(paper);
        return new ReceiptLayout(seed).build().getPageFormat();
    }

    /** A4 with half-inch margins, in points. The same format is passed to preview and printer. */
    public static PageFormat a4(boolean landscape) {
        Paper paper = new Paper();
        double width = 210 * 72 / 25.4, height = 297 * 72 / 25.4;
        paper.setSize(width, height);
        paper.setImageableArea(36, 36, width - 72, height - 72);
        PageFormat format = new PageFormat();
        format.setPaper(paper);
        format.setOrientation(landscape ? PageFormat.LANDSCAPE : PageFormat.PORTRAIT);
        return format;
    }

    public Paginated paginate(PageFormat format) {
        return receipt ? new ReceiptLayout(format).build() : new Layout(format).build();
    }

    /** A finished, immutable page layout, safe for concurrent preview and printer rendering. */
    public final class Paginated implements Printable {
        private final PageFormat format;
        private final List<List<Band>> pages;
        private final List<String> headings;
        private final double[] widths;
        private final List<ReceiptBand> receiptBands;

        private Paginated(PageFormat format, List<List<Band>> pages, List<String> headings, double[] widths) {
            this.format = (PageFormat) format.clone();
            this.pages = pages;
            this.headings = headings;
            this.widths = widths.clone();
            this.receiptBands = null;
        }

        private Paginated(PageFormat format, List<ReceiptBand> bands) {
            this.format = (PageFormat) format.clone();
            this.pages = Collections.singletonList(Collections.emptyList());
            this.headings = Collections.emptyList();
            this.widths = new double[0];
            this.receiptBands = List.copyOf(bands);
        }

        public int getPageCount() { return pages.size(); }
        public PageFormat getPageFormat() { return (PageFormat) format.clone(); }

        @Override public int print(Graphics graphics, PageFormat ignored, int pageIndex) {
            if (pageIndex < 0 || pageIndex >= pages.size()) return NO_SUCH_PAGE;
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                g.translate(format.getImageableX(), format.getImageableY());
                double width = format.getImageableWidth();
                if (receiptBands != null) {
                    g.setColor(Color.BLACK);
                    for (ReceiptBand band : receiptBands) band.paint(g, width);
                    return PAGE_EXISTS;
                }
                g.setColor(new Color(23, 64, 79));
                draw(g, Receipt.STATION_NAME, SMALL, 0, 9, width, false);
                double y = 31;
                for (String heading : headings) {
                    draw(g, heading, TITLE, 0, y, width, false);
                    y += 20;
                }
                g.setColor(new Color(171, 192, 199));
                g.drawLine(0, (int) (y - 9), (int) width, (int) (y - 9));
                for (Band band : pages.get(pageIndex)) band.paint(g, width, widths);
                double bottom = format.getImageableHeight();
                g.setColor(new Color(171, 192, 199));
                g.drawLine(0, (int) (bottom - 22), (int) width, (int) (bottom - 22));
                g.setColor(Color.DARK_GRAY);
                draw(g, "AquaTrack | " + (format.getOrientation() == PageFormat.PORTRAIT ? "Portrait" : "Landscape"),
                        SMALL, 0, bottom - 7, width, false);
                draw(g, "Page " + (pageIndex + 1) + " of " + pages.size(), SMALL, 0, bottom - 7, width, true);
                return PAGE_EXISTS;
            } finally { g.dispose(); }
        }

        public BufferedImage renderPage(int page, double scale) {
            if (page < 0 || page >= pages.size() || !Double.isFinite(scale) || scale <= 0 || scale > 3) {
                throw new IllegalArgumentException("Invalid preview page or scale.");
            }
            double pixelWidth = Math.ceil(format.getWidth() * scale);
            double pixelHeight = Math.ceil(format.getHeight() * scale);
            if (pixelWidth * pixelHeight > 24_000_000 || pixelWidth > Integer.MAX_VALUE
                    || pixelHeight > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("This receipt is too long for a single bitmap. Paint its Printable in a scrollable preview.");
            }
            BufferedImage image = new BufferedImage((int) pixelWidth, (int) pixelHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                graphics.scale(scale, scale);
                print(graphics, format, page);
            } finally { graphics.dispose(); }
            return image;
        }

        // Package-private inspection keeps pagination tests independent of raster font differences.
        List<String> bodyText(int page) {
            List<String> text = new ArrayList<>();
            if (receiptBands != null) {
                if (page != 0) throw new IndexOutOfBoundsException(page);
                for (ReceiptBand band : receiptBands) for (List<String> cell : band.cells) text.addAll(cell);
                return text;
            }
            for (Band band : pages.get(page)) for (List<String> cell : band.cells) text.addAll(cell);
            return text;
        }
        boolean withinMargins() {
            if (receiptBands != null) {
                for (ReceiptBand band : receiptBands) {
                    if (band.y < 0 || band.y + band.height > format.getImageableHeight() + .01) return false;
                }
                return true;
            }
            for (List<Band> page : pages) for (Band band : page) {
                if (band.y < 0 || band.y + band.height > format.getImageableHeight() - 29 + .01) return false;
            }
            return true;
        }
    }

    /** Roll layout has no page breaks: the same bands are used by the preview and Printable. */
    private final class ReceiptLayout {
        private final PageFormat requested;
        private final double width;
        private final List<ReceiptBand> bands = new ArrayList<>();
        private double y;

        private ReceiptLayout(PageFormat format) {
            requested = (PageFormat) format.clone();
            width = requested.getImageableWidth();
            if (!Double.isFinite(width) || width < 100 || !Double.isFinite(requested.getWidth())
                    || !Double.isFinite(requested.getHeight()) || requested.getImageableX() < 0
                    || requested.getImageableY() < 0
                    || requested.getImageableX() + width > requested.getWidth() + .01
                    || requested.getImageableHeight() < 0
                    || requested.getImageableY() + requested.getImageableHeight() > requested.getHeight() + .01) {
                throw new IllegalArgumentException("The receipt printer needs a valid printable area at least 36 mm wide.");
            }
        }

        private Paginated build() {
            text(Receipt.STATION_NAME, RECEIPT_BOLD, true);
            text(title, RECEIPT_TITLE, true);
            rule();
            for (String detail : details) text(detail, RECEIPT_BODY, false);
            rule();
            columns(List.of("Qty", "Unit price", "Amount"), RECEIPT_BOLD);
            if (rows.isEmpty()) text("No items to display.", RECEIPT_BODY, true);
            for (List<String> row : rows) {
                y += 4;
                text(row.get(0), RECEIPT_BODY, true);
                columns(row.subList(1, 4), RECEIPT_BODY);
            }
            rule();
            for (int i = 0; i < summary.size(); i++) {
                text(summary.get(i), i == 0 ? RECEIPT_BOLD : RECEIPT_BODY, i == summary.size() - 1);
            }
            y += 5;
            double top = requested.getImageableY();
            double bottom = Math.max(0, requested.getHeight() - top - requested.getImageableHeight());
            double height = Math.max(requested.getHeight(), Math.ceil(top + y + bottom));
            Paper paper = new Paper();
            paper.setSize(requested.getWidth(), height);
            paper.setImageableArea(requested.getImageableX(), top, width, height - top - bottom);
            PageFormat finished = new PageFormat();
            finished.setOrientation(PageFormat.PORTRAIT);
            finished.setPaper(paper);
            return new Paginated(finished, bands);
        }

        private void text(String value, Font font, boolean centered) {
            List<String> lines = wrap(value, font, width);
            double height = lines.size() * RECEIPT_LINE;
            bands.add(new ReceiptBand(y, height, List.of(lines), new double[] {width}, font, centered, false));
            y += height;
        }

        private void columns(List<String> values, Font font) {
            double[] widths = {width * .2, width * .4, width * .4};
            List<List<String>> cells = new ArrayList<>();
            for (int i = 0; i < values.size(); i++) cells.add(wrap(values.get(i), font, widths[i] - 4));
            double height = maxLines(cells) * RECEIPT_LINE;
            bands.add(new ReceiptBand(y, height, cells, widths, font, true, false));
            y += height;
        }

        private void rule() {
            bands.add(new ReceiptBand(y, 12, List.of(), new double[0], RECEIPT_BODY, false, true));
            y += 12;
        }
    }

    private static final class ReceiptBand {
        private final double y, height;
        private final List<List<String>> cells;
        private final double[] widths;
        private final Font font;
        private final boolean centered, rule;

        private ReceiptBand(double y, double height, List<List<String>> cells, double[] widths,
                            Font font, boolean centered, boolean rule) {
            this.y = y;
            this.height = height;
            List<List<String>> copied = new ArrayList<>();
            for (List<String> cell : cells) copied.add(List.copyOf(cell));
            this.cells = List.copyOf(copied);
            this.widths = widths.clone();
            this.font = font;
            this.centered = centered;
            this.rule = rule;
        }

        private void paint(Graphics2D g, double width) {
            // A scroll viewport can render only the visible part of even a very long receipt.
            java.awt.Rectangle clip = g.getClipBounds();
            if (clip != null && (y + height < clip.getMinY() || y > clip.getMaxY())) return;
            if (rule) {
                java.awt.Stroke previous = g.getStroke();
                g.setStroke(new java.awt.BasicStroke(.5f));
                g.draw(new java.awt.geom.Line2D.Double(0, y + 6, width, y + 6));
                g.setStroke(previous);
                return;
            }
            double x = 0;
            for (int i = 0; i < cells.size(); i++) {
                double baseline = y + 9;
                for (String line : cells.get(i)) {
                    draw(g, line, font, x, baseline, widths[i], centered);
                    baseline += RECEIPT_LINE;
                }
                x += widths[i];
            }
        }
    }

    private final class Layout {
        private final PageFormat format;
        private final double width, top, bottom;
        private final double[] widths;
        private final List<String> headings;
        private final List<List<String>> headers;
        private final double headerHeight;
        private final List<List<Band>> pages = new ArrayList<>();
        private List<Band> page;
        private double y;

        private Layout(PageFormat requested) {
            format = (PageFormat) requested.clone();
            width = format.getImageableWidth();
            if (width < 180 || format.getImageableHeight() < 200) {
                throw new IllegalArgumentException("The printable page area is too small.");
            }
            headings = wrap(title, TITLE, width);
            top = 35 + headings.size() * 20;
            bottom = format.getImageableHeight() - 29;
            widths = new double[weights.length];
            double sum = 0;
            for (double weight : weights) sum += weight;
            for (int i = 0; i < weights.length; i++) widths[i] = width * weights[i] / sum;
            headers = cells(columns, BOLD);
            headerHeight = maxLines(headers) * LINE + PAD * 2;
            if (top + headerHeight + LINE + PAD * 2 >= bottom) {
                throw new IllegalArgumentException("The title or columns leave too little space to print.");
            }
            newPage();
        }

        private Paginated build() {
            for (String detail : details) paragraph(detail, BODY);
            if (!details.isEmpty()) y += 8;
            startTable();
            if (rows.isEmpty()) paragraph("No records to display.", BODY);
            for (List<String> row : rows) tableRow(row);
            y += 10;
            for (String line : summary) paragraph(line, BOLD);
            List<List<Band>> finished = new ArrayList<>();
            for (List<Band> p : pages) finished.add(Collections.unmodifiableList(new ArrayList<>(p)));
            return new Paginated(format, Collections.unmodifiableList(finished), headings, widths);
        }

        private void newPage() { page = new ArrayList<>(); pages.add(page); y = top; }

        private void paragraph(String value, Font font) {
            for (String line : wrap(value, font, width)) {
                if (y + LINE > bottom) newPage();
                page.add(new Band(y, LINE, Collections.singletonList(Collections.singletonList(line)), font, false, false));
                y += LINE;
            }
        }

        private void startTable() {
            if (y + headerHeight + LINE + PAD * 2 > bottom) newPage();
            page.add(new Band(y, headerHeight, headers, BOLD, true, true));
            y += headerHeight;
        }

        private void tableRow(List<String> row) {
            List<List<String>> wrapped = cells(row, BODY);
            int lineCount = maxLines(wrapped), offset = 0;
            double fullHeight = lineCount * LINE + PAD * 2;
            // Keep ordinary rows together. Only a row taller than an entire page is split.
            if (fullHeight <= bottom - top - headerHeight && y + fullHeight > bottom) {
                newPage(); startTable();
            }
            while (offset < lineCount) {
                int capacity = (int) ((bottom - y - PAD * 2) / LINE);
                if (capacity < 1) { newPage(); startTable(); continue; }
                int end = Math.min(lineCount, offset + capacity);
                List<List<String>> slice = new ArrayList<>();
                for (List<String> cell : wrapped) {
                    slice.add(new ArrayList<>(cell.subList(Math.min(offset, cell.size()), Math.min(end, cell.size()))));
                }
                double height = (end - offset) * LINE + PAD * 2;
                page.add(new Band(y, height, slice, BODY, true, false));
                y += height;
                offset = end;
                if (offset < lineCount) { newPage(); startTable(); }
            }
        }

        private List<List<String>> cells(List<String> values, Font font) {
            List<List<String>> cells = new ArrayList<>();
            for (int i = 0; i < values.size(); i++) cells.add(wrap(values.get(i), font, widths[i] - PAD * 2));
            return cells;
        }
    }

    private static int maxLines(List<List<String>> cells) {
        int max = 1;
        for (List<String> cell : cells) max = Math.max(max, cell.size());
        return max;
    }

    private static final class Band {
        private final double y, height;
        private final List<List<String>> cells;
        private final Font font;
        private final boolean table, header;
        private Band(double y, double height, List<List<String>> cells, Font font, boolean table, boolean header) {
            this.y = y; this.height = height; this.cells = cells; this.font = font;
            this.table = table; this.header = header;
        }
        private void paint(Graphics2D g, double width, double[] widths) {
            if (table) {
                g.setColor(header ? new Color(228, 239, 243) : Color.WHITE);
                g.fill(new java.awt.geom.Rectangle2D.Double(0, y, width, height));
                g.setColor(new Color(196, 209, 214));
                g.draw(new java.awt.geom.Rectangle2D.Double(0, y, width, height));
            }
            double x = 0;
            for (int i = 0; i < cells.size(); i++) {
                double cellWidth = table ? widths[i] : width;
                List<String> lines = cells.get(i);
                double baseline = y + (table ? PAD : 0) + 10;
                g.setColor(new Color(24, 44, 54));
                for (String line : lines) {
                    draw(g, line, font, x + (table ? PAD : 0), baseline, cellWidth - (table ? PAD * 2 : 0), table);
                    baseline += LINE;
                }
                x += cellWidth;
                if (table && i < cells.size() - 1) {
                    g.setColor(new Color(196, 209, 214));
                    g.draw(new java.awt.geom.Line2D.Double(x, y, x, y + height));
                }
            }
        }
    }

    private static void draw(Graphics2D g, String text, Font font, double x, double baseline, double width, boolean center) {
        g.setFont(font);
        double left = center ? x + (width - font.getStringBounds(text, METRICS).getWidth()) / 2 : x;
        g.drawString(text, (float) left, (float) baseline);
    }

    /** Word wrapping with a code-point-safe hard break for long identifiers; never truncates content. */
    static List<String> wrap(String text, Font font, double width) {
        if (width < 10) throw new IllegalArgumentException("A print column is too narrow.");
        List<String> lines = new ArrayList<>();
        for (String paragraph : clean(text).split("\\R", -1)) {
            String remaining = paragraph;
            if (remaining.isEmpty()) lines.add("");
            while (!remaining.isEmpty()) {
                int points = remaining.codePointCount(0, remaining.length()), lo = 1, hi = points, fit = 0;
                while (lo <= hi) {
                    int mid = (lo + hi) / 2, end = remaining.offsetByCodePoints(0, mid);
                    if (font.getStringBounds(remaining.substring(0, end), METRICS).getWidth() <= width) {
                        fit = mid; lo = mid + 1;
                    } else hi = mid - 1;
                }
                int end = remaining.offsetByCodePoints(0, Math.max(1, fit));
                if (end < remaining.length()) {
                    int space = remaining.lastIndexOf(' ', end);
                    if (space > 0) end = space;
                }
                lines.add(remaining.substring(0, end).stripTrailing());
                remaining = remaining.substring(end).stripLeading();
            }
        }
        return lines;
    }
}
