package aquatrack.model;

import java.math.BigDecimal;

/** A customer's running total of orders, built up by the ranking code. */
public final class CustomerTotal {

    private final int id;
    private final String fullName;
    private final String contactNo;
    private int orderCount;
    private BigDecimal totalSpent = BigDecimal.ZERO;

    public CustomerTotal(int id, String fullName, String contactNo) {
        this.id = id;
        this.fullName = fullName;
        this.contactNo = contactNo;
    }

    public void addOrder(BigDecimal amount) {
        orderCount++;
        totalSpent = totalSpent.add(amount);
    }

    public int getId() { return id; }
    public String getFullName() { return fullName; }
    public String getContactNo() { return contactNo; }
    public int getOrderCount() { return orderCount; }
    public BigDecimal getTotalSpent() { return totalSpent; }
}
