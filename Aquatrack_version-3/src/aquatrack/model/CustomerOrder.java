package aquatrack.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** A sale: its header, its lines, and the gallons borrowed and returned with it. */
public final class CustomerOrder {

    private final int id;
    private final LocalDateTime date;
    private final String customerName;
    private final String customerAddress;
    private final String customerContact;
    private final String orderType;
    private final String paymentType;
    private final BigDecimal total;
    private final String status;
    private final String riderName;
    private final String servedBy;
    private String itemsSummary = "";
    private int gallonsBorrowed;
    private int gallonsReturned;
    private List<OrderLine> lines = new ArrayList<>();

    public CustomerOrder(int id, LocalDateTime date, String customerName, String customerAddress,
                         String customerContact, String orderType, String paymentType, BigDecimal total,
                         String status, String riderName, String servedBy) {
        this.id = id;
        this.date = date;
        this.customerName = customerName;
        this.customerAddress = customerAddress;
        this.customerContact = customerContact;
        this.orderType = orderType;
        this.paymentType = paymentType;
        this.total = total;
        this.status = status;
        this.riderName = riderName;
        this.servedBy = servedBy;
    }

    public int getId() { return id; }
    public LocalDateTime getDate() { return date; }
    public String getCustomerName() { return customerName; }
    public String getCustomerAddress() { return customerAddress; }
    public String getCustomerContact() { return customerContact; }
    public String getOrderType() { return orderType; }
    public String getPaymentType() { return paymentType; }
    public BigDecimal getTotal() { return total; }
    public String getStatus() { return status; }
    public String getRiderName() { return riderName; }
    public String getServedBy() { return servedBy; }
    public String getItemsSummary() { return itemsSummary; }
    public int getGallonsBorrowed() { return gallonsBorrowed; }
    public int getGallonsReturned() { return gallonsReturned; }
    public List<OrderLine> getLines() { return lines; }

    public void setItemsSummary(String itemsSummary) {
        this.itemsSummary = itemsSummary == null ? "" : itemsSummary;
    }

    public void setGallons(int borrowed, int returned) {
        this.gallonsBorrowed = borrowed;
        this.gallonsReturned = returned;
    }

    public void setLines(List<OrderLine> lines) {
        this.lines = lines;
    }
}
