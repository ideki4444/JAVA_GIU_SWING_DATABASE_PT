package aquatrack.ui;

import aquatrack.dao.CustomerDao;
import aquatrack.dao.CustomerDao.Show;
import aquatrack.model.Customer;
import aquatrack.util.Session;
import aquatrack.util.Validator;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagLayout;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;

/**
 * Customers: search, add, edit, archive, and delete. Names and contact numbers must be unique.
 * Archive, restore, and delete are Owner only. A customer with history can be archived but not deleted.
 */
public final class CustomersPanel extends JPanel implements MainFrame.Screen {

    /** Choices in the Show box, in the same order as the labels. */
    private static final Show[] SHOW_CHOICES = {Show.ACTIVE, Show.ARCHIVED, Show.ALL};
    private static final String[] SHOW_LABELS = {"Active", "Archived", "All"};

    private final CustomerDao dao;
    private final JTextField search = UiKit.field(22);
    private final JComboBox<String> showBox = new JComboBox<>(SHOW_LABELS);
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[] {"Customer", "Address", "Contact No.", "Status"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = UiKit.table(model);
    private final JTextField nameField = UiKit.field(24);
    private final JTextField addressField = UiKit.field(24);
    private final JTextField contactField = UiKit.field(24);
    private final JLabel note = UiKit.muted(" ");
    private final JLabel editing = UiKit.muted("New customer");
    private final UiKit.ThemedButton saveButton = UiKit.button("Save", UiKit.ACCENT);
    private final UiKit.ThemedButton archiveButton = UiKit.button("Archive", UiKit.SIDEBAR);
    private final UiKit.ThemedButton deleteButton = UiKit.button("Delete", UiKit.DANGER);
    private final JLabel status = UiKit.muted(" ");
    private List<Customer> shown = new ArrayList<>();
    private Customer selected;
    private boolean reloading;
    private boolean recordsLoaded;
    private boolean saving;
    private long loadNumber;
    private long selectionNumber;
    private javax.swing.SwingWorker<List<Customer>, Void> loader;
    private final javax.swing.Timer searchTimer = new javax.swing.Timer(180, e -> reload());
    private final UiKit.ThemedButton newButton = UiKit.button("New", UiKit.SIDEBAR);

    public CustomersPanel() {
        this(new CustomerDao());
    }

    CustomersPanel(CustomerDao dao) {
        super(new BorderLayout(0, 16));
        this.dao = dao;
        searchTimer.setRepeats(false);
        setBackground(UiKit.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        add(UiKit.header("Customers", "Search, add, and update the people who buy from the station"),
                BorderLayout.NORTH);

        UiKit.styleTable(table);
        UiKit.widths(table, 170, 190, 130, 100);
        UiKit.statusColumn(table, 3);
        UiKit.styleInput(showBox);
        showBox.addActionListener(e -> reload());
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                pickRow();
            }
        });
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                searchTimer.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                searchTimer.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                searchTimer.restart();
            }
        });

        // Search and the Show filter sit on separate rows so a narrow window never overlaps them.
        JPanel searchRow = new JPanel(new BorderLayout(12, 0));
        searchRow.setOpaque(false);
        searchRow.add(UiKit.label("Search by name or number:"), BorderLayout.WEST);
        searchRow.add(search, BorderLayout.CENTER);
        JPanel showRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        showRow.setOpaque(false);
        showRow.add(UiKit.label("Show"));
        showRow.add(showBox);
        JPanel searchBar = new JPanel(new BorderLayout(0, 8));
        searchBar.setOpaque(false);
        searchBar.add(searchRow, BorderLayout.NORTH);
        searchBar.add(showRow, BorderLayout.SOUTH);
        JPanel left = UiKit.card(new BorderLayout(0, 10));
        left.add(searchBar, BorderLayout.NORTH);
        left.add(UiKit.scroll(table), BorderLayout.CENTER);
        left.add(status, BorderLayout.SOUTH);

        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        UiKit.formRow(fields, 0, "Full name", nameField);
        UiKit.formRow(fields, 1, "Address", addressField);
        UiKit.formRow(fields, 2, "Contact no.", contactField);
        JPanel formBody = new JPanel(new BorderLayout(0, 12));
        formBody.setOpaque(false);
        formBody.add(fields, BorderLayout.NORTH);
        formBody.add(note, BorderLayout.SOUTH);

        newButton.addActionListener(e -> clearForm());
        saveButton.addActionListener(e -> save());
        archiveButton.addActionListener(e -> toggleArchive());
        deleteButton.addActionListener(e -> delete());
        archiveButton.setVisible(Session.isOwner());
        deleteButton.setVisible(Session.isOwner());

        JPanel mainRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        mainRow.setOpaque(false);
        mainRow.add(newButton);
        mainRow.add(saveButton);
        // Archive and Delete sit on their own row, away from Save, so one stray click cannot remove a record.
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
        heading.add(UiKit.sectionTitle("Customer details"), BorderLayout.NORTH);
        heading.add(editing, BorderLayout.SOUTH);
        form.add(heading, BorderLayout.NORTH);
        form.add(formBody, BorderLayout.CENTER);
        form.add(buttons, BorderLayout.SOUTH);

        add(UiKit.splitFixedRight(left, form, 280), BorderLayout.CENTER);
        showSelectionState();
    }

    @Override
    public void refresh() {
        reload();
    }
    @Override public boolean hasPendingOperation() { return saving; }

    /** Fills the form when a row is clicked. Ignored while the table is being rebuilt. */
    private void pickRow() {
        if (reloading || !recordsLoaded || saving) {
            return;
        }
        int row = table.getSelectedRow();
        // Clearing a table selection must not turn an existing draft into an insert.
        if (row < 0 || row >= shown.size()) {
            return;
        }
        selected = shown.get(row);
        nameField.setText(selected.getFullName());
        addressField.setText(selected.getAddress());
        contactField.setText(selected.getContactNo());
        showSelectionState();
    }

    /** Keeps the archive and delete buttons and the note in step with the selected customer. */
    private void showSelectionState() {
        editing.setText(selected == null ? "New customer" : "Editing: " + selected.getFullName());
        editing.setToolTipText(selected == null ? null : "Saving updates " + selected.getFullName()
                + ", even when this customer is hidden by the current filter.");
        saveButton.setEnabled(recordsLoaded && !saving);
        table.setEnabled(recordsLoaded && !saving);
        newButton.setEnabled(!saving);
        nameField.setEditable(!saving);
        addressField.setEditable(!saving);
        contactField.setEditable(!saving);
        search.setEnabled(!saving);
        showBox.setEnabled(!saving);
        long selection = ++selectionNumber;
        if (!recordsLoaded || saving) {
            archiveButton.setEnabled(false);
            deleteButton.setEnabled(false);
            return;
        }
        if (selected == null) {
            archiveButton.setText("Archive");
            archiveButton.setEnabled(false);
            deleteButton.setEnabled(false);
            deleteButton.setToolTipText(null);
            note.setText(" ");
            return;
        }
        archiveButton.setText(selected.isArchived() ? "Restore" : "Archive");
        archiveButton.setEnabled(Session.isOwner());
        deleteButton.setEnabled(false);
        Customer chosen = selected;
        UiTask.run(() -> dao.hasHistory(chosen.getId()), history -> {
            if (selection != selectionNumber || selected == null || selected.getId() != chosen.getId() || saving) return;
            deleteButton.setEnabled(!history);
            deleteButton.setToolTipText(history ? "Has orders, payments, or container records. Archive it instead." : null);
            if (selected.isArchived()) {
                note.setText(UiKit.wrapped("Archived. Restore this customer to use them in new orders again.", 240));
            } else if (history) {
                note.setText(UiKit.wrapped("Has orders or payments, so it can be archived but not deleted.", 240));
            } else {
                note.setText(" ");
            }
        }, e -> {
            if (selection != selectionNumber) return;
            deleteButton.setEnabled(false);
            status.setText("Could not check this customer's history: " + e.getMessage());
        });
    }

    /** Reloads the table for the search text and the Show filter. The form is left alone so typing never wipes an edit. */
    private void reload() {
        if (saving) return;
        long request = ++loadNumber;
        if (loader != null) loader.cancel(true);
        String query = search.getText();
        Show show = SHOW_CHOICES[showBox.getSelectedIndex()];
        status.setText("Loading customers…");
        loader = UiTask.run(() -> dao.search(query, show), loaded -> {
            if (request != loadNumber || saving) return;
            reloading = true;
            shown = loaded;
            recordsLoaded = true;
            model.setRowCount(0);
            int highlight = -1;
            for (int i = 0; i < shown.size(); i++) {
                Customer c = shown.get(i);
                model.addRow(new Object[] {c.getFullName(), c.getAddress(), c.getContactNo(),
                        c.isArchived() ? "Archived" : "Active"});
                if (selected != null && c.getId() == selected.getId()) {
                    selected = c;
                    highlight = i;
                }
            }
            if (highlight >= 0) {
                table.setRowSelectionInterval(highlight, highlight);
            } else {
                table.clearSelection();
            }
            status.setText(shown.size() + " customer(s) shown");
            reloading = false;
            showSelectionState();
        }, e -> {
            if (request != loadNumber) return;
            recordsLoaded = false;
            showSelectionState();
            status.setText("Could not load customers: " + e.getMessage());
        });
    }

    private void clearForm() {
        selected = null;
        nameField.setText("");
        addressField.setText("");
        contactField.setText("");
        table.clearSelection();
        showSelectionState();
    }

    private void save() {
        if (!recordsLoaded || saving) {
            return;
        }
        boolean adding = selected == null;
        try {
            String name = Validator.requireText(nameField.getText(), "Full name", 100);
            String address = Validator.requireText(addressField.getText(), "Address", 255);
            String contact = Validator.requireMobile(contactField.getText(), "Contact number");
            int id = adding ? 0 : selected.getId();
            write(() -> {
                String clash = duplicateProblem(name, contact, id);
                if (clash != null) throw new IllegalArgumentException(clash);
                if (adding) dao.insert(name, address, contact);
                else dao.update(id, name, address, contact);
                return null;
            }, adding ? "Customer added." : "Customer updated.");
        } catch (IllegalArgumentException e) {
            UiKit.warn(this, e.getMessage());
        }
    }

    /** Explains which other customer already uses the name or number, or returns null when both are free. */
    private String duplicateProblem(String name, String contact, int ownId) throws SQLException {
        Customer sameName = dao.findByName(name, ownId);
        if (sameName != null) {
            return "The name \"" + sameName.getFullName() + "\" is already used by another customer"
                    + (sameName.isArchived()
                    ? " (archived). Restore that record instead of adding it again."
                    : ". Each customer needs a unique name.");
        }
        Customer sameContact = dao.findByContact(contact, ownId);
        if (sameContact != null) {
            return "The contact number " + contact + " already belongs to " + sameContact.getFullName()
                    + (sameContact.isArchived() ? " (archived)." : ".")
                    + " Each customer needs their own number.";
        }
        return null;
    }

    private void toggleArchive() {
        if (!recordsLoaded || saving || !Session.isOwner()) {
            return;
        }
        if (selected == null) {
            UiKit.warn(this, "Select a customer in the table first.");
            return;
        }
        boolean restore = selected.isArchived();
        String question = restore
                ? "Restore " + selected.getFullName() + " so they can order again?"
                : "Archive " + selected.getFullName() + "? They will be hidden from new orders, "
                + "but their orders and payments are kept.";
        if (!UiKit.confirm(this, question)) {
            return;
        }
        int id = selected.getId();
        write(() -> {
            if (restore) {
                dao.restore(id);
            } else {
                dao.archive(id);
            }
            return null;
        }, restore ? "Customer restored." : "Customer archived.");
    }

    private void delete() {
        if (!recordsLoaded || saving || !Session.isOwner()) {
            return;
        }
        if (selected == null) {
            UiKit.warn(this, "Select a customer in the table first.");
            return;
        }
        if (!UiKit.confirm(this, "Delete " + selected.getFullName() + "? This cannot be undone.")) {
            return;
        }
        int id = selected.getId();
        write(() -> {
            if (dao.hasHistory(id)) throw new IllegalArgumentException(
                    "This customer has orders, payments, or container records. Use Archive instead.");
            dao.delete(id);
            return null;
        }, "Customer deleted.");
    }

    private void write(java.util.concurrent.Callable<Void> work, String message) {
        saving = true;
        ++loadNumber;
        if (loader != null) loader.cancel(true);
        showSelectionState();
        status.setText("Saving…");
        UiTask.run(work, ignored -> {
            saving = false;
            clearForm();
            UiKit.info(this, message);
            reload();
        }, error -> {
            saving = false;
            showSelectionState();
            if (error instanceof IllegalArgumentException) UiKit.warn(this, error.getMessage());
            else UiKit.error(this, error);
            status.setText("The change was not saved. Your entries are still available.");
        });
    }
}
