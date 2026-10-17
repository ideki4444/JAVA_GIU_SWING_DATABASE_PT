package aquatrack.ui;

import aquatrack.dao.UserDao;
import aquatrack.model.User;
import aquatrack.util.PasswordUtil;
import aquatrack.util.Session;
import aquatrack.util.Validator;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.swing.Box;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

/** Accounts (Owner only): add sign-in accounts, reset passwords, and turn accounts on or off. */
public final class AccountsPanel extends JPanel implements MainFrame.Screen {

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,50}");
    private static final int MIN_PASSWORD = 6;

    private final UserDao users = new UserDao();
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[] {"Username", "Full name", "Role", "Status"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = UiKit.table(model);
    private final JTextField usernameField = UiKit.field(18);
    private final JTextField fullNameField = UiKit.field(18);
    private final JComboBox<String> roleBox = new JComboBox<>(new String[] {"Staff", "Owner/Admin"});
    private final JPasswordField passwordField = new JPasswordField(18);
    private final UiKit.ThemedButton toggleButton = UiKit.button("Turn off", UiKit.DANGER);
    private final JLabel status = UiKit.muted(" ");
    private List<User> shown = new ArrayList<>();

    public AccountsPanel() {
        super(new BorderLayout(0, 16));
        setBackground(UiKit.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        add(UiKit.header("Accounts", "Owner only. Add staff logins, reset passwords, and turn accounts on or off."),
                BorderLayout.NORTH);

        UiKit.styleTable(table);
        UiKit.widths(table, 130, 170, 120, 90);
        UiKit.statusColumn(table, 3);
        UiKit.styleInput(roleBox);
        UiKit.styleInput(passwordField);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateToggle();
            }
        });

        JPanel left = UiKit.card(new BorderLayout(0, 10));
        left.add(UiKit.scroll(table), BorderLayout.CENTER);
        left.add(status, BorderLayout.SOUTH);

        UiKit.ThemedButton addButton = UiKit.button("Add account", UiKit.ACCENT);
        addButton.addActionListener(e -> addAccount());
        UiKit.ThemedButton resetButton = UiKit.button("Reset password of selected", UiKit.SIDEBAR);
        resetButton.addActionListener(e -> resetPassword());
        toggleButton.addActionListener(e -> toggleSelected());

        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setOpaque(false);
        column.add(UiKit.sectionTitle("New account or password"));
        column.add(Box.createVerticalStrut(14));
        column.add(UiKit.label("Username (3 to 50 letters, numbers, or _)"));
        column.add(Box.createVerticalStrut(6));
        column.add(UiKit.fullWidth(usernameField));
        column.add(Box.createVerticalStrut(12));
        column.add(UiKit.label("Full name"));
        column.add(Box.createVerticalStrut(6));
        column.add(UiKit.fullWidth(fullNameField));
        column.add(Box.createVerticalStrut(12));
        column.add(UiKit.label("Role"));
        column.add(Box.createVerticalStrut(6));
        column.add(UiKit.fullWidth(roleBox));
        column.add(Box.createVerticalStrut(12));
        column.add(UiKit.label("Password (at least 6 characters)"));
        column.add(Box.createVerticalStrut(6));
        column.add(UiKit.fullWidth(passwordField));
        column.add(Box.createVerticalStrut(18));
        column.add(UiKit.fullWidth(addButton));
        column.add(Box.createVerticalStrut(8));
        column.add(UiKit.fullWidth(resetButton));
        column.add(Box.createVerticalStrut(8));
        column.add(UiKit.fullWidth(toggleButton));

        JPanel right = UiKit.card(new BorderLayout());
        right.setPreferredSize(new Dimension(360, 0));
        right.add(column, BorderLayout.NORTH);

        add(UiKit.splitFixedRight(left, right, 340), BorderLayout.CENTER);
    }

    @Override
    public void refresh() {
        reload(selectedId());
    }

    private boolean reload(int keepId) {
        try {
            shown = users.findAll();
            model.setRowCount(0);
            int highlight = -1;
            for (int i = 0; i < shown.size(); i++) {
                User u = shown.get(i);
                model.addRow(new Object[] {u.getUsername(), u.getFullName(), u.getRole(),
                        u.isActive() ? "Active" : "Off"});
                if (u.getId() == keepId) {
                    highlight = i;
                }
            }
            if (highlight >= 0) {
                table.setRowSelectionInterval(highlight, highlight);
            } else {
                table.clearSelection();
            }
            updateToggle();
            status.setText(shown.size() + " account(s)");
            return true;
        } catch (SQLException e) {
            status.setText("Could not load accounts: " + e.getMessage());
            return false;
        }
    }

    private User selectedUser() {
        int row = table.getSelectedRow();
        return row < 0 ? null : shown.get(row);
    }

    private int selectedId() {
        User u = selectedUser();
        return u == null ? -1 : u.getId();
    }

    private void updateToggle() {
        User u = selectedUser();
        toggleButton.setText(u != null && u.isActive() ? "Turn off" : "Turn on");
    }

    private String requirePassword() {
        String password = new String(passwordField.getPassword());
        if (password.length() < MIN_PASSWORD) {
            throw new IllegalArgumentException("Password must be at least " + MIN_PASSWORD + " characters.");
        }
        return password;
    }

    private void addAccount() {
        try {
            String username = usernameField.getText().trim();
            if (!USERNAME.matcher(username).matches()) {
                throw new IllegalArgumentException("Username must be 3 to 50 letters, numbers, or underscores.");
            }
            String fullName = Validator.requireText(fullNameField.getText(), "Full name", 100);
            User sameName = users.findByFullName(fullName);
            if (sameName != null) {
                UiKit.warn(this, "The full name \"" + sameName.getFullName() + "\" already belongs to the account "
                        + sameName.getUsername() + (sameName.isActive() ? "" : " (turned off)")
                        + ". Each account needs its own full name.");
                return;
            }
            String hash = PasswordUtil.hash(requirePassword());
            users.insert(username, hash, fullName, (String) roleBox.getSelectedItem());
            usernameField.setText("");
            fullNameField.setText("");
            passwordField.setText("");
            if (reload(-1)) {
                status.setText("Account " + username + " added.");
            }
        } catch (IllegalArgumentException e) {
            UiKit.warn(this, e.getMessage());
        } catch (SQLIntegrityConstraintViolationException e) {
            UiKit.warn(this, "That username is already taken.");
        } catch (SQLException e) {
            UiKit.error(this, e);
        }
    }

    private void resetPassword() {
        User user = selectedUser();
        if (user == null) {
            UiKit.warn(this, "Select an account in the list first.");
            return;
        }
        try {
            users.setPasswordHash(user.getId(), PasswordUtil.hash(requirePassword()));
            passwordField.setText("");
            status.setText("Password changed for " + user.getUsername() + ".");
        } catch (IllegalArgumentException e) {
            UiKit.warn(this, e.getMessage());
        } catch (SQLException e) {
            UiKit.error(this, e);
        }
    }

    private void toggleSelected() {
        User user = selectedUser();
        if (user == null) {
            UiKit.warn(this, "Select an account in the list first.");
            return;
        }
        boolean turnOn = !user.isActive();
        try {
            if (!turnOn && user.getId() == Session.user().getId()) {
                UiKit.warn(this, "You cannot turn off the account you are signed in with.");
                return;
            }
            if (!turnOn && user.isOwner() && users.countActiveOwners() <= 1) {
                UiKit.warn(this, "Keep at least one active Owner/Admin account.");
                return;
            }
            users.setActive(user.getId(), turnOn);
            if (reload(user.getId())) {
                status.setText(user.getUsername() + (turnOn ? " turned on." : " turned off."));
            }
        } catch (SQLException e) {
            UiKit.error(this, e);
        }
    }
}
