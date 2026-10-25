package aquatrack.util;

/**
 * Delivery status rules: Pending, then Out for Delivery, then Delivered.
 * Each check returns a message explaining why the move is not allowed, or null when it is allowed.
 */
public final class DeliveryRules {

    public static final String PENDING = "Pending";
    public static final String OUT = "Out for Delivery";
    public static final String DELIVERED = "Delivered";

    private DeliveryRules() {
    }

    public static String canAssignRider(String status) {
        return DELIVERED.equals(status) ? "A delivered order cannot change riders." : null;
    }

    public static String canGoOut(String status, boolean hasRider) {
        if (!PENDING.equals(status)) {
            return OUT.equals(status) ? "This order is already out for delivery." : "Only pending orders can go out.";
        }
        return hasRider ? null : "Assign a rider before sending the order out.";
    }

    public static String canDeliver(String status) {
        return DELIVERED.equals(status) ? "This order is already delivered." : null;
    }
}
