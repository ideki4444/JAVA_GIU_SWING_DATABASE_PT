package aquatrack.model;

import java.math.BigDecimal;

/** One line of a sale: a product, a quantity, the unit price, and the line total. */
public final class OrderLine {

    private final int productId;
    private final String productName;
    private final int quantity;
    private final BigDecimal unitPrice;
    private final BigDecimal lineTotal;

    public OrderLine(int productId, String productName, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.lineTotal = lineTotal;
    }

    /** A new line priced at the product's current price. */
    public static OrderLine priced(int productId, String productName, int quantity, BigDecimal unitPrice) {
        BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2);
        return new OrderLine(productId, productName, quantity, unitPrice, lineTotal);
    }

    public int getProductId() { return productId; }
    public String getProductName() { return productName; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getLineTotal() { return lineTotal; }
}
