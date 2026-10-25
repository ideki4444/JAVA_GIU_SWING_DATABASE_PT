package aquatrack.ui;

import aquatrack.dao.UserDao;
import aquatrack.model.User;
import aquatrack.util.PasswordUtil;
import aquatrack.util.Session;
import aquatrack.util.Validator;
import java.awt.BorderLayout;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.swing.Box;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;

/** Selecting an account opens its editable details; password reset is an independent action. */
public final class AccountsPanel extends JPanel implements MainFrame.Screen {
    interface Data {
        List<User> findAll() throws SQLException;
        User save(int actorId, int userId, String username, String name, String role, boolean active, String hash) throws SQLException;
        User resetPassword(int actorId, int userId, String hash) throws SQLException;
    }
    interface Dialogs {
        int unsaved();
        String newPassword(String username);
        void problem(String message);
    }

    private final Data data;
    private final Dialogs dialogs;
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[] {"Username", "Full name", "Role", "Status"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = UiKit.table(model);
    private final JTextField usernameField = UiKit.field(18);
    private final JTextField fullNameField = UiKit.field(18);
    private final JComboBox<String> roleBox = new JComboBox<>(new String[] {"Staff", "Owner/Admin"});
    private final JCheckBox activeBox = new JCheckBox("Account can sign in", true);
    private final JPasswordField passwordField = new JPasswordField(18);
    private final JLabel passwordLabel = UiKit.label("Initial password (at least 6 characters)");
    private final JLabel formTitle = UiKit.sectionTitle("New account");
    private final UiKit.ThemedButton newButton = UiKit.button("New account", UiKit.SIDEBAR);
    private final UiKit.ThemedButton saveButton = UiKit.button("Create account", UiKit.ACCENT);
    private final UiKit.ThemedButton resetButton = UiKit.button("Reset password...", UiKit.SIDEBAR);
    private final JLabel status = UiKit.muted("Select an account to edit, or choose New account.");
    private List<User> shown = new ArrayList<>();
    private User editing;
    private boolean applying, busy, recordsLoaded;
    private long generation;

    public AccountsPanel() { this(defaultData(), null); }

    AccountsPanel(Data data, Dialogs dialogs) {
        super(new BorderLayout(0, 16));
        this.data = data;
        this.dialogs = dialogs == null ? new Dialogs() {
            @Override public int unsaved() {
                return JOptionPane.showOptionDialog(AccountsPanel.this,
                        "Save your account changes before leaving this form?", "Unsaved account changes",
                        JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                        new String[] {"Save", "Discard", "Cancel"}, "Save");
            }
            @Override public String newPassword(String username) {
                JPasswordField input = new JPasswordField(20);
                UiKit.styleInput(input);
                Object[] message = {"New password for " + username + " (at least 6 characters)", input};
                return JOptionPane.showConfirmDialog(AccountsPanel.this, message, "Reset password",
                        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION
                        ? new String(input.getPassword()) : null;
            }
            @Override public void problem(String message) { UiKit.warn(AccountsPanel.this, message); }
        } : dialogs;
        setBackground(UiKit.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        add(UiKit.header("Accounts", "Select an account to edit its details. Use New account to create a login."), BorderLayout.NORTH);
        UiKit.styleTable(table);
        UiKit.widths(table, 130, 170, 120, 90);
        UiKit.statusColumn(table, 3);
        UiKit.styleInput(roleBox);
        UiKit.styleInput(passwordField);
        activeBox.setOpaque(false);
        activeBox.setFont(UiKit.BODY);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || applying || busy) return;
            User requested = selectedUser();
            if (sameAccount(requested, editing)) return;
            selectEditing();
            Runnable change = () -> loadUser(requested);
            if (requestLeave(change)) change.run();
        });
        DocumentListener edits = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { updateControls(); }
            @Override public void removeUpdate(DocumentEvent e) { updateControls(); }
            @Override public void changedUpdate(DocumentEvent e) { updateControls(); }
        };
        usernameField.getDocument().addDocumentListener(edits);
        fullNameField.getDocument().addDocumentListener(edits);
        passwordField.getDocument().addDocumentListener(edits);
        roleBox.addActionListener(e -> updateControls());
        activeBox.addActionListener(e -> updateControls());
        newButton.addActionListener(e -> {
            Runnable change = () -> { loadUser(null); usernameField.requestFocusInWindow(); };
            if (requestLeave(change)) change.run();
        });
        saveButton.addActionListener(e -> save(null));
        resetButton.addActionListener(e -> {
            Runnable reset = this::resetPassword;
            if (requestLeave(reset)) reset.run();
        });
        JPanel left = UiKit.card(new BorderLayout(0, 10));
        left.add(newButton, BorderLayout.NORTH);
        left.add(UiKit.scroll(table), BorderLayout.CENTER);
        left.add(status, BorderLayout.SOUTH);
        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setOpaque(false);
        column.add(formTitle);
        column.add(Box.createVerticalStrut(14));
        addField(column, "Username (3 to 50 letters, numbers, or _)", usernameField);
        addField(column, "Full name", fullNameField);
        addField(column, "Role", roleBox);
        column.add(activeBox);
        column.add(Box.createVerticalStrut(12));
        column.add(passwordLabel);
        column.add(Box.createVerticalStrut(6));
        column.add(UiKit.fullWidth(passwordField));
        column.add(Box.createVerticalStrut(18));
        column.add(UiKit.fullWidth(saveButton));
        column.add(Box.createVerticalStrut(8));
        column.add(UiKit.fullWidth(resetButton));
        JPanel right = UiKit.card(new BorderLayout());
        right.add(column, BorderLayout.NORTH);
        add(UiKit.splitFixedRight(left, right, 360), BorderLayout.CENTER);
        updateControls();
    }

    private static Data defaultData() {
        UserDao dao = new UserDao();
        return new Data() {
            @Override public List<User> findAll() throws SQLException { return dao.findAll(); }
            @Override public User save(int actor, int id, String username, String name, String role, boolean active, String hash) throws SQLException {
                return dao.save(actor, id, username, name, role, active, hash);
            }
            @Override public User resetPassword(int actor, int id, String hash) throws SQLException {
                return dao.resetPassword(actor, id, hash);
            }
        };
    }

    private static void addField(JPanel panel, String text, javax.swing.JComponent field) {
        panel.add(UiKit.label(text));
        panel.add(Box.createVerticalStrut(6));
        panel.add(UiKit.fullWidth(field));
        panel.add(Box.createVerticalStrut(12));
    }

    /** Returns true for immediate leave; Save invokes continuation only after a successful commit. */
    public boolean requestLeave(Runnable continuation) {
        if (busy || !recordsLoaded && isDirty()) {
            status.setText("Please wait for the account operation to finish.");
            return false;
        }
        if (!isDirty()) return true;
        int choice = dialogs.unsaved();
        if (choice == 0) { save(continuation); return false; }
        if (choice == 1) { loadUser(editing); return true; }
        return false;
    }

    @Override public void refresh() { if (!busy) reload(editing == null ? -1 : editing.getId()); }
    @Override public boolean hasPendingOperation() { return busy; }

    private void reload(int keepId) {
        long request = ++generation;
        recordsLoaded = false;
        updateControls();
        status.setText("Loading accounts...");
        new SwingWorker<List<User>, Void>() {
            @Override protected List<User> doInBackground() throws Exception { return data.findAll(); }
            @Override protected void done() {
                if (request != generation) return;
                try {
                    List<User> fresh = get();
                    boolean dirty = isDirty();
                    applying = true;
                    shown = fresh;
                    model.setRowCount(0);
                    User selected = null;
                    for (User user : shown) {
                        model.addRow(new Object[] {user.getUsername(), user.getFullName(), user.getRole(), user.isActive() ? "Active" : "Off"});
                        if (user.getId() == keepId) selected = user;
                    }
                    applying = false;
                    recordsLoaded = true;
                    if (dirty) selectEditing(); else loadUser(selected);
                    status.setText(shown.size() + " account(s). Select a row to edit.");
                } catch (Exception e) {
                    applying = false;
                    status.setText("Could not load accounts. Try opening Accounts again.");
                    dialogs.problem(problem(e));
                }
                updateControls();
            }
        }.execute();
    }

    private User selectedUser() {
        int row = table.getSelectedRow();
        int index = row < 0 ? -1 : table.convertRowIndexToModel(row);
        return index < 0 || index >= shown.size() ? null : shown.get(index);
    }

    private static boolean sameAccount(User first, User second) {
        return first == second || first != null && second != null && first.getId() == second.getId();
    }

    private void selectEditing() {
        boolean before = applying;
        applying = true;
        table.clearSelection();
        if (editing != null) {
            for (int i = 0; i < shown.size(); i++) if (shown.get(i).getId() == editing.getId()) {
                int view = table.convertRowIndexToView(i);
                if (view >= 0) table.setRowSelectionInterval(view, view);
                break;
            }
        }
        applying = before;
    }

    private void loadUser(User user) {
        applying = true;
        editing = user;
        usernameField.setText(user == null ? "" : user.getUsername());
        fullNameField.setText(user == null ? "" : user.getFullName());
        roleBox.setSelectedItem(user == null ? "Staff" : user.getRole());
        activeBox.setSelected(user == null || user.isActive());
        passwordField.setText("");
        passwordField.setVisible(user == null);
        passwordLabel.setVisible(user == null);
        formTitle.setText(user == null ? "New account" : "Edit account");
        saveButton.setText(user == null ? "Create account" : "Save changes");
        selectEditing();
        applying = false;
        updateControls();
        revalidate();
    }

    /** Keep the visible row current even when a follow-up password reset delays reloading. */
    private void showSavedUser(User user) {
        boolean previousApplying = applying;
        applying = true;
        shown = new ArrayList<>(shown);
        int index = -1;
        for (int i = 0; i < shown.size(); i++) {
            if (shown.get(i).getId() == user.getId()) { index = i; break; }
        }
        if (index < 0) {
            shown.add(user);
            model.addRow(new Object[] {user.getUsername(), user.getFullName(), user.getRole(), user.isActive() ? "Active" : "Off"});
        } else {
            shown.set(index, user);
            model.setValueAt(user.getUsername(), index, 0);
            model.setValueAt(user.getFullName(), index, 1);
            model.setValueAt(user.getRole(), index, 2);
            model.setValueAt(user.isActive() ? "Active" : "Off", index, 3);
        }
        applying = previousApplying;
    }

    private boolean isDirty() {
        if (editing == null) return !usernameField.getText().trim().isEmpty() || !fullNameField.getText().trim().isEmpty()
                || passwordField.getPassword().length != 0 || !"Staff".equals(roleBox.getSelectedItem()) || !activeBox.isSelected();
        return !editing.getUsername().equals(usernameField.getText().trim())
                || !editing.getFullName().equals(fullNameField.getText().trim())
                || !editing.getRole().equals(roleBox.getSelectedItem()) || editing.isActive() != activeBox.isSelected();
    }

    private void updateControls() {
        if (applying) return;
        boolean ready = recordsLoaded && !busy && Session.isOwner();
        User actor = Session.user();
        boolean self = editing != null && actor != null && editing.getId() == actor.getId();
        table.setEnabled(!busy && recordsLoaded);
        usernameField.setEnabled(ready);
        fullNameField.setEnabled(ready);
        passwordField.setEnabled(ready);
        roleBox.setEnabled(ready && !self);
        activeBox.setEnabled(ready && !self);
        newButton.setEnabled(ready);
        saveButton.setEnabled(ready && isDirty());
        resetButton.setEnabled(ready && editing != null);
    }

    private void save(Runnable continuation) {
        if (busy || !recordsLoaded) return;
        User actor = Session.user();
        if (actor == null || !Session.isOwner()) { dialogs.problem("An active Owner/Admin account is required."); return; }
        try {
            String username = usernameField.getText().trim();
            if (!username.matches("[A-Za-z0-9_]{3,50}")) throw new IllegalArgumentException("Username must be 3 to 50 letters, numbers, or underscores.");
            String name = Validator.requireText(fullNameField.getText(), "Full name", 100);
            String role = (String) roleBox.getSelectedItem();
            boolean active = activeBox.isSelected();
            int id = editing == null ? -1 : editing.getId();
            String password = id == -1 ? requirePassword(new String(passwordField.getPassword())) : null;
            busy = true;
            generation++;
            updateControls();
            status.setText("Saving account...");
            new SwingWorker<User, Void>() {
                @Override protected User doInBackground() throws Exception {
                    return data.save(actor.getId(), id, username, name, role, active, password == null ? null : PasswordUtil.hash(password));
                }
                @Override protected void done() {
                    busy = false;
                    try {
                        User saved = get();
                        showSavedUser(saved);
                        loadUser(saved);
                        Session.refresh(saved);
                        status.setText("Account " + saved.getUsername() + " saved.");
                        if (continuation != null) continuation.run();
                        if (!busy) reload(editing == null ? -1 : editing.getId());
                    } catch (Exception e) { dialogs.problem(problem(e)); status.setText("Account was not saved. Your entries are still here."); }
                    updateControls();
                }
            }.execute();
        } catch (IllegalArgumentException e) { dialogs.problem(e.getMessage()); }
    }

    private void resetPassword() {
        if (busy || editing == null || !recordsLoaded) return;
        User actor = Session.user();
        if (actor == null || !Session.isOwner()) { dialogs.problem("An active Owner/Admin account is required."); return; }
        String entered = dialogs.newPassword(editing.getUsername());
        if (entered == null) return;
        try {
            String password = requirePassword(entered);
            int id = editing.getId();
            busy = true;
            generation++;
            updateControls();
            status.setText("Resetting password...");
            new SwingWorker<User, Void>() {
                @Override protected User doInBackground() throws Exception {
                    return data.resetPassword(actor.getId(), id, PasswordUtil.hash(password));
                }
                @Override protected void done() {
                    busy = false;
                    try {
                        User updated = get();
                        loadUser(updated);
                        Session.refresh(updated);
                        status.setText("Password changed for " + updated.getUsername() + ".");
                    } catch (Exception e) { dialogs.problem(problem(e)); status.setText("Password was not changed."); }
                    updateControls();
                }
            }.execute();
        } catch (IllegalArgumentException e) { dialogs.problem(e.getMessage()); }
    }

    private static String requirePassword(String password) {
        if (password.length() < 6) throw new IllegalArgumentException("Password must be at least 6 characters.");
        return password;
    }

    private static String problem(Exception exception) {
        Throwable cause = exception instanceof ExecutionException ? exception.getCause() : exception;
        if (cause instanceof SQLIntegrityConstraintViolationException) return "That username is already taken. Choose a different username.";
        return cause.getMessage() == null ? "The account operation could not be completed." : cause.getMessage();
    }
}
