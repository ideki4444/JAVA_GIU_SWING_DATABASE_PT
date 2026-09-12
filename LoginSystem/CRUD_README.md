# Twilight Peaks — Climber Management CRUD System

## Overview
A complete **CRUD (Create, Read, Update, Archive/Delete)** system for managing climber profiles, built with the existing "Twilight Peaks" design aesthetic from the LoginSystem folder.

## Features Implemented

### ✅ CREATE - Add New Data
- Navigate to **"Add New"** from the sidebar menu
- Fill in the form fields:
  - Full Name (required)
  - Email Address (required, validated)
  - Phone Number (optional)
  - Experience Level (dropdown: Beginner to Professional)
  - Additional Notes (optional text area)
- Click **"Save Climber"** to add the record
- Form validation ensures data integrity

### ✅ READ - View, Display & Search
- **"All Climbers"** tab displays all active climbers in a table
- Columns: ID, Name, Email, Phone, Experience, Actions
- **Search functionality**: Type in the search box to filter by name or email (real-time)
- Table styling matches the Twilight Peaks theme with frosted glass effects

### ✅ UPDATE - Modify Existing Data
- Click the **"Edit"** button in any row's Actions column
- System loads the climber's data into the form
- A confirmation dialog shows which climber is being edited
- Make changes and click **"Save Climber"** to update
- Form switches back to "All Climbers" view after successful update

### ✅ DELETE (ARCHIVE) - Soft Delete Instead of Permanent Removal
- Click the **"Archive"** button in any row's Actions column
- Confirmation dialog warns about archiving (not permanent deletion)
- Record is copied to `archived_climbers` table with timestamp
- Original record is removed from main `climbers` table
- Navigate to **"Archived"** tab to view all archived climbers
- **Restore functionality**: Select an archived climber and click "Restore Selected" to bring them back

## Database Schema

### Main Table: `climbers`
```sql
CREATE TABLE climbers (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    phone VARCHAR(20),
    experience_level VARCHAR(50),
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### Archive Table: `archived_climbers`
```sql
CREATE TABLE archived_climbers (
    archive_id INT AUTO_INCREMENT PRIMARY KEY,
    original_id INT NOT NULL,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    experience_level VARCHAR(50),
    notes TEXT,
    archived_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## Design Elements Used (from LoginSystem)

The CRUD system reuses all the beautiful UI components from the existing design:

- **ScenePanel**: Background with twilight mountain scene
- **GlassPanel**: Frosted glass cards with rounded corners
- **RoundedTextField**: Styled input fields with placeholders
- **NavButton**: Sidebar navigation with selection indicator
- **GhostButton**: Outlined buttons for secondary actions
- **RoundedButton**: Gradient pill buttons for primary actions
- **MoonLabel**: Crescent moon brand mark
- **ThemedDialogs**: Frosted-glass confirmation and message dialogs
- **Color Palette**: Deep indigo, violet, lavender mist, alpenglow pink, horizon peach

## How to Run

### Prerequisites
1. MySQL/MariaDB running (XAMPP recommended)
2. Java JDK 17+ installed
3. MySQL Connector/J in the `lib` folder

### Steps
1. Ensure your MySQL server is running
2. The database connection uses:
   - Host: `localhost:3306`
   - Database: `login_db` (same as login system)
   - User: `root`
   - Password: (blank)
3. Compile all sources:
   ```bash
   cd LoginSystem
   javac -d bin -cp "lib/*:src" src/*.java
   ```
4. Run the application:
   ```bash
   java -cp "bin;lib/*" ClimberDashboard
   ```
   Or on Linux/Mac:
   ```bash
   java -cp "bin:lib/*" ClimberDashboard
   ```

### First Launch
- On first run, the system automatically creates the `climbers` and `archived_climbers` tables
- No manual database setup required!

## Usage Workflow

1. **Login** → Start from `Loginform.java` to access the Dashboard
2. **Navigate** → From Dashboard, you can extend it to include a link to ClimberDashboard
3. **View All** → See all climbers in the main table
4. **Search** → Type in the search box to filter results
5. **Add New** → Click "Add New" in sidebar, fill form, save
6. **Edit** → Click "Edit" button on any row, modify, save
7. **Archive** → Click "Archive" button, confirm action
8. **Restore** → Go to "Archived" tab, select record, click "Restore Selected"
9. **Logout** → Click logout in sidebar to return to login screen

## Security Features

- **PreparedStatement**: All SQL queries use prepared statements to prevent SQL injection
- **Transaction Management**: Archive/restore operations use transactions with rollback on failure
- **Input Validation**: Email format validation, required field checks
- **Confirmation Dialogs**: All destructive actions require user confirmation

## Error Handling

- Duplicate email detection with user-friendly error messages
- Database connection errors displayed with themed dialogs
- Form validation prevents empty or invalid submissions
- Transaction rollback on archive/restore failures

---

**Built with ❤️ using the Twilight Peaks Design System**
