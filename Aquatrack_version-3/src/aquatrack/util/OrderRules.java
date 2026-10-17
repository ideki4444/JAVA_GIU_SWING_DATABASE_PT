package aquatrack.util;

import aquatrack.model.OrderLine;
import aquatrack.model.Product;
import java.math.BigDecimal;
import java.util.List;

/**
 * Sales rules that can be checked without a database. The DAO repeats the stock and gallon
 * checks inside the database transaction, so these are a friendly first check, not the only one.
 */
public final class OrderRules {

    private OrderRules() {
    }

    /** A message when the quantity cannot be sold, or null when it can. */
    public static String stockProblem(Product product, int alreadyInOrder, int wanted) {
        if (!product.isStockTracked()) {
            return null;
        }
        int needed = alreadyInOrder + wanted;
        if (needed > product.getStockQty()) {
            return "Only " + product.getStockQty() + " " + product.getName() + " in stock"
                    + (alreadyInOrder > 0 ? ", and " + alreadyInOrder + " are already in this order." : ".");
        }
        return null;
    }

    /** A message when the customer would return more gallons than they have out, or null when it is fine. */
    public static String gallonProblem(int outBefore, int borrowed, int returned) {
        if (returned > outBefore + borrowed) {
            return "This customer has " + outBefore + " gallon(s) out, so returning " + returned
                    + " is too many. Lower the returned count or record the borrowed gallons.";
        }
        return null;
    }

    public static BigDecimal total(List<OrderLine> lines) {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderLine line : lines) {
            total = total.add(line.getLineTotal());
        }
        return total.setScale(2);
    }
}
