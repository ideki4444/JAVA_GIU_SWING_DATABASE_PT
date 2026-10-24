package aquatrack.ui;

import aquatrack.dao.ProductDao;
import aquatrack.model.Product;
import aquatrack.util.Fmt;
import aquatrack.util.Session;
import aquatrack.util.Validator;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

/**
 * Products and prices. The Owner adds products, sets prices, archives, restores, and deletes.
 * Staff can view the list. Product names must be unique. A product that has been sold can be archived
 * but not deleted.
 */
public final class ProductsPanel extends JPanel implements MainFrame.Screen {

    private static final int SHOW_ACTIVE = 0;
    private static final int SHOW_ARCHIVED = 1;

    private final ProductDao dao;
    private final JComboBox<String> showBox = new JComboBox<>(new String[] {"Active", "Archived", "All"});
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[] {"Product", "Category", "Unit price", "Stock", "Reorder at", "Status"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = UiKit.table(model);
    private final JTextField nameField = UiKit.field(24);
    private final JComboBox<String> categoryBox = new JComboBox<>(
            new String[] {Product.REFILL, Product.CONTAINER, Product.CAP_SEAL});
    private final JTextField priceField = UiKit.field(24);
    private final JTextField reorderField = UiKit.field(24);
    private final UiKit.ThemedButton newButton = UiKit.button("New", UiKit.SIDEBAR);
    private final UiKit.ThemedButton saveButton = UiKit.button("Save", UiKit.ACCENT);
    private final UiKit.ThemedButton archiveButton = UiKit.button("Archive", UiKit.SIDEBAR);
    private final UiKit.ThemedButton deleteButton = UiKit.button("Delete", UiKit.DANGER);
    private final JLabel note = UiKit.muted(" ");
    private final JLabel editing = UiKit.muted("New product");
    private final JLabel status = UiKit.muted(" ");
    private List<Product> shown = new ArrayList<>();
    private Product selected;
    private boolean reloading;
    private boolean recordsLoaded;

    public ProductsPanel() {
        this(new ProductDao());
    }

    ProductsPanel(ProductDao dao) {
        super(new BorderLayout(0, 16));
        this.dao = dao;
        setBackground(UiKit.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        add(UiKit.header("Products & Prices", "Sellable items, their prices, and reorder levels"),
                BorderLayout.NORTH);

        UiKit.styleTable(table);
        UiKit.widths(table, 190, 100, 110, 80, 100, 100);
        UiKit.statusColumn(table, 5);
        UiKit.alignRight(table, 2, 3, 4);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                pickRow();
            }
        });
        UiKit.styleInput(categoryBox);
        UiKit.styleInput(showBox);
        showBox.addActionListener(e -> reload());

        JPanel showGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        showGroup.setOpaque(false);
        showGroup.add(UiKit.label("Show"));
        showGroup.add(showBox);
        JPanel left = UiKit.card(new BorderLayout(0, 10));
        left.add(showGroup, BorderLayout.NORTH);
        left.add(UiKit.scroll(table), BorderLayout.CENTER);
        left.add(status, BorderLayout.SOUTH);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        UiKit.formRow(fields, 0, "Product name", nameField);
        UiKit.formRow(fields, 1, "Category", categoryBox);
        UiKit.formRow(fields, 2, "Unit price (\u20B1)", priceField);
        UiKit.formRow(fields, 3, "Reorder at (stock)", reorderField);
        JPanel formBody = new JPanel(new BorderLayout(0, 12));
        formBody.setOpaque(false);
        formBody.add(fields, BorderLayout.NORTH);
        formBody.add(note, BorderLayout.SOUTH);

        newButton.addActionListener(e -> clearForm());
        saveButton.addActionListener(e -> save());
        archiveButton.addActionListener(e -> toggleArchive());
        deleteButton.addActionListener(e -> delete());
        JPanel mainRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        mainRow.setOpaque(false);
        mainRow.add(newButton);
        mainRow.add(saveButton);
        // Archive and Delete sit on their own row, away from Save, so one stray click cannot remove a product.
        JPanel removeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        removeRow.setOpaque(false);
        removeRow.add(archiveButton);
        removeRow.add(deleteButton);
        JPanel buttons = new JPanel(new BorderLayout(0, 10));
        buttons.setOpaque(false);
        buttons.add(mainRow, BorderLayout.NORTH);
        buttons.add(removeRow, BorderLayout.SOUTH);

