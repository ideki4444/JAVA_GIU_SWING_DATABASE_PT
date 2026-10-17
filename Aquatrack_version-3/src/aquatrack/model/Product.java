package aquatrack.model;

import java.math.BigDecimal;

/** A product sold by the station: a refill, a new container, or a cap or seal. */
public final class Product {

    public static final String REFILL = "Refill";
    public static final String CONTAINER = "Container";
    public static final String CAP_SEAL = "Cap/Seal";

    private final int id;
    private final String name;
    private final String category;
    private final BigDecimal unitPrice;
    private final int stockQty;
    private final int reorderLevel;
    private final boolean active;

    public Product(int id, String name, String category, BigDecimal unitPrice,
                   int stockQty, int reorderLevel, boolean active) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.unitPrice = unitPrice;
        this.stockQty = stockQty;
        this.reorderLevel = reorderLevel;
        this.active = active;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getStockQty() {
        return stockQty;
    }

    public int getReorderLevel() {
        return reorderLevel;
    }

    public boolean isActive() {
        return active;
    }

    /** Refills are made on demand, so only containers and caps or seals have stock counts. */
    public boolean isStockTracked() {
        return !REFILL.equals(category);
    }

    /** True when an active, stock-tracked item has reached its reorder level. */
    public boolean isLowStock() {
        return active && isStockTracked() && stockQty <= reorderLevel;
    }

    @Override
    public String toString() {
        return name;
    }
}
