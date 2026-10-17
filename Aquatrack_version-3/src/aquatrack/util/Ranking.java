package aquatrack.util;

import aquatrack.model.CustomerTotal;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ranks customers by what they have spent. Each order row is added to a HashMap keyed by
 * customer id (one fast lookup per row), then the totals are sorted from highest to lowest.
 */
public final class Ranking {

    private Ranking() {
    }

    /** Each row is {customer id (Integer), name, contact number, order total (BigDecimal)}. */
    public static List<CustomerTotal> topCustomers(List<Object[]> rows, int limit) {
        Map<Integer, CustomerTotal> byCustomer = new HashMap<>();
        for (Object[] row : rows) {
            int id = (Integer) row[0];
            CustomerTotal total = byCustomer.get(id);
            if (total == null) {
                total = new CustomerTotal(id, (String) row[1], (String) row[2]);
                byCustomer.put(id, total);
            }
            total.addOrder((BigDecimal) row[3]);
        }
        List<CustomerTotal> ranked = new ArrayList<>(byCustomer.values());
        ranked.sort(Comparator.comparing(CustomerTotal::getTotalSpent).reversed());
        return new ArrayList<>(ranked.subList(0, Math.min(limit, ranked.size())));
    }
}