        JPanel form = UiKit.card(new BorderLayout(0, 14));
        form.setPreferredSize(new Dimension(380, 0));
        JPanel heading = new JPanel(new BorderLayout(0, 4));
        heading.setOpaque(false);
        heading.add(UiKit.sectionTitle("Product details"), BorderLayout.NORTH);
        heading.add(editing, BorderLayout.SOUTH);
        form.add(heading, BorderLayout.NORTH);
        form.add(formBody, BorderLayout.CENTER);
        form.add(buttons, BorderLayout.SOUTH);

        add(UiKit.splitFixedRight(left, form, 340), BorderLayout.CENTER);
        applyPermissions();
    }

    @Override
    public void refresh() {
        applyPermissions();
        reload();
    }

    /** Only the Owner can change products, so Staff see the list with the form and buttons switched off. */
    private void applyPermissions() {
        boolean owner = Session.isOwner();
        nameField.setEditable(owner);
        priceField.setEditable(owner);
        reorderField.setEditable(owner);
        categoryBox.setEnabled(owner);
        newButton.setVisible(owner);
        saveButton.setVisible(owner);
        archiveButton.setVisible(owner);
        deleteButton.setVisible(owner);
        showSelectionState();
    }

    private void pickRow() {
        if (reloading || !recordsLoaded) {
            return;
        }
        int row = table.getSelectedRow();
        if (row < 0 || row >= shown.size()) {
            return;
        }
        selected = shown.get(row);
        nameField.setText(selected.getName());
        categoryBox.setSelectedItem(selected.getCategory());
        priceField.setText(selected.getUnitPrice().toPlainString());
        reorderField.setText(String.valueOf(selected.getReorderLevel()));
        showSelectionState();
    }

    /** Keeps Archive or Restore, Delete, and the note in step with the selected product. */
    private void showSelectionState() {
        editing.setText(selected == null ? "New product" : "Editing: " + selected.getName());
        editing.setToolTipText(selected == null ? null : "Saving updates " + selected.getName()
                + ", even when this product is hidden by the current filter.");
        saveButton.setEnabled(recordsLoaded && Session.isOwner());
        table.setEnabled(recordsLoaded);
        archiveButton.setEnabled(false);
        deleteButton.setEnabled(false);
        if (!recordsLoaded) {
            return;
        }
        if (!Session.isOwner()) {
            note.setText(UiKit.wrapped("Only the Owner can add products or change prices."));
            return;
        }
        if (selected == null) {
            archiveButton.setText("Archive");
            archiveButton.setEnabled(false);
            deleteButton.setEnabled(false);
            deleteButton.setToolTipText(null);
            note.setText(UiKit.wrapped("Changes apply to the next order."));
            return;
        }
        archiveButton.setText(selected.isActive() ? "Archive" : "Restore");
        archiveButton.setEnabled(true);
        try {
            boolean used = dao.hasOrders(selected.getId());
            deleteButton.setEnabled(!used);
            deleteButton.setToolTipText(used ? "Already used in orders. Archive it instead." : null);
            if (!selected.isActive()) {
                note.setText(UiKit.wrapped("Archived products are hidden from new orders. Restore to sell again."));
            } else if (used) {
                note.setText(UiKit.wrapped("Already used in orders, so it can be archived but not deleted."));
            } else {
                note.setText(UiKit.wrapped("Changes apply to the next order."));
            }
        } catch (SQLException e) {
            deleteButton.setEnabled(false);
            status.setText("Could not check the sales history: " + e.getMessage());
        }
    }

    /** Reloads the list for the Show filter. The form is left alone so typing never wipes an edit. */
    private void reload() {
        reloading = true;
        try {
            int choice = showBox.getSelectedIndex();
            List<Product> loaded = new ArrayList<>();
            for (Product p : dao.findAll(false)) {
                boolean keep;
                if (choice == SHOW_ACTIVE) {
                    keep = p.isActive();
                } else if (choice == SHOW_ARCHIVED) {
                    keep = !p.isActive();
                } else {
                    keep = true;
                }
                if (keep) {
                    loaded.add(p);
                }
            }
            shown = loaded;
            recordsLoaded = true;
            model.setRowCount(0);
            int highlight = -1;
            for (int i = 0; i < shown.size(); i++) {
                Product p = shown.get(i);
                model.addRow(new Object[] {
                    p.getName(),
                    p.getCategory(),
                    Fmt.peso(p.getUnitPrice()),
                    p.isStockTracked() ? String.valueOf(p.getStockQty()) : "-",
                    p.isStockTracked() ? String.valueOf(p.getReorderLevel()) : "-",
                    p.isActive() ? "Active" : "Archived"
                });
                if (selected != null && p.getId() == selected.getId()) {
                    selected = p;
                    highlight = i;
                }
            }
            if (highlight >= 0) {
                table.setRowSelectionInterval(highlight, highlight);
            } else {
                table.clearSelection();
            }
            status.setText(shown.size() + " product(s) shown");
            showSelectionState();
        } catch (SQLException e) {
            recordsLoaded = false;
            showSelectionState();
            status.setText("Could not load products: " + e.getMessage());
        } finally {
            reloading = false;
        }
    }

    private void clearForm() {
        selected = null;
        nameField.setText("");
        priceField.setText("");
        reorderField.setText("");
        categoryBox.setSelectedIndex(0);
        table.clearSelection();
        showSelectionState();
    }

    private void save() {
        if (!recordsLoaded || !Session.isOwner()) {
            return;
        }
        boolean adding = selected == null;
        try {
            String name = Validator.requireText(nameField.getText(), "Product name", 100);
            String category = (String) categoryBox.getSelectedItem();
            BigDecimal price = Validator.requireMoney(priceField.getText(), "Unit price");
            if (price.signum() == 0) {
                throw new IllegalArgumentException("Unit price must be more than zero.");
            }
            int reorder = Product.REFILL.equals(category)
                    ? 0 : Validator.requireNonNegativeInt(reorderField.getText(), "Reorder level");
            Product same = dao.findByName(name, adding ? 0 : selected.getId());
            if (same != null) {
                UiKit.warn(this, "A product named \"" + same.getName() + "\" already exists"
                        + (same.isActive()
                        ? ". Use a different name."
                        : " (archived). Restore it instead of adding it again."));
                return;
            }
            if (adding) {
                dao.insert(name, category, price, reorder);
            } else {
                dao.update(selected.getId(), name, category, price, reorder);
            }
            UiKit.info(this, adding ? "Product added." : "Product updated.");
            clearForm();
            reload();
        } catch (IllegalArgumentException e) {
            UiKit.warn(this, e.getMessage());
        } catch (SQLException e) {
            UiKit.error(this, e);
        }
    }

    private void toggleArchive() {
        if (!recordsLoaded || !Session.isOwner()) {
            return;
        }
        if (selected == null) {
            UiKit.warn(this, "Select a product in the table first.");
            return;
        }
        boolean restore = !selected.isActive();
        String question = restore
                ? "Restore " + selected.getName() + " so it can be sold again?"
                : "Archive " + selected.getName() + "? It will be hidden from new orders. "
                + "Past orders keep their records.";
        if (!UiKit.confirm(this, question)) {
            return;
        }
        try {
            dao.setActive(selected.getId(), restore);
            UiKit.info(this, restore ? "Product restored." : "Product archived.");
            clearForm();
            reload();
        } catch (SQLException e) {
            UiKit.error(this, e);
        }
    }

    private void delete() {
        if (!recordsLoaded || !Session.isOwner()) {
            return;
        }
        if (selected == null) {
            UiKit.warn(this, "Select a product in the table first.");
            return;
        }
        if (!UiKit.confirm(this, "Delete " + selected.getName() + "? This cannot be undone.")) {
            return;
        }
        try {
            if (dao.hasOrders(selected.getId())) {
                UiKit.warn(this, "This product is already used in orders, so it cannot be deleted. "
                        + "Use Archive instead.");
                return;
            }
            dao.delete(selected.getId());
            UiKit.info(this, "Product deleted.");
            clearForm();
            reload();
        } catch (SQLException e) {
            UiKit.error(this, e);
        }
    }
}
