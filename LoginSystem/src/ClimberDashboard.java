// ============================================================================
// ClimberDashboard.java — TWILIGHT PEAKS CLIMBER MANAGEMENT SYSTEM
// ----------------------------------------------------------------------------
// A full CRUD (Create, Read, Update, Archive/Delete) system for managing
// climber profiles, built with the Twilight Peaks design aesthetic.
//
// Features:
//   • Create: Add new climber records
//   • Read: View and search existing climbers
//   • Update: Edit existing climber information
//   • Delete: Archive climbers (soft delete) instead of permanent removal
// ============================================================================
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.*;
import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class ClimberDashboard extends JFrame {

    private final CardLayout contentCards = new CardLayout();
    private final JPanel contentPanel = new JPanel(contentCards);
    private final Map<String, NavButton> nav = new LinkedHashMap<>();
    
    // Table components for Read operation
    private JTable climbersTable;
    private DefaultTableModel tableModel;
    private JTextField searchField;
    
    // Form fields for Create/Update operations
    private RoundedTextField nameField;
    private RoundedTextField emailField;
    private RoundedTextField phoneField;
    private JComboBox<String> experienceBox;
    private JTextArea notesArea;
    
    private Connection con;
    private boolean isUpdateMode = false;
    private int currentClimberId = -1;

    public ClimberDashboard() {
        setTitle("Climber Management — Twilight Peaks");
        setSize(1200, 750);
        setMinimumSize(new Dimension(980, 620));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initializeDatabase();
        
        ScenePanel root = new ScenePanel();
        root.setLayout(new BorderLayout(14, 14));
        root.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        /* ---------- top bar ---------- */
        GlassPanel topBar = new GlassPanel(new BorderLayout(), 24);
        topBar.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 18));
        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        brand.setOpaque(false);
        brand.add(new MoonLabel());
        JLabel appName = new JLabel("Twilight Peaks — Climber Management");
        appName.setFont(UIKit.font(17f, Font.BOLD));
        appName.setForeground(UIKit.TEXT);
        brand.add(appName);
        topBar.add(brand, BorderLayout.WEST);
        JLabel greeting = new JLabel("Manage your climbing team");
        greeting.setFont(UIKit.font(12.5f, Font.PLAIN));
        greeting.setForeground(UIKit.TEXT_DIM);
        topBar.add(greeting, BorderLayout.EAST);
        root.add(topBar, BorderLayout.NORTH);

        /* ---------- sidebar ---------- */
        GlassPanel sidebar = new GlassPanel(new GridBagLayout(), 24);
        GridBagConstraints sc = new GridBagConstraints();
        sc.gridx = 0; sc.weightx = 1; sc.fill = GridBagConstraints.HORIZONTAL;
        sc.insets = new Insets(6, 10, 6, 10);

        JLabel navTitle = new JLabel("  MENU");
        navTitle.setFont(UIKit.font(11f, Font.BOLD));
        navTitle.setForeground(UIKit.TEXT_DIM);
        sc.gridy = 0; sc.insets = new Insets(16, 14, 8, 10); 
        sidebar.add(navTitle, sc);
        sc.insets = new Insets(6, 10, 6, 10);

        String[] items = {"All Climbers", "Add New", "Archived"};
        int y = 1;
        for (String item : items) {
            NavButton b = new NavButton(item);
            nav.put(item, b);
            sc.gridy = y++;
            sidebar.add(b, sc);
            b.addActionListener(e -> select(item));
        }
        sc.gridy = y++; sc.weighty = 1;
        sidebar.add(Box.createGlue(), sc);
        sc.weighty = 0;

        GhostButton logoutButton = new GhostButton("Logout");
        sc.gridy = y; sc.insets = new Insets(6, 10, 16, 10);
        sidebar.add(logoutButton, sc);
        logoutButton.addActionListener(e -> {
            int confirm = ThemedDialogs.showConfirm(logoutButton, 
                    "Are you sure you want to logout?", "Confirm Logout");
            if (confirm == JOptionPane.YES_OPTION) {
                dispose();
                SwingUtilities.invokeLater(() -> new Loginform().setVisible(true));
            }
        });
        sidebar.setPreferredSize(new Dimension(220, 10));
        root.add(sidebar, BorderLayout.WEST);

        /* ---------- content cards ---------- */
        contentPanel.setOpaque(false);
        contentPanel.add(allClimbersCard(), "All Climbers");
        contentPanel.add(addClimberCard(), "Add New");
        contentPanel.add(archivedCard(), "Archived");
        root.add(contentPanel, BorderLayout.CENTER);

        setContentPane(root);
        select("All Climbers");
    }

    private void select(String name) {
        for (Map.Entry<String, NavButton> e : nav.entrySet())
            e.getValue().setSelected(e.getKey().equals(name));
        contentCards.show(contentPanel, name);
        
        // Refresh data when switching tabs
        if ("All Climbers".equals(name)) {
            displayTable_climbers();
        } else if ("Archived".equals(name)) {
            displayTable_archived();
        }
    }

    /* ==================== DATABASE INITIALIZATION ==================== */
    private void initializeDatabase() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/login_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Manila", 
                "root", "");
            
            // Create climbers table if not exists
            String createTableSQL = """
                CREATE TABLE IF NOT EXISTS climbers (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    email VARCHAR(100) UNIQUE NOT NULL,
                    phone VARCHAR(20),
                    experience_level VARCHAR(50),
                    notes TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                )
                """;
            con.createStatement().execute(createTableSQL);
            
            // Create archived_climbers table for soft delete
            String createArchiveTableSQL = """
                CREATE TABLE IF NOT EXISTS archived_climbers (
                    archive_id INT AUTO_INCREMENT PRIMARY KEY,
                    original_id INT NOT NULL,
                    name VARCHAR(100) NOT NULL,
                    email VARCHAR(100) NOT NULL,
                    phone VARCHAR(20),
                    experience_level VARCHAR(50),
                    notes TEXT,
                    archived_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;
            con.createStatement().execute(createArchiveTableSQL);
            
            System.out.println("Database tables initialized successfully!");
        } catch (Exception e) {
            System.err.println("Database initialization error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /* ==================== ALL CLIMBERS CARD (READ + SEARCH) ==================== */
    private JPanel allClimbersCard() {
        GlassPanel card = new GlassPanel(new BorderLayout(18, 18), 26);
        card.setBorder(BorderFactory.createEmptyBorder(26, 28, 26, 28));

        // Header with search
        JPanel head = new JPanel(new BorderLayout(12, 12));
        head.setOpaque(false);
        
        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titlePanel.setOpaque(false);
        JLabel t = new JLabel("All Climbers");
        t.setFont(UIKit.font(22f, Font.BOLD)); 
        t.setForeground(UIKit.TEXT);
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel s = new JLabel("View and manage your climbing team members");
        s.setFont(UIKit.font(12.5f, Font.PLAIN)); 
        s.setForeground(UIKit.TEXT_DIM);
        s.setAlignmentX(Component.LEFT_ALIGNMENT);
        titlePanel.add(t); 
        titlePanel.add(Box.createVerticalStrut(4)); 
        titlePanel.add(s);
        head.add(titlePanel, BorderLayout.CENTER);
        
        // Search field
        searchField = new RoundedTextField("Search by name or email...");
        searchField.setPreferredSize(new Dimension(280, 46));
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                filterTable(searchField.getText().trim());
            }
        });
        head.add(searchField, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);

        // Table for displaying climbers
        String[] columns = {"ID", "Name", "Email", "Phone", "Experience", "Actions"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 5; // Only actions column is editable
            }
        };
        
        climbersTable = new JTable(tableModel);
        climbersTable.setRowHeight(42);
        climbersTable.setFont(UIKit.font(12f, Font.PLAIN));
        climbersTable.setForeground(UIKit.TEXT);
        climbersTable.setSelectionBackground(UIKit.GLOW_PINK);
        climbersTable.setSelectionForeground(UIKit.INK_ON_ACCENT);
        climbersTable.getTableHeader().setFont(UIKit.font(12f, Font.BOLD));
        climbersTable.getTableHeader().setBackground(UIKit.CARD_BG);
        climbersTable.getTableHeader().setForeground(UIKit.TEXT);
        climbersTable.setGridColor(UIKit.LINE);
        climbersTable.setShowVerticalLines(true);
        climbersTable.setShowHorizontalLines(true);
        
        // Set column widths
        climbersTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        climbersTable.getColumnModel().getColumn(1).setPreferredWidth(180);
        climbersTable.getColumnModel().getColumn(2).setPreferredWidth(220);
        climbersTable.getColumnModel().getColumn(3).setPreferredWidth(120);
        climbersTable.getColumnModel().getColumn(4).setPreferredWidth(130);
        climbersTable.getColumnModel().getColumn(5).setPreferredWidth(180);
        
        JScrollPane scrollPane = new JScrollPane(climbersTable);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Custom renderer for table cells
        climbersTable.setDefaultRenderer(Object.class, new TableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel label = new JLabel(value == null ? "" : value.toString());
                label.setFont(UIKit.font(12f, Font.PLAIN));
                label.setForeground(isSelected ? UIKit.INK_ON_ACCENT : UIKit.TEXT);
                label.setOpaque(true);
                label.setBackground(isSelected ? UIKit.GLOW_PINK : new Color(0, 0, 0, 0));
                label.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
                return label;
            }
        });
        
        // Renderer for actions column with buttons
        climbersTable.getColumnModel().getColumn(5).setCellRenderer(new ButtonRenderer());
        climbersTable.getColumnModel().getColumn(5).setCellEditor(new ButtonEditor(
            new JCheckBox(), climbersTable, false));
        
        card.add(scrollPane, BorderLayout.CENTER);
        
        // Footer with stats
        JLabel stats = new JLabel("Tip: Use the search box to quickly find climbers");
        stats.setFont(UIKit.font(11f, Font.ITALIC));
        stats.setForeground(UIKit.TEXT_DIM);
        card.add(stats, BorderLayout.SOUTH);
        
        return card;
    }

    /* ==================== ADD NEW CLIMBER CARD (CREATE) ==================== */
    private JPanel addClimberCard() {
        GlassPanel card = new GlassPanel(new GridBagLayout(), 26);
        card.setBorder(BorderFactory.createEmptyBorder(30, 40, 30, 40));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(8, 40, 8, 40);

        JLabel t = new JLabel("Add New Climber", SwingConstants.CENTER);
        t.setFont(UIKit.font(22f, Font.BOLD)); 
        t.setForeground(UIKit.TEXT);
        c.gridy = 0; 
        card.add(t, c);

        JLabel subtitle = new JLabel("Enter the climber's information below", SwingConstants.CENTER);
        subtitle.setFont(UIKit.font(12f, Font.PLAIN)); 
        subtitle.setForeground(UIKit.TEXT_DIM);
        c.gridy = 1; 
        c.insets = new Insets(0, 40, 20, 40);
        card.add(subtitle, c);
        c.insets = new Insets(8, 40, 8, 40);

        // Name field
        c.gridy = 2;
        JLabel nameLabel = new JLabel("Full Name");
        nameLabel.setFont(UIKit.font(12f, Font.BOLD));
        nameLabel.setForeground(UIKit.TEXT);
        card.add(nameLabel, c);
        
        c.gridy = 3;
        nameField = new RoundedTextField("Enter full name");
        nameField.setPreferredSize(new Dimension(0, 46));
        card.add(nameField, c);

        // Email field
        c.gridy = 4;
        JLabel emailLabel = new JLabel("Email Address");
        emailLabel.setFont(UIKit.font(12f, Font.BOLD));
        emailLabel.setForeground(UIKit.TEXT);
        card.add(emailLabel, c);
        
        c.gridy = 5;
        emailField = new RoundedTextField("Enter email address");
        emailField.setPreferredSize(new Dimension(0, 46));
        card.add(emailField, c);

        // Phone field
        c.gridy = 6;
        JLabel phoneLabel = new JLabel("Phone Number");
        phoneLabel.setFont(UIKit.font(12f, Font.BOLD));
        phoneLabel.setForeground(UIKit.TEXT);
        card.add(phoneLabel, c);
        
        c.gridy = 7;
        phoneField = new RoundedTextField("Enter phone number");
        phoneField.setPreferredSize(new Dimension(0, 46));
        card.add(phoneField, c);

        // Experience level dropdown
        c.gridy = 8;
        JLabel expLabel = new JLabel("Experience Level");
        expLabel.setFont(UIKit.font(12f, Font.BOLD));
        expLabel.setForeground(UIKit.TEXT);
        card.add(expLabel, c);
        
        c.gridy = 9;
        String[] experiences = {"Beginner", "Intermediate", "Advanced", "Expert", "Professional"};
        experienceBox = new JComboBox<>(experiences);
        experienceBox.setFont(UIKit.font(13f, Font.PLAIN));
        experienceBox.setForeground(UIKit.TEXT);
        experienceBox.setBackground(UIKit.FIELD_BG);
        experienceBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                label.setFont(UIKit.font(13f, Font.PLAIN));
                label.setForeground(UIKit.TEXT);
                label.setBackground(isSelected ? UIKit.GLOW_PINK : UIKit.FIELD_BG);
                label.setOpaque(true);
                label.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
                return label;
            }
        });
        card.add(experienceBox, c);

        // Notes area
        c.gridy = 10;
        JLabel notesLabel = new JLabel("Additional Notes (Optional)");
        notesLabel.setFont(UIKit.font(12f, Font.BOLD));
        notesLabel.setForeground(UIKit.TEXT);
        card.add(notesLabel, c);
        
        c.gridy = 11;
        notesArea = new JTextArea(4, 20);
        notesArea.setFont(UIKit.font(12f, Font.PLAIN));
        notesArea.setForeground(UIKit.TEXT);
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        notesArea.setOpaque(false);
        notesArea.setBackground(new Color(0, 0, 0, 0));
        notesArea.setCaretColor(UIKit.MOON);
        notesArea.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIKit.LINE, 1, true),
            BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        
        JScrollPane notesScroll = new JScrollPane(notesArea);
        notesScroll.setOpaque(false);
        notesScroll.getViewport().setOpaque(false);
        notesScroll.setBorder(BorderFactory.createEmptyBorder());
        card.add(notesScroll, c);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 0));
        buttonPanel.setOpaque(false);
        
        RoundedButton saveButton = new RoundedButton("Save Climber");
        saveButton.addActionListener(e -> saveClimber());
        buttonPanel.add(saveButton);
        
        GhostButton clearButton = new GhostButton("Clear Form");
        clearButton.addActionListener(e -> clearForm());
        buttonPanel.add(clearButton);
        
        c.gridy = 12; 
        c.insets = new Insets(20, 40, 10, 40);
        card.add(buttonPanel, c);
        
        return card;
    }

    /* ==================== ARCHIVED CLIMBERS CARD ==================== */
    private JPanel archivedCard() {
        GlassPanel card = new GlassPanel(new BorderLayout(18, 18), 26);
        card.setBorder(BorderFactory.createEmptyBorder(26, 28, 26, 28));

        JPanel head = new JPanel(new BorderLayout(12, 12));
        head.setOpaque(false);
        
        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titlePanel.setOpaque(false);
        JLabel t = new JLabel("Archived Climbers");
        t.setFont(UIKit.font(22f, Font.BOLD)); 
        t.setForeground(UIKit.TEXT);
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel s = new JLabel("Previously archived team members (soft-deleted records)");
        s.setFont(UIKit.font(12.5f, Font.PLAIN)); 
        s.setForeground(UIKit.TEXT_DIM);
        s.setAlignmentX(Component.LEFT_ALIGNMENT);
        titlePanel.add(t); 
        titlePanel.add(Box.createVerticalStrut(4)); 
        titlePanel.add(s);
        head.add(titlePanel, BorderLayout.CENTER);
        card.add(head, BorderLayout.NORTH);

        // Table for archived climbers
        String[] columns = {"Archive ID", "Original ID", "Name", "Email", "Phone", "Experience", "Archived Date"};
        DefaultTableModel archiveTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        JTable archiveTable = new JTable(archiveTableModel);
        archiveTable.setRowHeight(40);
        archiveTable.setFont(UIKit.font(12f, Font.PLAIN));
        archiveTable.setForeground(UIKit.TEXT);
        archiveTable.getTableHeader().setFont(UIKit.font(12f, Font.BOLD));
        archiveTable.getTableHeader().setBackground(UIKit.CARD_BG);
        archiveTable.getTableHeader().setForeground(UIKit.TEXT);
        
        JScrollPane scrollPane = new JScrollPane(archiveTable);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        card.add(scrollPane, BorderLayout.CENTER);
        
        // Restore button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setOpaque(false);
        
        RoundedButton restoreBtn = new RoundedButton("Restore Selected");
        restoreBtn.addActionListener(e -> {
            int selectedRow = archiveTable.getSelectedRow();
            if (selectedRow >= 0) {
                restoreClimber(archiveTableModel, selectedRow);
            } else {
                ThemedDialogs.showMessage(this, "Please select a climber to restore",
                        "No Selection", JOptionPane.WARNING_MESSAGE);
            }
        });
        buttonPanel.add(restoreBtn);
        
        GhostButton refreshBtn = new GhostButton("Refresh");
        refreshBtn.addActionListener(e -> displayTable_archived());
        buttonPanel.add(refreshBtn);
        
        card.add(buttonPanel, BorderLayout.SOUTH);
        
        // Store reference for later use
        this.archivedTableModel = archiveTableModel;
        this.archiveTableRef = archiveTable;
        
        return card;
    }
    
    private DefaultTableModel archivedTableModel;
    private JTable archiveTableRef;

    /* ==================== CRUD OPERATIONS ==================== */
    
    // CREATE: Save new climber
    private void saveClimber() {
        String name = nameField.getText().trim();
        String email = emailField.getText().trim();
        String phone = phoneField.getText().trim();
        String experience = experienceBox.getSelectedItem().toString();
        String notes = notesArea.getText().trim();

        // Validation
        if (name.isEmpty()) {
            ThemedDialogs.showMessage(this, "Please enter the climber's name",
                    "Validation Error", JOptionPane.ERROR_MESSAGE);
            nameField.requestFocusInWindow();
            return;
        }
        if (email.isEmpty()) {
            ThemedDialogs.showMessage(this, "Please enter an email address",
                    "Validation Error", JOptionPane.ERROR_MESSAGE);
            emailField.requestFocusInWindow();
            return;
        }
        if (!isValidEmail(email)) {
            ThemedDialogs.showMessage(this, "Please enter a valid email address",
                    "Validation Error", JOptionPane.ERROR_MESSAGE);
            emailField.requestFocusInWindow();
            return;
        }

        try {
            if (isUpdateMode && currentClimberId > 0) {
                // UPDATE existing climber
                String updateSQL = "UPDATE climbers SET name=?, email=?, phone=?, experience_level=?, notes=? WHERE id=?";
                PreparedStatement pstmt = con.prepareStatement(updateSQL);
                pstmt.setString(1, name);
                pstmt.setString(2, email);
                pstmt.setString(3, phone);
                pstmt.setString(4, experience);
                pstmt.setString(5, notes);
                pstmt.setInt(6, currentClimberId);
                
                int rows = pstmt.executeUpdate();
                if (rows > 0) {
                    ThemedDialogs.showMessage(this, "Climber information updated successfully!",
                            "Update Successful", JOptionPane.INFORMATION_MESSAGE);
                    clearForm();
                    displayTable_climbers();
                    select("All Climbers");
                }
            } else {
                // CREATE new climber
                String insertSQL = "INSERT INTO climbers (name, email, phone, experience_level, notes) VALUES (?, ?, ?, ?, ?)";
                PreparedStatement pstmt = con.prepareStatement(insertSQL);
                pstmt.setString(1, name);
                pstmt.setString(2, email);
                pstmt.setString(3, phone);
                pstmt.setString(4, experience);
                pstmt.setString(5, notes);
                
                int rows = pstmt.executeUpdate();
                if (rows > 0) {
                    ThemedDialogs.showMessage(this, "New climber added successfully!",
                            "Creation Successful", JOptionPane.INFORMATION_MESSAGE);
                    clearForm();
                    displayTable_climbers();
                    select("All Climbers");
                }
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            ThemedDialogs.showMessage(this, "A climber with this email already exists.",
                    "Duplicate Entry", JOptionPane.ERROR_MESSAGE);
            emailField.requestFocusInWindow();
        } catch (SQLException e) {
            ThemedDialogs.showMessage(this, "Database error: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    // READ: Display all active climbers
    private void displayTable_climbers() {
        tableModel.setRowCount(0); // Clear existing data
        
        try {
            String query = "SELECT * FROM climbers ORDER BY id DESC";
            Statement stmt = con.createStatement();
            ResultSet rs = stmt.executeQuery(query);
            
            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("name");
                String email = rs.getString("email");
                String phone = rs.getString("phone");
                String experience = rs.getString("experience_level");
                
                tableModel.addRow(new Object[]{id, name, email, phone, experience, "Edit | Archive"});
            }
            rs.close();
            stmt.close();
        } catch (SQLException e) {
            System.err.println("Error loading climbers: " + e.getMessage());
        }
    }

    // Filter table based on search
    private void filterTable(String searchText) {
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(tableModel);
        climbersTable.setRowSorter(sorter);
        
        if (searchText.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + searchText, 1, 2)); // Search in name and email columns
        }
    }

    // UPDATE: Load climber data into form for editing
    private void editClimber(int id) {
        try {
            String query = "SELECT * FROM climbers WHERE id = ?";
            PreparedStatement pstmt = con.prepareStatement(query);
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                isUpdateMode = true;
                currentClimberId = id;
                
                nameField.setText(rs.getString("name"));
                emailField.setText(rs.getString("email"));
                phoneField.setText(rs.getString("phone"));
                experienceBox.setSelectedItem(rs.getString("experience_level"));
                notesArea.setText(rs.getString("notes") != null ? rs.getString("notes") : "");
                
                // Switch to Add New tab
                select("Add New");
                
                // Change button text to indicate update mode
                ThemedDialogs.showMessage(this, 
                        "Editing climber: " + rs.getString("name") + "\n\nMake your changes and click Save.",
                        "Edit Mode", JOptionPane.INFORMATION_MESSAGE);
            }
            rs.close();
            pstmt.close();
        } catch (SQLException e) {
            ThemedDialogs.showMessage(this, "Error loading climber data: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // DELETE (ARCHIVE): Soft delete climber
    private void archiveClimber(int id, String name) {
        int confirm = ThemedDialogs.showConfirm(this,
                "Are you sure you want to archive \"" + name + "\"?\n\n" +
                "This will move the record to the archive but won't permanently delete it.",
                "Confirm Archive");
        
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        
        try {
            con.setAutoCommit(false);
            
            // First, copy to archive table
            String archiveSQL = """
                INSERT INTO archived_climbers (original_id, name, email, phone, experience_level, notes)
                SELECT id, name, email, phone, experience_level, notes FROM climbers WHERE id = ?
                """;
            PreparedStatement archiveStmt = con.prepareStatement(archiveSQL);
            archiveStmt.setInt(1, id);
            archiveStmt.executeUpdate();
            archiveStmt.close();
            
            // Then delete from main table
            String deleteSQL = "DELETE FROM climbers WHERE id = ?";
            PreparedStatement deleteStmt = con.prepareStatement(deleteSQL);
            deleteStmt.setInt(1, id);
            deleteStmt.executeUpdate();
            deleteStmt.close();
            
            con.commit();
            con.setAutoCommit(true);
            
            ThemedDialogs.showMessage(this, "\"" + name + "\" has been archived successfully.",
                    "Archive Successful", JOptionPane.INFORMATION_MESSAGE);
            
            displayTable_climbers();
        } catch (SQLException e) {
            try { con.rollback(); con.setAutoCommit(true); } catch (SQLException ex) {}
            ThemedDialogs.showMessage(this, "Error archiving climber: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
        }
    }

    // Restore archived climber
    private void restoreClimber(DefaultTableModel model, int row) {
        try {
            int archiveId = (int) model.getValueAt(row, 0);
            int originalId = (int) model.getValueAt(row, 1);
            String name = model.getValueAt(row, 2).toString();
            
            int confirm = ThemedDialogs.showConfirm(this,
                    "Restore \"" + name + "\" to active climbers?",
                    "Confirm Restore");
            
            if (confirm != JOptionPane.YES_OPTION) return;
            
            con.setAutoCommit(false);
            
            // Insert back to main table
            String restoreSQL = """
                INSERT INTO climbers (name, email, phone, experience_level, notes)
                SELECT name, email, phone, experience_level, notes 
                FROM archived_climbers WHERE archive_id = ?
                """;
            PreparedStatement restoreStmt = con.prepareStatement(restoreSQL);
            restoreStmt.setInt(1, archiveId);
            restoreStmt.executeUpdate();
            restoreStmt.close();
            
            // Delete from archive
            String deleteSQL = "DELETE FROM archived_climbers WHERE archive_id = ?";
            PreparedStatement deleteStmt = con.prepareStatement(deleteSQL);
            deleteStmt.setInt(1, archiveId);
            deleteStmt.executeUpdate();
            deleteStmt.close();
            
            con.commit();
            con.setAutoCommit(true);
            
            ThemedDialogs.showMessage(this, "\"" + name + "\" has been restored.",
                    "Restore Successful", JOptionPane.INFORMATION_MESSAGE);
            
            displayTable_archived();
        } catch (SQLException e) {
            try { con.rollback(); con.setAutoCommit(true); } catch (SQLException ex) {}
            ThemedDialogs.showMessage(this, "Error restoring climber: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Display archived climbers
    private void displayTable_archived() {
        if (archivedTableModel == null) return;
        
        archivedTableModel.setRowCount(0);
        
        try {
            String query = "SELECT * FROM archived_climbers ORDER BY archived_date DESC";
            Statement stmt = con.createStatement();
            ResultSet rs = stmt.executeQuery(query);
            
            while (rs.next()) {
                int archiveId = rs.getInt("archive_id");
                int originalId = rs.getInt("original_id");
                String name = rs.getString("name");
                String email = rs.getString("email");
                String phone = rs.getString("phone");
                String experience = rs.getString("experience_level");
                Timestamp archivedDate = rs.getTimestamp("archived_date");
                
                archivedTableModel.addRow(new Object[]{
                    archiveId, originalId, name, email, phone, experience, 
                    archivedDate != null ? archivedDate.toString() : "N/A"
                });
            }
            rs.close();
            stmt.close();
        } catch (SQLException e) {
            System.err.println("Error loading archived climbers: " + e.getMessage());
        }
    }

    // Clear form fields
    private void clearForm() {
        isUpdateMode = false;
        currentClimberId = -1;
        nameField.setText("");
        emailField.setText("");
        phoneField.setText("");
        experienceBox.setSelectedIndex(0);
        notesArea.setText("");
        nameField.requestFocusInWindow();
    }

    // Email validation helper
    private boolean isValidEmail(String email) {
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        return email.matches(emailRegex);
    }

    /* ==================== CUSTOM TABLE CELL RENDERER & EDITOR ==================== */
    
    // Renderer for buttons in table
    static class ButtonRenderer extends JPanel implements TableCellRenderer {
        private final JButton editBtn = new JButton("Edit");
        private final JButton archiveBtn = new JButton("Archive");
        
        public ButtonRenderer() {
            setLayout(new FlowLayout(FlowLayout.CENTER, 5, 0));
            setOpaque(false);
            
            editBtn.setFont(UIKit.font(11f, Font.BOLD));
            editBtn.setForeground(UIKit.INK_ON_ACCENT);
            editBtn.setBackground(UIKit.GLOW_PEACH);
            editBtn.setFocusPainted(false);
            editBtn.setBorderPainted(false);
            editBtn.setPreferredSize(new Dimension(60, 28));
            
            archiveBtn.setFont(UIKit.font(11f, Font.BOLD));
            archiveBtn.setForeground(UIKit.INK_ON_ACCENT);
            archiveBtn.setBackground(UIKit.DANGER);
            archiveBtn.setFocusPainted(false);
            archiveBtn.setBorderPainted(false);
            archiveBtn.setPreferredSize(new Dimension(70, 28));
            
            add(editBtn);
            add(archiveBtn);
        }
        
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            return this;
        }
    }

    // Editor for buttons in table
    static class ButtonEditor extends DefaultCellEditor {
        private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        private final JButton editBtn = new JButton("Edit");
        private final JButton archiveBtn = new JButton("Archive");
        private JTable table;
        private boolean isArchiveTab;
        
        public ButtonEditor(JCheckBox checkBox, JTable table, boolean isArchiveTab) {
            super(checkBox);
            this.table = table;
            this.isArchiveTab = isArchiveTab;
            
            panel.setOpaque(false);
            
            editBtn.setFont(UIKit.font(11f, Font.BOLD));
            editBtn.setForeground(UIKit.INK_ON_ACCENT);
            editBtn.setBackground(UIKit.GLOW_PEACH);
            editBtn.setFocusPainted(false);
            editBtn.setBorderPainted(false);
            editBtn.setPreferredSize(new Dimension(60, 28));
            
            archiveBtn.setFont(UIKit.font(11f, Font.BOLD));
            archiveBtn.setForeground(UIKit.INK_ON_ACCENT);
            archiveBtn.setBackground(UIKit.DANGER);
            archiveBtn.setFocusPainted(false);
            archiveBtn.setBorderPainted(false);
            archiveBtn.setPreferredSize(new Dimension(70, 28));
            
            panel.add(editBtn);
            panel.add(archiveBtn);
        }
        
        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                boolean isSelected, int row, int column) {
            int id = (int) table.getValueAt(row, 0);
            String name = table.getValueAt(row, 1).toString();
            
            editBtn.addActionListener(e -> {
                fireEditingStopped();
                if (table instanceof JTable) {
                    ((ClimberDashboard) SwingUtilities.getWindowAncestor(panel))
                        .editClimber(id);
                }
            });
            
            archiveBtn.addActionListener(e -> {
                fireEditingStopped();
                if (table instanceof JTable) {
                    ((ClimberDashboard) SwingUtilities.getWindowAncestor(panel))
                        .archiveClimber(id, name);
                }
            });
            
            return panel;
        }
        
        @Override
        public Object getCellEditorValue() {
            return "";
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new ClimberDashboard().setVisible(true);
        });
    }
}
