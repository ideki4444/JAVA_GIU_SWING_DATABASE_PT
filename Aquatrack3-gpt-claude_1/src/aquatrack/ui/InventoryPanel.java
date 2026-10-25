package aquatrack.ui;

import aquatrack.dao.ProductDao;
import aquatrack.model.Product;
import aquatrack.util.Session;
import aquatrack.util.Validator;
import java.awt.BorderLayout;
import java.awt.Component;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import javax.swing.Box;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/** Inventory for containers and caps or seals, with literal search and background database work. */
public final class InventoryPanel extends JPanel implements MainFrame.Screen {
    private final ProductDao products;
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[] {"Item", "Category", "In stock", "Low-stock alert at", "Status"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = UiKit.table(model);
    private final JTextField search = UiKit.field(18);
    private final JLabel summary = UiKit.muted(" ");
    private final JLabel chosen = UiKit.sectionTitle("Select an item");
    private final JTextField addField = UiKit.field(8);
    private final JTextField reorderField = UiKit.field(8);
    private final UiKit.ThemedButton addButton = UiKit.button("Add to stock", UiKit.ACCENT);
    private final UiKit.ThemedButton reorderButton = UiKit.button("Save stock alert", UiKit.SIDEBAR);
    private final JLabel status = UiKit.muted(" ");
    private final Timer searchTimer;
    private List<Product> all = new ArrayList<>();
    private List<Product> shown = new ArrayList<>();
    private boolean recordsLoaded;
    private boolean rebuilding;
    private boolean busy;
    private int editingId = -1;
    private boolean resetStockAlert;

    public InventoryPanel() { this(new ProductDao()); }

    InventoryPanel(ProductDao products) {
        super(new BorderLayout(0, 16));
        this.products = products;
        setBackground(UiKit.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        add(UiKit.header("Inventory", "Track containers and caps or seals. Stock alerts show when supplies need replenishing."), BorderLayout.NORTH);
        UiKit.styleTable(table);
        UiKit.widths(table, 200, 120, 85, 135, 120);
        table.setDefaultRenderer(Object.class, new StockRenderer());
        UiKit.statusColumn(table, 4);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !rebuilding) showChosen();
        });
        searchTimer = new Timer(180, e -> filter(selectedId()));
        searchTimer.setRepeats(false);
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { searchTimer.restart(); }
            public void removeUpdate(DocumentEvent e) { searchTimer.restart(); }
            public void changedUpdate(DocumentEvent e) { searchTimer.restart(); }
        });
        JPanel searchRow = new JPanel(new BorderLayout(8, 0));
        searchRow.setOpaque(false);
        searchRow.add(UiKit.label("Search name or category"), BorderLayout.WEST);
        searchRow.add(search, BorderLayout.CENTER);
        UiKit.ThemedButton clear = UiKit.button("Clear", UiKit.SIDEBAR);
        clear.addActionListener(e -> search.setText(""));
        searchRow.add(clear, BorderLayout.EAST);
        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.setOpaque(false);
        top.add(searchRow, BorderLayout.NORTH);
        top.add(summary, BorderLayout.CENTER);
        UiKit.ThemedButton refresh = UiKit.button("Refresh", UiKit.SIDEBAR);
        refresh.addActionListener(e -> refresh());
        top.add(refresh, BorderLayout.SOUTH);
        JPanel left = UiKit.card(new BorderLayout(0, 10));
        left.add(top, BorderLayout.NORTH);
        left.add(UiKit.scroll(table), BorderLayout.CENTER);
        left.add(status, BorderLayout.SOUTH);
        addButton.addActionListener(e -> addStock());
        reorderButton.addActionListener(e -> saveReorder());
        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setOpaque(false);
        column.add(chosen);
        column.add(Box.createVerticalStrut(18));
        column.add(UiKit.label("Stock received"));
        column.add(Box.createVerticalStrut(6));
        column.add(UiKit.fullWidth(addField));
        column.add(Box.createVerticalStrut(8));
        column.add(UiKit.fullWidth(addButton));
        column.add(Box.createVerticalStrut(26));
        column.add(UiKit.label("Low-stock alert at (Owner only)"));
        column.add(Box.createVerticalStrut(6));
        column.add(UiKit.fullWidth(reorderField));
        column.add(Box.createVerticalStrut(8));
        column.add(UiKit.fullWidth(reorderButton));
        JPanel right = UiKit.card(new BorderLayout());
        right.add(column, BorderLayout.NORTH);
        add(UiKit.splitFixedRight(left, right, 400), BorderLayout.CENTER);
        showChosen();
    }

    @Override public void refresh() { reload(selectedId(), null); }
    @Override public boolean hasPendingOperation() { return busy; }

    private void reload(int keepId, String savedMessage) {
        if (busy) return;
        busy = true;
        status.setText(savedMessage == null ? "Loading inventory..." : savedMessage + " Refreshing...");
        showChosen();
        new SwingWorker<List<Product>, Void>() {
            protected List<Product> doInBackground() throws SQLException {
                List<Product> loaded = new ArrayList<>();
                for (Product p : products.findAll(true)) if (p.isStockTracked()) loaded.add(p);
                return loaded;
            }
            protected void done() {
                busy = false;
                try {
                    all = get();
                    recordsLoaded = true;
                    filter(keepId);
                    if (savedMessage != null) status.setText(savedMessage);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    recordsLoaded = false;
                    status.setText("Loading interrupted. Use Refresh to retry.");
                } catch (ExecutionException e) {
                    recordsLoaded = false;
                    status.setText((savedMessage == null ? "" : savedMessage + " ")
                            + "Could not refresh inventory. Use Refresh to retry. " + e.getCause().getMessage());
                }
                showChosen();
            }
        }.execute();
    }

    private void filter(int keepId) {
        if (!recordsLoaded) return;
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        rebuilding = true;
        shown = new ArrayList<>();
        model.setRowCount(0);
        int highlight = -1;
        int low = 0;
        for (Product p : all) {
            if (p.isLowStock()) low++;
            if (!(p.getName() + " " + p.getCategory()).toLowerCase(Locale.ROOT).contains(query)) continue;
            if (p.getId() == keepId) highlight = shown.size();
            shown.add(p);
            model.addRow(new Object[] {p.getName(), p.getCategory(), p.getStockQty(), p.getReorderLevel(), p.getAvailability()});
        }
        if (highlight >= 0) table.setRowSelectionInterval(highlight, highlight);
        else table.clearSelection();
        rebuilding = false;
        summary.setText(low == 0 ? "All supplies are above their stock alert level."
                : low + " item(s) need replenishing across all inventory.");
        status.setText(shown.isEmpty() ? "No items match. Clear the search to see all supplies."
                : shown.size() + " of " + all.size() + " tracked item(s)");
        showChosen();
    }

    private Product selectedProduct() {
        int row = table.getSelectedRow();
        if (row < 0) return null;
        int index = table.convertRowIndexToModel(row);
        return index >= shown.size() ? null : shown.get(index);
    }

    private int selectedId() {
        Product p = selectedProduct();
        return p == null ? -1 : p.getId();
    }

    private void showChosen() {
        Product p = selectedProduct();
        boolean ready = recordsLoaded && p != null && !busy;
        table.setEnabled(recordsLoaded && !busy);
        addButton.setEnabled(ready);
        addField.setEnabled(ready);
        reorderButton.setVisible(Session.isOwner());
        reorderButton.setEnabled(ready && Session.isOwner());
        reorderField.setEnabled(ready && Session.isOwner());
        if (!recordsLoaded) {
            chosen.setText("Refresh inventory to continue");
            return;
        }
        int id = p == null ? -1 : p.getId();
        if (!busy) {
            boolean changed = editingId != id;
            if (changed) addField.setText("");
            if (changed || resetStockAlert) reorderField.setText(p == null ? "" : String.valueOf(p.getReorderLevel()));
            editingId = id;
            resetStockAlert = false;
        }
        chosen.setText(p == null ? "Select an item" : p.getName() + " (" + p.getStockQty() + " in stock)");
    }

    private interface Write { void run() throws SQLException; }

    private void mutate(Product p, Write write, String success, boolean refreshAlert) {
        busy = true;
        status.setText("Saving...");
        showChosen();
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws SQLException { write.run(); return null; }
            protected void done() {
                busy = false;
                try {
                    get();
                    addField.setText("");
                    resetStockAlert = refreshAlert;
                    reload(p.getId(), success);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    recordsLoaded = false;
                    status.setText("Save interrupted. Refresh before trying again.");
                } catch (ExecutionException e) {
                    status.setText("Changes were not saved.");
                    UiKit.warn(InventoryPanel.this, e.getCause().getMessage());
                }
                showChosen();
            }
        }.execute();
    }

    private void addStock() {
        Product p = selectedProduct();
        if (!recordsLoaded || busy || p == null) return;
        try {
            int count = Validator.requirePositiveInt(addField.getText(), "Stock received");
            mutate(p, () -> products.restock(p.getId(), count), count + " added to " + p.getName() + ".", false);
        } catch (IllegalArgumentException e) { UiKit.warn(this, e.getMessage()); }
    }

    private void saveReorder() {
        Product p = selectedProduct();
        if (!recordsLoaded || busy || p == null || !Session.isOwner()) return;
        try {
            int level = Validator.requireNonNegativeInt(reorderField.getText(), "Low-stock alert");
            mutate(p, () -> products.setReorderLevel(p.getId(), level), "Stock alert for " + p.getName() + " set to " + level + ".", true);
        } catch (IllegalArgumentException e) { UiKit.warn(this, e.getMessage()); }
    }

    private final class StockRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                                 boolean focused, int row, int column) {
            super.getTableCellRendererComponent(t, value, selected, focused, row, column);
            setHorizontalAlignment(SwingConstants.CENTER);
            if (!selected) {
                int index = t.convertRowIndexToModel(row);
                setBackground(index < shown.size() && shown.get(index).isLowStock() ? UiKit.WARNING : t.getBackground());
            }
            return this;
        }
    }
}
