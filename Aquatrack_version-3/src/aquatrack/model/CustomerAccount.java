package aquatrack.model;

import java.math.BigDecimal;

/** A customer's open position: gallons still out and any unpaid credit balance. */
public final class CustomerAccount {

    private final int id;
    private final String fullName;
    private final String contactNo;
    private final int gallonsOut;
    private final BigDecimal unpaid;

    public CustomerAccount(int id, String fullName, String contactNo, int gallonsOut, BigDecimal unpaid) {
        this.id = id;
        this.fullName = fullName;
        this.contactNo = contactNo;
        this.gallonsOut = gallonsOut;
        this.unpaid = unpaid;
    }

    public int getId() { return id; }
    public String getFullName() { return fullName; }
    public String getContactNo() { return contactNo; }
    public int getGallonsOut() { return gallonsOut; }
    public BigDecimal getUnpaid() { return unpaid; }

    /** True when the customer still has gallons out or owes money. */
    public boolean hasOpenItems() {
        return gallonsOut > 0 || unpaid.signum() > 0;
    }
}
