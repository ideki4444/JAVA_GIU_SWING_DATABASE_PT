package aquatrack.ui;

import aquatrack.dao.ReportDao;
import aquatrack.util.Fmt;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

/** Dashboard: today's key numbers, a seven-day sales chart, and the latest orders. */
public final class DashboardPanel extends JPanel implements MainFrame.Screen {

    private final ReportDao reports = new ReportDao();
    private final JLabel todaySales = bigNumber();
    private final JLabel pendingDeliveries = bigNumber();
    private final JLabel gallonsOut = bigNumber();
    private final JLabel lowStock = bigNumber();
    private final SalesChart chart = new SalesChart();
    private final DefaultTableModel recentModel = new DefaultTableModel(
            new Object[] {"Order #", "Date", "Customer", "Type", "Payment", "Total", "Status"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JLabel status = UiKit.muted(" ");
    private long refreshNumber;
    private javax.swing.SwingWorker<DashboardData, Void> worker;
    private record DashboardData(BigDecimal sales, int pending, int gallons, int low,
                                 Map<LocalDate, BigDecimal> chart, java.util.List<Object[]> orders) { }

    public DashboardPanel() {
        super(new BorderLayout(0, 16));
        setBackground(UiKit.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        add(UiKit.header("Dashboard", "Today's numbers at a glance"), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 16));
        body.setOpaque(false);
        body.add(kpiRow(), BorderLayout.NORTH);
        body.add(lowerRow(), BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
    }

    public void refresh() {
        final LocalDate today = LocalDate.now();
        final long request = ++refreshNumber;
        if (worker != null) worker.cancel(true);
        status.setText("Loading today's activity…");
        worker = UiTask.run(() -> new DashboardData(reports.salesOn(today), reports.countPendingDeliveries(),
                reports.gallonsOut(), reports.countLowStock(), reports.salesBetween(today.minusDays(6), today),
                reports.recentOrders(8)), data -> {
            if (request != refreshNumber) return;
            todaySales.setText(Fmt.peso(data.sales()));
            pendingDeliveries.setText(String.valueOf(data.pending()));
            gallonsOut.setText(String.valueOf(data.gallons()));
            lowStock.setText(String.valueOf(data.low()));
            chart.setData(data.chart(), today);
            recentModel.setRowCount(0);
            for (Object[] row : data.orders()) {
                recentModel.addRow(new Object[] {
                    row[0],
                    Fmt.dateTime((LocalDateTime) row[1]),
                    row[2],
                    row[3],
                    row[4],
                    Fmt.peso((BigDecimal) row[5]),
                    row[6]
                });
            }
            status.setText("Updated " + Fmt.dateTime(LocalDateTime.now()));
        }, e -> {
            if (request == refreshNumber) status.setText("Could not refresh the dashboard. Check MySQL and reopen this page.");
        });
    }

    private JPanel kpiRow() {
        JPanel row = new JPanel(new GridLayout(1, 4, 14, 0));
        row.setOpaque(false);
        row.add(kpiCard("Today's sales", todaySales));
        row.add(kpiCard("Pending deliveries", pendingDeliveries));
        row.add(kpiCard("Gallons out with customers", gallonsOut));
        row.add(kpiCard("Low or out of stock", lowStock));
        return row;
    }

    private JComponent lowerRow() {

        JPanel chartCard = UiKit.card(new BorderLayout(0, 10));
        chartCard.add(UiKit.sectionTitle("Sales, last 7 days"), BorderLayout.NORTH);
        chartCard.add(chart, BorderLayout.CENTER);

        JTable table = UiKit.table(recentModel);
        UiKit.styleTable(table);
        UiKit.widths(table, 80, 150, 160, 100, 110, 120, 120);
        UiKit.statusColumn(table, 6);
        UiKit.alignCenter(table, 5);
        JPanel tableCard = UiKit.card(new BorderLayout(0, 10));
        tableCard.add(UiKit.sectionTitle("Recent orders"), BorderLayout.NORTH);
        tableCard.add(UiKit.scroll(table), BorderLayout.CENTER);

        return UiKit.splitProportional(chartCard, tableCard, 0.45);
    }

    private static JLabel bigNumber() {
        JLabel label = new JLabel("—");
        label.setFont(UiKit.KPI);
        label.setForeground(UiKit.TEXT);
        return label;
    }

    private static JPanel kpiCard(String title, JLabel value) {
        JPanel card = UiKit.card(new BorderLayout(10, 6));
        card.add(new KpiCaption(title), BorderLayout.NORTH);
        card.add(value, BorderLayout.CENTER);
        JPanel accent = new JPanel();
        accent.setBackground(UiKit.ACCENT);
        accent.setPreferredSize(new Dimension(4, 1));
        card.add(accent, BorderLayout.WEST);
        return card;
    }

    /** Keep metric captions on one line, including on the smallest scrollable workspace. */
    private static final class KpiCaption extends JLabel {
        KpiCaption(String text) {
            super(text);
            setFont(UiKit.BODY);
            setForeground(UiKit.MUTED);
        }

        @Override public void setBounds(int x, int y, int width, int height) {
            super.setBounds(x, y, width, height);
            Font font = UiKit.BODY;
            while (font.getSize2D() > 10 && getFontMetrics(font).stringWidth(getText()) > width) {
                font = font.deriveFont(font.getSize2D() - 0.5f);
            }
            if (!font.equals(getFont())) setFont(font);
        }

        @Override public Dimension getPreferredSize() {
            // Reserve the same caption height in every card even when one font needs to shrink.
            return new Dimension(getFontMetrics(UiKit.BODY).stringWidth(getText()),
                    getFontMetrics(UiKit.BODY).getHeight());
        }
    }

    /** Seven bars, one per day, drawn directly with Java2D. */
    static final class SalesChart extends JPanel {

        private Map<LocalDate, BigDecimal> totals = new HashMap<>();
        private LocalDate lastDay = LocalDate.now();

        SalesChart() {
            setOpaque(false);
            setPreferredSize(new Dimension(360, 240));
        }

        void setData(Map<LocalDate, BigDecimal> data, LocalDate last) {
            totals = data;
            lastDay = last;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setFont(UiKit.BODY);
            int width = getWidth();
            int height = getHeight();
            int baseline = height - 30;
            int top = 26;

            double max = 0;
            for (BigDecimal value : totals.values()) {
                max = Math.max(max, value.doubleValue());
            }

            int slot = width / 7;
            int barWidth = Math.max(14, slot / 2);
            for (int i = 0; i < 7; i++) {
                LocalDate day = lastDay.minusDays(6 - i);
                BigDecimal sales = totals.get(day);
                double value = sales == null ? 0 : sales.doubleValue();
                int barHeight = max == 0 ? 0 : (int) Math.round((baseline - top) * value / max);
                int x = i * slot + (slot - barWidth) / 2;

                g2.setColor(UiKit.ACCENT);
                g2.fillRoundRect(x, baseline - barHeight, barWidth, barHeight, 8, 8);

                String dayName = day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
                g2.setColor(UiKit.MUTED);
                g2.drawString(dayName, centered(g2, dayName, x, barWidth), height - 8);

                if (value > 0) {
                    String amount = String.format(Locale.US, "%,.0f", value);
                    g2.setColor(UiKit.TEXT);
                    g2.drawString(amount, centered(g2, amount, x, barWidth), baseline - barHeight - 6);
                }
            }

            if (max == 0) {
                g2.setColor(UiKit.MUTED);
                String empty = "No sales in the last 7 days yet";
                g2.drawString(empty, (width - g2.getFontMetrics().stringWidth(empty)) / 2, height / 2);
            }
            g2.dispose();
        }

        private static int centered(Graphics2D g2, String text, int x, int barWidth) {
            return x + (barWidth - g2.getFontMetrics().stringWidth(text)) / 2;
        }
    }
}
