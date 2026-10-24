package aquatrack.model;

/** A delivery rider who can be assigned to delivery orders. */
public final class DeliveryStaff {

    private final int id;
    private final String fullName;
    private final String contactNo;
    private final boolean active;

    public DeliveryStaff(int id, String fullName, String contactNo, boolean active) {
        this.id = id;
        this.fullName = fullName;
        this.contactNo = contactNo;
        this.active = active;
    }

    public int getId() { return id; }
    public String getFullName() { return fullName; }
    public String getContactNo() { return contactNo; }
    public boolean isActive() { return active; }

    @Override
    public String toString() {
        return fullName;
    }
}
