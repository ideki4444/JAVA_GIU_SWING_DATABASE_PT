package aquatrack.ui;

import aquatrack.dao.ProductDao;
import aquatrack.model.Product;
import aquatrack.util.Fmt;
import aquatrack.util.Session;
import aquatrack.util.Validator;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;

/** Owner-managed catalog. Filtering preserves the current edit and its immutable product ID. */
public final class ProductsPanel extends JPanel implements MainFrame.Screen {
    private final ProductDao dao;
    private final JComboBox<String> showBox = new JComboBox<>(new String[] {"Current products", "Archived", "All"});
    private final JTextField search = UiKit.field(20);
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[] {"Product", "Category", "Pickup / unit price", "Delivery price", "Stock", "Low-stock alert at", "Status"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = UiKit.table(model);
    private final JTextField nameField = UiKit.field(24);
    private final JComboBox<String> categoryBox = new JComboBox<>(new String[] {Product.REFILL, Product.CONTAINER, Product.CAP_SEAL});
    private final JTextField priceField = UiKit.field(24);
    private final JTextField deliveryPriceField = UiKit.field(24);
    private final JTextField reorderField = UiKit.field(24);
    private final UiKit.ThemedButton newButton = UiKit.button("New product", UiKit.SIDEBAR);
    private final UiKit.ThemedButton saveButton = UiKit.button("Save", UiKit.ACCENT);
    private final UiKit.ThemedButton archiveButton = UiKit.button("Archive", UiKit.SIDEBAR);
    private final UiKit.ThemedButton deleteButton = UiKit.button("Delete", UiKit.DANGER);
    private final JLabel note = UiKit.muted(" ");
    private final JLabel editing = UiKit.muted("New product");
    private final JLabel status = UiKit.muted(" ");
    private final Timer searchTimer;
    private List<Product> all = new ArrayList<>();
    private List<Product> shown = new ArrayList<>();
    private Product selected;
    private boolean rebuilding;
    private boolean recordsLoaded;
    private boolean busy;

    public ProductsPanel() { this(new ProductDao()); }

    ProductsPanel(ProductDao dao) {
        super(new BorderLayout(0, 16));
        this.dao = dao;
        setBackground(UiKit.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        add(UiKit.header("Products & Prices", "Pickup and delivery refill prices, and stock alerts for supplies."), BorderLayout.NORTH);
        UiKit.styleTable(table);
        UiKit.widths(table, 190, 100, 125, 115, 65, 130, 110);
        UiKit.statusColumn(table, 6);
        table.getSelectionModel().addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) pickRow(); });
        UiKit.styleInput(categoryBox);
        UiKit.styleInput(showBox);
        categoryBox.addActionListener(e -> updateControls());
        showBox.addActionListener(e -> filter());
        searchTimer = new Timer(180, e -> filter());
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
        JPanel filters = new JPanel(new BorderLayout(0, 8));
        filters.setOpaque(false);
        filters.add(searchRow, BorderLayout.NORTH);
        JPanel showGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        showGroup.setOpaque(false);
        showGroup.add(UiKit.label("Show"));
        showGroup.add(showBox);
        UiKit.ThemedButton refresh = UiKit.button("Refresh", UiKit.SIDEBAR);
        refresh.addActionListener(e -> refresh());
        showGroup.add(refresh);
        filters.add(showGroup, BorderLayout.SOUTH);
        JPanel left = UiKit.card(new BorderLayout(0, 10));
        left.add(filters, BorderLayout.NORTH);
        left.add(UiKit.scroll(table), BorderLayout.CENTER);
        left.add(status, BorderLayout.SOUTH);
        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        UiKit.formRow(fields, 0, "Product name", nameField);
        UiKit.formRow(fields, 1, "Category", categoryBox);
        UiKit.formRow(fields, 2, "Pickup / unit price (PHP)", priceField);
        UiKit.formRow(fields, 3, "Delivery refill price (PHP)", deliveryPriceField);
        UiKit.formRow(fields, 4, "Low-stock alert at", reorderField);
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
        JPanel removeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        removeRow.setOpaque(false);
        removeRow.add(archiveButton);
        removeRow.add(deleteButton);
        JPanel buttons = new JPanel(new BorderLayout(0, 10));
        buttons.setOpaque(false);
        buttons.add(mainRow, BorderLayout.NORTH);
        buttons.add(removeRow, BorderLayout.SOUTH);
        JPanel form = UiKit.card(new BorderLayout(0, 14));
        JPanel heading = new JPanel(new BorderLayout(0, 4));
        heading.setOpaque(false);
        heading.add(UiKit.sectionTitle("Product details"), BorderLayout.NORTH);
        heading.add(editing, BorderLayout.SOUTH);
        form.add(heading, BorderLayout.NORTH);
        form.add(formBody, BorderLayout.CENTER);
        form.add(buttons, BorderLayout.SOUTH);
        add(UiKit.splitFixedRight(left, form, 470), BorderLayout.CENTER);
        updateControls();
    }

    @Override public void refresh() { reload(null); }
    @Override public boolean hasPendingOperation() { return busy; }

    private void reload(String savedMessage) {
        if (busy) return;
        busy = true;
        status.setText(savedMessage == null ? "Loading products..." : savedMessage + " Refreshing...");
        updateControls();
        new SwingWorker<List<Product>, Void>() {
            protected List<Product> doInBackground() throws SQLException { return dao.findAll(false); }
            protected void done() {
                busy = false;
                try {
                    all = get();
                    recordsLoaded = true;
                    if (selected != null) {
                        int id = selected.getId();
                        selected = all.stream().filter(p -> p.getId() == id).findFirst().orElse(null);
                        if (selected == null) clearForm();
                    }
                    filter();
                    if (savedMessage != null) status.setText(savedMessage + " " + shown.size() + " product(s) shown.");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    recordsLoaded = false;
                    status.setText("Product loading interrupted. Use Refresh to retry.");
                } catch (ExecutionException e) {
                    recordsLoaded = false;
                    status.setText((savedMessage == null ? "" : savedMessage + " ")
                            + "Could not refresh products. Use Refresh to retry. " + e.getCause().getMessage());
                }
                updateControls();
            }
        }.execute();
    }

    /** Literal matching: punctuation is ordinary text, and keystrokes never query the database. */
    private void filter() {
        if (!recordsLoaded) return;
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        int choice = showBox.getSelectedIndex();
        rebuilding = true;
        shown = new ArrayList<>();
        model.setRowCount(0);
        int highlight = -1;
        for (Product p : all) {
            if (choice == 0 && !p.isActive() || choice == 1 && p.isActive()) continue;
            if (!(p.getName() + " " + p.getCategory()).toLowerCase(Locale.ROOT).contains(query)) continue;
            if (selected != null && p.getId() == selected.getId()) highlight = shown.size();
            shown.add(p);
            model.addRow(new Object[] {p.getName(), p.getCategory(), Fmt.peso(p.getUnitPrice()),
                Product.REFILL.equals(p.getCategory()) ? p.getDeliveryPrice() == null ? "Needs setup" : Fmt.peso(p.getDeliveryPrice()) : "Same unit price",
                p.isStockTracked() ? p.getStockQty() : "Made on demand",
                p.isStockTracked() ? p.getReorderLevel() : "\u2014", p.getAvailability()});
        }
        if (highlight >= 0) table.setRowSelectionInterval(highlight, highlight);
        else table.clearSelection();
        rebuilding = false;
        status.setText(shown.isEmpty() ? "No products match. Clear the search or change Show." : shown.size() + " product(s) shown");
        updateControls();
    }

    private void pickRow() {
        if (rebuilding || busy || !recordsLoaded) return;
        int row = table.getSelectedRow();
        if (row < 0) return;
        selected = shown.get(table.convertRowIndexToModel(row));
        nameField.setText(selected.getName());
        categoryBox.setSelectedItem(selected.getCategory());
        priceField.setText(selected.getUnitPrice().toPlainString());
        deliveryPriceField.setText(selected.getDeliveryPrice() == null ? "" : selected.getDeliveryPrice().toPlainString());
        reorderField.setText(String.valueOf(selected.getReorderLevel()));
        updateControls();
    }

    private void updateControls() {
        boolean owner = Session.isOwner();
        boolean ready = owner && recordsLoaded && !busy;
        boolean refill = Product.REFILL.equals(categoryBox.getSelectedItem());
        nameField.setEditable(ready);
        priceField.setEditable(ready);
        deliveryPriceField.setEnabled(ready && refill);
        reorderField.setEnabled(ready && !refill);
        categoryBox.setEnabled(ready);
        newButton.setVisible(owner);
        saveButton.setVisible(owner);
        archiveButton.setVisible(owner);
        deleteButton.setVisible(owner);
        newButton.setEnabled(ready);
        saveButton.setEnabled(ready);
        table.setEnabled(recordsLoaded && !busy);
        archiveButton.setEnabled(ready && selected != null);
        deleteButton.setEnabled(ready && selected != null);
        archiveButton.setText(selected != null && !selected.isActive() ? "Restore" : "Archive");
        boolean hidden = selected != null && shown.stream().noneMatch(p -> p.getId() == selected.getId());
        editing.setText(selected == null ? "New product" : "Editing: " + selected.getName() + (hidden ? " (hidden by filter)" : ""));
        editing.setToolTipText(selected == null ? null : "Save updates this product. Choose New product to add another.");
        note.setText(UiKit.wrapped(!owner ? "Only the Owner can change products and prices."
                : refill ? "Both refill prices are per container of the size in the product name, for example one 5-gallon container. Stock is made on demand. Price changes apply to future sales."
                : "Supplies use the same unit price for pickup and delivery. A stock alert appears at or below this quantity."));
    }

    private void clearForm() {
        selected = null;
        nameField.setText("");
        priceField.setText("");
        deliveryPriceField.setText("");
        reorderField.setText("");
        categoryBox.setSelectedIndex(0);
        table.clearSelection();
        updateControls();
    }

    private interface Write { void run() throws SQLException; }

    private void mutate(Write write, String success) {
        busy = true;
        status.setText("Saving...");
        updateControls();
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws SQLException { write.run(); return null; }
            protected void done() {
                busy = false;
                try {
                    get();
                    clearForm();
                    reload(success);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    recordsLoaded = false;
                    status.setText("Save interrupted. Refresh before trying again.");
                } catch (ExecutionException e) {
                    status.setText("Changes were not saved.");
                    UiKit.warn(ProductsPanel.this, e.getCause().getMessage());
                }
                updateControls();
            }
        }.execute();
    }

    private void save() {
        if (!recordsLoaded || busy || !Session.isOwner()) return;
        final int id = selected == null ? 0 : selected.getId();
        try {
            String name = Validator.requireText(nameField.getText(), "Product name", 100);
            String category = (String) categoryBox.getSelectedItem();
            BigDecimal price = Validator.requireMoney(priceField.getText(), "Pickup / unit price");
            BigDecimal delivered = Product.REFILL.equals(category) ? Validator.requireMoney(deliveryPriceField.getText(), "Delivery refill price") : null;
            Product checked = new Product(id, name, category, price, delivered, 0, 0, true);
            checked.priceForOrderType("Walk-in");
            if (Product.REFILL.equals(category)) checked.priceForOrderType("Delivery");
            int level = Product.REFILL.equals(category) ? 0 : Validator.requireNonNegativeInt(reorderField.getText(), "Low-stock alert");
            mutate(() -> {
                Product same = dao.findByName(name, id);
                if (same != null) throw new IllegalArgumentException("A product named \"" + same.getName() + "\" already exists"
                        + (same.isActive() ? ". Choose another name." : " in the archive. Restore it instead."));
                if (id == 0) dao.insert(name, category, price, delivered, level);
                else dao.update(id, name, category, price, delivered, level);
            }, id == 0 ? "Product added." : "Product updated.");
        } catch (IllegalArgumentException e) { UiKit.warn(this, e.getMessage()); }
    }

    private void toggleArchive() {
        if (!recordsLoaded || busy || !Session.isOwner() || selected == null) return;
        Product target = selected;
        boolean restore = !target.isActive();
        if (!UiKit.confirm(this, (restore ? "Restore " : "Archive ") + target.getName() + "? Past sales keep their recorded prices.")) return;
        mutate(() -> dao.setActive(target.getId(), restore), restore ? "Product restored." : "Product archived.");
    }

    private void delete() {
        if (!recordsLoaded || busy || !Session.isOwner() || selected == null) return;
        Product target = selected;
        if (!UiKit.confirm(this, "Delete " + target.getName() + "? This cannot be undone.")) return;
        mutate(() -> {
            if (dao.hasOrders(target.getId())) throw new IllegalArgumentException("This product has sales history. Archive it instead.");
            dao.delete(target.getId());
        }, "Product deleted.");
    }
}
