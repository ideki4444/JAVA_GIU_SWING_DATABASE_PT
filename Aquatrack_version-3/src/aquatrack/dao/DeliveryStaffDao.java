package aquatrack.dao;

import aquatrack.db.Database;
import aquatrack.model.DeliveryStaff;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** SQL for the delivery_staff table (riders). */
public final class DeliveryStaffDao {

    public List<DeliveryStaff> findAll(boolean activeOnly) throws SQLException {
        String sql = "SELECT staff_id, full_name, contact_no, is_active FROM delivery_staff "
                + (activeOnly ? "WHERE is_active = 1 " : "") + "ORDER BY full_name";
        List<DeliveryStaff> riders = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                riders.add(new DeliveryStaff(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getBoolean(4)));
            }
        }
        return riders;
    }

    /** Adds a rider. The contact number may be null. */
    public void insert(String fullName, String contactNo) throws SQLException {
        Database.update("INSERT INTO delivery_staff (full_name, contact_no) VALUES (?, ?)", fullName, contactNo);
    }

    public void setActive(int staffId, boolean active) throws SQLException {
        Database.update("UPDATE delivery_staff SET is_active = ? WHERE staff_id = ?", active ? 1 : 0, staffId);
    }
}
