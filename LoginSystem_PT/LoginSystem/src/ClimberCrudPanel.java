import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * CRUD panel for climber records.
 *
 * Create  -> Add new climber
 * Read    -> Search / display climbers
 * Update  -> Edit selected climber
 * Delete  -> Archive (never permanently deleted) + Restore
 */
public class ClimberCrudPanel extends JPanel {

    /* ---------- combo options (must match the .sql seed values) ---------- */
    private static final String[] ROLES = {
        "Night Cartographer", "Trail Guide", "Summit Photographer", "Base Camp Medic",
        "Rope Specialist", "Porter Lead", "Weather Observer", "Trail Scout"
    };
    private static final String[] CAMPS = {
        "Valley of Mist", "Twilight Ridge", "Moonlit Basin",
        "Echo Pass", "Mist fall Meadow", "Observatory Null"
    };
    private static final String[] SKILLS = {"Beginner", "Intermediate", "Advanced", "Expert"};
    private static final String[] STATUSES = {"Active", "Resting", "Injured", "Retired"};

    private static final int ARCHIVED_COL = 9;
    private static final Color HEADER_BG = new Color(0x2B2447);   // dark header (no white bar)

    private final RoundedTextField searchField   = new RoundedTextField("Search name, username, email, role, note...");
    private final RoundedTextField fullNameField = new RoundedTextField("Full name");
    private final RoundedTextField usernameField = new RoundedTextField("Username");
    private final RoundedTextField emailField    = new RoundedTextField("Email");
    private final RoundedTextField noteField     = new RoundedTextField("Note (anything you want to store)");

