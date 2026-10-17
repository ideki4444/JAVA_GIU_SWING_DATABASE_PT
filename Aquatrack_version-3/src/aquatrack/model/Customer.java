package aquatrack.model;

/** A customer record. Archived customers keep their history but are hidden from new orders. */
public final class Customer {

    private final int id;
    private final String fullName;
    private final String address;
    private final String contactNo;
    private final boolean archived;

    public Customer(int id, String fullName, String address, String contactNo) {
        this(id, fullName, address, contactNo, false);
    }

    public Customer(int id, String fullName, String address, String contactNo, boolean archived) {
        this.id = id;
        this.fullName = fullName;
        this.address = address;
        this.contactNo = contactNo;
        this.archived = archived;
    }

    public int getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getAddress() {
        return address;
    }

    public String getContactNo() {
        return contactNo;
    }

    public boolean isArchived() {
        return archived;
    }

    @Override
    public String toString() {
        return fullName + " (" + contactNo + ")";
    }
}
