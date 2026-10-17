package aquatrack.ui;

import aquatrack.dao.ProductDao;
import aquatrack.model.Product;
import aquatrack.util.Session;
import aquatrack.util.Validator;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Box;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/** Inventory for containers and caps or seals: stock on hand, restocking, and low-stock warnings. */
public final class InventoryPanel extends JPanel implements MainFrame.Screen {

    private final ProductDao products;
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[] {"Item", "Category", "In stock", "Reorder at", "Status"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = UiKit.table(model);
    private final JLabel summary = UiKit.muted(" ");
    private final JLabel chosen = UiKit.sectionTitle("Select an item");
    private final JTextField addField = UiKit.field(8);
    private final JTextField reorderField = UiKit.field(8);
    private final UiKit.ThemedButton addButton = UiKit.button("Add to stock", UiKit.ACCENT);
    private final UiKit.ThemedButton reorderButton = UiKit.button("Save reorder level", UiKit.SIDEBAR);
    private final JLabel status = UiKit.muted(" ");
    private List<Product> shown = new ArrayList<>();
    private boolean recordsLoaded;
    private boolean reloading;

    public InventoryPanel() {
        this(new ProductDao());
    }

    InventoryPanel(ProductDao products) {
        super(new BorderLayout(0, 16));
        this.products = products;
        setBackground(UiKit.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        add(UiKit.header("Inventory", "Containers and caps or seals. Amber rows are at or below their reorder level."),
                BorderLayout.NORTH);

        UiKit.styleTable(table);
        UiKit.widths(table, 200, 120, 100, 110, 120);
        table.setDefaultRenderer(Object.class, new StockRenderer());
        UiKit.statusColumn(table, 4);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !reloading) {
                showChosen();
            }
        });

        JPanel left = UiKit.card(new BorderLayout(0, 10));
        left.add(summary, BorderLayout.NORTH);
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
        column.add(UiKit.label("Reorder level (Owner only)"));
        column.add(Box.createVerticalStrut(6));
        column.add(UiKit.fullWidth(reorderField));
        column.add(Box.createVerticalStrut(8));
        column.add(UiKit.fullWidth(reorderButton));

        JPanel right = UiKit.card(new BorderLayout());
        right.setPreferredSize(new Dimension(340, 0));
        right.add(column, BorderLayout.NORTH);

        add(UiKit.splitFixedRight(left, right, 330), BorderLayout.CENTER);
        applyPermissions();
        showChosen();
    }

    @Override
    public void refresh() {
        applyPermissions();
        reload(selectedId());
    }

    private void applyPermissions() {
        boolean owner = Session.isOwner();
        reorderField.setEditable(owner);
        reorderButton.setVisible(owner);
    }

    private boolean reload(int keepId) {
        reloading = true;
        try {
            List<Product> loaded = new ArrayList<>();
            for (Product p : products.findAll(true)) {
                if (p.isStockTracked()) {
                    loaded.add(p);
                }
            }
            shown = loaded;
            recordsLoaded = true;
            table.setEnabled(true);
            model.setRowCount(0);
            int highlight = -1;
            int low = 0;
            for (int i = 0; i < shown.size(); i++) {
                Product p = shown.get(i);
                if (p.isLowStock()) {
                    low++;
                }
                model.addRow(new Object[] {p.getName(), p.getCategory(), p.getStockQty(), p.getReorderLevel(),
                        p.isLowStock() ? "LOW - reorder" : "OK"});
                if (p.getId() == keepId) {
                    highlight = i;
                }
            }
            if (highlight >= 0) {
                table.setRowSelectionInterval(highlight, highlight);
            } else {
                table.clearSelection();
            }
            showChosen();
            summary.setText(low == 0 ? "Everything is above its reorder level."
                    : low + " item(s) at or below their reorder level.");
            status.setText(shown.size() + " tracked item(s)");
            return true;
        } catch (SQLException e) {
            recordsLoaded = false;
            table.setEnabled(false);
            showChosen();
            summary.setText("Inventory is unavailable. Reopen this screen to retry.");
            status.setText("Could not load inventory: " + e.getMessage());
            return false;
        } finally {
            reloading = false;
        }
    }

    private Product selectedProduct() {
        int row = table.getSelectedRow();
        return row < 0 || row >= shown.size() ? null : shown.get(row);
    }

    private int selectedId() {
        Product p = selectedProduct();
        return p == null ? -1 : p.getId();
    }

    private void showChosen() {
        Product p = selectedProduct();
        boolean ready = recordsLoaded && p != null;
        addButton.setEnabled(ready);
        reorderButton.setEnabled(ready && Session.isOwner());
        if (!recordsLoaded) {
            chosen.setText("Reload inventory to continue");
            return;
        }
        chosen.setText(p == null ? "Select an item" : p.getName() + " (" + p.getStockQty() + " in stock)");
        reorderField.setText(p == null ? "" : String.valueOf(p.getReorderLevel()));
    }

    private void addStock() {
        if (!recordsLoaded) {
            return;
        }
        Product p = selectedProduct();
        if (p == null) {
            UiKit.warn(this, "Select an item in the list first.");
            return;
        }
        try {
            int count = Validator.requirePositiveInt(addField.getText(), "Stock received");
            products.restock(p.getId(), count);
            addField.setText("");
            if (reload(p.getId())) {
                status.setText(count + " added to " + p.getName() + ".");
            }
        } catch (IllegalArgumentException e) {
            UiKit.warn(this, e.getMessage());
        } catch (SQLException e) {
            UiKit.error(this, e);
        }
    }

    private void saveReorder() {
        if (!recordsLoaded || !Session.isOwner()) {
            return;
        }
        Product p = selectedProduct();
        if (p == null) {
            UiKit.warn(this, "Select an item in the list first.");
            return;
        }
        try {
            int level = Validator.requireNonNegativeInt(reorderField.getText(), "Reorder level");
            products.setReorderLevel(p.getId(), level);
            if (reload(p.getId())) {
                status.setText("Reorder level for " + p.getName() + " set to " + level + ".");
            }
        } catch (IllegalArgumentException e) {
            UiKit.warn(this, e.getMessage());
        } catch (SQLException e) {
            UiKit.error(this, e);
        }
    }

    /** Paints low-stock rows amber and right-aligns the number columns. */
    private final class StockRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            super.getTableCellRendererComponent(t, value, selected, focused, row, column);
            setHorizontalAlignment(column == 2 || column == 3 ? SwingConstants.RIGHT : SwingConstants.LEFT);
            if (!selected) {
                boolean low = row < shown.size() && shown.get(row).isLowStock();
                setBackground(low ? UiKit.WARNING : t.getBackground());
            }
            return this;
        }
    }
}