    private final ThemedComboBox<String> roleCombo  = new ThemedComboBox<>(ROLES);
    private final ThemedComboBox<String> campCombo  = new ThemedComboBox<>(CAMPS);
    private final ThemedComboBox<String> skillCombo = new ThemedComboBox<>(SKILLS);
    private final ThemedComboBox<String> statusCombo = new ThemedComboBox<>(STATUSES);

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"ID", "Full Name", "Username", "Email", "Role",
                         "Base Camp", "Skill", "Status", "Note", "Archived"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) { return false; }
    };

    private final JTable table = new JTable(model) {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getRowCount() == 0) {
                Graphics2D g2 = (Graphics2D) g.create();
                UIKit.antialias(g2);
                g2.setFont(UIKit.font(13f, Font.PLAIN));
                g2.setColor(UIKit.TEXT_DIM);
                String msg;
                if (showArchived) {
                    msg = "No archived climbers.";
                } else if (!searchField.getText().trim().isEmpty()) {
                    msg = "No climbers match your search.";
                } else {
                    msg = "No climbers yet - add one above to get started.";
                }
                FontMetrics fm = g2.getFontMetrics();
                int x = Math.max((getWidth() - fm.stringWidth(msg)) / 2, 0);
                g2.drawString(msg, x, getHeight() / 2);
                g2.dispose();
            }
        }
    };

    private final RoundedButton saveButton = new RoundedButton("Add climber");
    private final GhostButton clearButton  = new GhostButton("Clear / New");
    private final GhostButton archiveButton = new GhostButton("Archive");
    private final GhostButton restoreButton = new GhostButton("Restore");
    private final GhostButton archivedToggleButton = new GhostButton("Show archived: OFF");

    private boolean showArchived = false;
    private Integer selectedId = null;
    private boolean loading = false;

    public ClimberCrudPanel() {
        setOpaque(false);
        setLayout(new BorderLayout(0, 0));

        setFieldSizes();

        GlassPanel card = new GlassPanel(new BorderLayout(16, 16), 26);
        card.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));

        JPanel top = new JPanel(new BorderLayout(0, 14));
        top.setOpaque(false);
        top.add(header(), BorderLayout.NORTH);
        top.add(form(), BorderLayout.CENTER);

        card.add(top, BorderLayout.NORTH);
        card.add(tableArea(), BorderLayout.CENTER);
        add(card, BorderLayout.CENTER);

        attachListeners();
        loadData(false);
        clearForm(false);
    }

    /* ------------------------------------------------------------ */
    /* UI SETUP                                                     */
    /* ------------------------------------------------------------ */

    private void setFieldSizes() {
        fullNameField.setPreferredSize(new Dimension(0, 46));
        usernameField.setPreferredSize(new Dimension(0, 46));
        emailField.setPreferredSize(new Dimension(0, 46));
        noteField.setPreferredSize(new Dimension(0, 46));
        searchField.setPreferredSize(new Dimension(300, 46));
    }

    private JPanel header() {
        JPanel p = new JPanel(new BorderLayout(14, 0));
        p.setOpaque(false);

        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setOpaque(false);

        JLabel title = new JLabel("Climber Records");
        title.setFont(UIKit.font(22f, Font.BOLD));
        title.setForeground(UIKit.TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("Create, search, update, and archive climber records.");
        subtitle.setFont(UIKit.font(12.5f, Font.PLAIN));
        subtitle.setForeground(UIKit.TEXT_DIM);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        left.add(title);
        left.add(Box.createVerticalStrut(3));
        left.add(subtitle);

        JPanel right = new JPanel(new BorderLayout());
        right.setOpaque(false);
        right.add(searchField, BorderLayout.CENTER);

        p.add(left, BorderLayout.CENTER);
        p.add(right, BorderLayout.EAST);
        return p;
    }

    private JPanel form() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;

        /* row 0: name / username / email */
        c.gridy = 0;
        c.gridx = 0; c.weightx = 0.35; c.insets = new Insets(4, 0, 4, 10);
        form.add(fullNameField, c);
        c.gridx = 1; c.weightx = 0.30;
        form.add(usernameField, c);
        c.gridx = 2; c.weightx = 0.35; c.insets = new Insets(4, 0, 4, 0);
        form.add(emailField, c);

        /* row 1: role / base camp / skill */
        c.gridy = 1;
        c.gridx = 0; c.weightx = 0.35; c.insets = new Insets(4, 0, 4, 10);
        form.add(roleCombo, c);
        c.gridx = 1; c.weightx = 0.30;
        form.add(campCombo, c);
        c.gridx = 2; c.weightx = 0.35; c.insets = new Insets(4, 0, 4, 0);
        form.add(skillCombo, c);

        /* row 2: status + note */
        c.gridy = 2;
        c.gridx = 0; c.weightx = 0.35; c.insets = new Insets(4, 0, 4, 10);
        form.add(statusCombo, c);
        c.gridx = 1; c.gridwidth = 2; c.weightx = 0.65; c.insets = new Insets(4, 0, 4, 0);
        form.add(noteField, c);

        /* row 3: buttons */
        c.gridy = 3;
        c.gridx = 0; c.gridwidth = 3; c.insets = new Insets(12, 0, 0, 0);
        form.add(actions(), c);

        return form;
    }

    private JPanel actions() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        p.setOpaque(false);
        p.add(saveButton);
        p.add(clearButton);
        p.add(archiveButton);
        p.add(restoreButton);
        p.add(archivedToggleButton);
        return p;
    }

    private JPanel tableArea() {
        styleTable();

        JScrollPane scroll = new JScrollPane(table);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());

        scroll.getVerticalScrollBar().setUI(new ThemedScrollBarUI());
        scroll.getHorizontalScrollBar().setUI(new ThemedScrollBarUI());
        scroll.getVerticalScrollBar().setOpaque(false);
        scroll.getHorizontalScrollBar().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getHorizontalScrollBar().setUnitIncrement(16);

        GlassPanel wrap = new GlassPanel(new BorderLayout(), 22);
        wrap.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        wrap.add(scroll, BorderLayout.CENTER);
        return wrap;
    }

    private void styleTable() {
        table.setOpaque(false);
        table.setBackground(new Color(0, 0, 0, 0));
        table.setFillsViewportHeight(true);
        table.setRowHeight(36);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionBackground(new Color(231, 159, 180, 70));
        table.setSelectionForeground(UIKit.TEXT);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        int[] widths = {50, 150, 110, 190, 150, 140, 110, 90, 240, 90};
        for (int i = 0; i < widths.length; i++) {
            TableColumn col = table.getColumnModel().getColumn(i);
            col.setPreferredWidth(widths[i]);
        }

        /* ---- DARK header (replaces the white bar) ---- */
        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setOpaque(true);
        header.setBackground(HEADER_BG);
        header.setPreferredSize(new Dimension(0, 42));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(255, 255, 255, 40)));
        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable t, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                setText(value == null ? "" : value.toString());
                setOpaque(true);
                setBackground(HEADER_BG);
                setForeground(UIKit.TEXT_DIM);
                setFont(UIKit.font(11f, Font.BOLD));
                setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
                return this;
            }
        });

        /* ---- rows ---- */
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable t, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                setOpaque(true);
                setFont(UIKit.font(13f, Font.PLAIN));
                setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

                boolean archived = false;
                try {
                    archived = "Yes".equals(t.getModel().getValueAt(row, ARCHIVED_COL));
                } catch (Exception ignored) {}

                if (isSelected) {
                    setBackground(new Color(231, 159, 180, 70));
                    setForeground(UIKit.TEXT);
                } else {
                    setBackground(row % 2 == 0
                            ? new Color(255, 255, 255, 12)
                            : new Color(255, 255, 255, 24));
                    setForeground(archived ? UIKit.TEXT_DIM : UIKit.TEXT);
                }
                return this;
            }
        });
    }

    /* ------------------------------------------------------------ */
    /* EVENTS                                                       */
    /* ------------------------------------------------------------ */

    private void attachListeners() {
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { loadData(true); }
            @Override public void removeUpdate(DocumentEvent e)  { loadData(true); }
            @Override public void changedUpdate(DocumentEvent e) { loadData(true); }
        });

        saveButton.addActionListener(e -> save());
        clearButton.addActionListener(e -> clearForm(true));
        archiveButton.addActionListener(e -> archiveSelected());
        restoreButton.addActionListener(e -> restoreSelected());
        archivedToggleButton.addActionListener(e -> toggleArchivedView());

        table.getSelectionModel().addListSelectionListener(e -> {
            if (loading || e.getValueIsAdjusting()) return;
            syncFormToSelection();
        });
    }

    /* ------------------------------------------------------------ */
    /* READ / SEARCH                                                */
    /* ------------------------------------------------------------ */

    private void loadData(boolean silent) {
        loading = true;
        try {
            model.setRowCount(0);

            String sql =
                "SELECT id, full_name, username, email, role, base_camp, "
              + "       skill_level, status, note, archived "
              + "FROM climbers "
              + "WHERE (? = 1 OR archived = 0) "
              + "AND ("
              + "    full_name LIKE ? OR username LIKE ? OR email LIKE ? "
              + "    OR role LIKE ? OR base_camp LIKE ? OR skill_level LIKE ? "
              + "    OR status LIKE ? OR note LIKE ? OR CAST(id AS CHAR) LIKE ?"
              + ") "
              + "ORDER BY archived ASC, id DESC";

            try (Connection conn = Databaseconnection.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setInt(1, showArchived ? 1 : 0);

                String term = "%" + searchField.getText().trim() + "%";
                for (int i = 2; i <= 10; i++) ps.setString(i, term);

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        model.addRow(new Object[]{
                            rs.getInt("id"),
                            rs.getString("full_name"),
                            rs.getString("username"),
                            rs.getString("email"),
                            rs.getString("role"),
                            rs.getString("base_camp"),
                            rs.getString("skill_level"),
                            rs.getString("status"),
                            rs.getString("note"),
                            rs.getInt("archived") == 1 ? "Yes" : "No"
                        });
                    }
                }
            }

            if (table.getSelectedRow() < 0 && selectedId != null) {
                clearForm(false);
            }

        } catch (SQLException ex) {
            if (!silent) {
                ThemedDialogs.showMessage(this,
                        "Database error: " + ex.getMessage(),
                        "Connection Error", JOptionPane.ERROR_MESSAGE);
            }
            ex.printStackTrace();
        } finally {
            loading = false;
        }
    }

    /* ------------------------------------------------------------ */
    /* CREATE / UPDATE                                              */
    /* ------------------------------------------------------------ */

    private void save() {
        String fullName = fullNameField.getText().trim();
        String username = usernameField.getText().trim();
        String email    = emailField.getText().trim();
        String note     = noteField.getText().trim();

        String role  = (String) roleCombo.getSelectedItem();
        String camp  = (String) campCombo.getSelectedItem();
        String skill = (String) skillCombo.getSelectedItem();
        String stat  = (String) statusCombo.getSelectedItem();

        if (fullName.isEmpty() || username.isEmpty()) {
            ThemedDialogs.showMessage(this,
                    "Full name and username are required.",
                    "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try (Connection conn = Databaseconnection.getConnection()) {

            if (selectedId == null) {
                /* CREATE */
                if (usernameExists(conn, username, null)) {
                    ThemedDialogs.showMessage(this,
                            "That username is already active.",
                            "Duplicate Username", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                String sql =
                    "INSERT INTO climbers "
                  + "(full_name, username, email, role, base_camp, skill_level, status, note, archived) "
                  + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)";

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, fullName);
                    ps.setString(2, username);
                    ps.setString(3, email);
                    ps.setString(4, role);
                    ps.setString(5, camp);
                    ps.setString(6, skill);
                    ps.setString(7, stat);
                    ps.setString(8, note);
                    ps.executeUpdate();
                }

                ThemedDialogs.showMessage(this, "Climber added successfully.",
                        "Saved", JOptionPane.INFORMATION_MESSAGE);
                clearForm(true);
                loadData(false);

            } else {
                /* UPDATE */
                if (usernameExists(conn, username, selectedId)) {
                    ThemedDialogs.showMessage(this,
                            "That username is already active.",
                            "Duplicate Username", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                String sql =
                    "UPDATE climbers "
                  + "SET full_name = ?, username = ?, email = ?, role = ?, "
                  + "    base_camp = ?, skill_level = ?, status = ?, note = ? "
                  + "WHERE id = ?";

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, fullName);
                    ps.setString(2, username);
                    ps.setString(3, email);
                    ps.setString(4, role);
                    ps.setString(5, camp);
                    ps.setString(6, skill);
                    ps.setString(7, stat);
                    ps.setString(8, note);
                    ps.setInt(9, selectedId);

                    int updated = ps.executeUpdate();
                    if (updated == 0) {
                        ThemedDialogs.showMessage(this, "That record no longer exists.",
                                "Not Found", JOptionPane.ERROR_MESSAGE);
                    } else {
                        ThemedDialogs.showMessage(this, "Climber updated successfully.",
                                "Updated", JOptionPane.INFORMATION_MESSAGE);
                    }
                }

                clearForm(true);
                loadData(false);
            }

        } catch (SQLException ex) {
            ThemedDialogs.showMessage(this,
                    "Database error: " + ex.getMessage(),
                    "Connection Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private boolean usernameExists(Connection conn, String username, Integer ignoreId)
            throws SQLException {
        String sql = "SELECT id FROM climbers WHERE username = ? AND archived = 0";
        if (ignoreId != null) sql += " AND id <> ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            if (ignoreId != null) ps.setInt(2, ignoreId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /* ------------------------------------------------------------ */
    /* ARCHIVE / RESTORE                                            */
    /* ------------------------------------------------------------ */

    private void archiveSelected() {
        if (selectedId == null) {
            ThemedDialogs.showMessage(this, "Select a climber row first.",
                    "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = ThemedDialogs.showConfirm(this,
                "Archive this climber record? It will remain in the database as archived.",
                "Confirm Archive");
        if (confirm != JOptionPane.YES_OPTION) return;

        if (updateArchived(selectedId, 1)) {
            ThemedDialogs.showMessage(this, "Climber archived.",
                    "Archived", JOptionPane.INFORMATION_MESSAGE);
            clearForm(true);
            loadData(false);
        }
    }

    private void restoreSelected() {
        if (selectedId == null) {
            ThemedDialogs.showMessage(this, "Select an archived climber row first.",
                    "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = ThemedDialogs.showConfirm(this,
                "Restore this climber record to active?",
                "Confirm Restore");
        if (confirm != JOptionPane.YES_OPTION) return;

        if (updateArchived(selectedId, 0)) {
            ThemedDialogs.showMessage(this, "Climber restored.",
                    "Restored", JOptionPane.INFORMATION_MESSAGE);
            clearForm(true);
            loadData(false);
        }
    }

    private boolean updateArchived(int id, int archived) {
        String sql = "UPDATE climbers SET archived = ? WHERE id = ?";
        try (Connection conn = Databaseconnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, archived);
            ps.setInt(2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) {
            ThemedDialogs.showMessage(this,
                    "Database error: " + ex.getMessage(),
                    "Connection Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private void toggleArchivedView() {
        showArchived = !showArchived;
        archivedToggleButton.setText(showArchived ? "Show archived: ON" : "Show archived: OFF");
        clearForm(true);
        loadData(false);
    }

    /* ------------------------------------------------------------ */
    /* FORM / TABLE SYNC                                            */
    /* ------------------------------------------------------------ */

    private void syncFormToSelection() {
        int row = table.getSelectedRow();
        if (row < 0) {
            clearForm(false);
            return;
        }

        selectedId = (Integer) model.getValueAt(row, 0);

        fullNameField.setText(asString(model.getValueAt(row, 1)));
        usernameField.setText(asString(model.getValueAt(row, 2)));
        emailField.setText(asString(model.getValueAt(row, 3)));
        setCombo(roleCombo,   asString(model.getValueAt(row, 4)));
        setCombo(campCombo,   asString(model.getValueAt(row, 5)));
        setCombo(skillCombo,  asString(model.getValueAt(row, 6)));
        setCombo(statusCombo, asString(model.getValueAt(row, 7)));
        noteField.setText(asString(model.getValueAt(row, 8)));

        boolean archived = "Yes".equals(model.getValueAt(row, ARCHIVED_COL));

        saveButton.setText("Update climber");
        saveButton.setEnabled(!archived);
        archiveButton.setEnabled(!archived);
        restoreButton.setEnabled(archived);
    }

    private void setCombo(JComboBox<String> combo, String value) {
        combo.setSelectedItem(value);
        if (combo.getSelectedIndex() < 0) combo.setSelectedIndex(0);
    }

    private void clearForm(boolean clearTableSelection) {
        fullNameField.setText("");
        usernameField.setText("");
        emailField.setText("");
        noteField.setText("");

        roleCombo.setSelectedIndex(0);
        campCombo.setSelectedIndex(0);
        skillCombo.setSelectedIndex(0);
        statusCombo.setSelectedIndex(0);

        selectedId = null;
        saveButton.setText("Add climber");
        saveButton.setEnabled(true);
        archiveButton.setEnabled(false);
        restoreButton.setEnabled(false);

        if (clearTableSelection) table.clearSelection();
    }

    private String asString(Object value) {
        return value == null ? "" : value.toString();
    }
}