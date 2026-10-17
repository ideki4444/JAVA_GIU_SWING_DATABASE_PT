package aquatrack.util;

import aquatrack.model.CustomerOrder;
import aquatrack.model.OrderLine;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Builds the sales receipt as plain text, 42 characters wide, so it prints cleanly on A4 or a narrow printer. */
public final class Receipt {

    /** Printed at the top of every receipt. Change this to your station's name. */
    public static final String STATION_NAME = "AQUATRACK WATER REFILLING";

    private static final int WIDTH = 42;
    private static final int ITEM_WIDTH = 22;
    private static final String RULE = repeat('-', WIDTH);

    private Receipt() {
    }

    public static String format(CustomerOrder order) {
        StringBuilder text = new StringBuilder();
        text.append(center(STATION_NAME)).append('\n');
        text.append(center("Sales receipt")).append('\n');
        text.append(RULE).append('\n');
        text.append(pair("Order #", String.valueOf(order.getId()))).append('\n');
        text.append(pair("Date", Fmt.dateTime(order.getDate()))).append('\n');
        text.append(pair("Customer", order.getCustomerName())).append('\n');
        text.append(pair("Contact", order.getCustomerContact())).append('\n');
        text.append(pair("Address", order.getCustomerAddress())).append('\n');
        String type = order.getOrderType();
        if ("Delivery".equals(type)) {
            type = type + " (" + order.getStatus() + ")";
        }
        text.append(pair("Type", type)).append('\n');
        text.append(RULE).append('\n');
        text.append(line("Item", "Qty", "Amount")).append('\n');
        for (OrderLine item : order.getLines()) {
            List<String> name = wrap(item.getProductName(), ITEM_WIDTH);
            text.append(String.format(Locale.US, "%-22s %4d %14s", name.get(0), item.getQuantity(),
                    money(item.getLineTotal()))).append('\n');
            for (int i = 1; i < name.size(); i++) {
                text.append(name.get(i)).append('\n');
            }
        }
        text.append(RULE).append('\n');
        text.append(String.format(Locale.US, "%-28s%14s", "TOTAL", money(order.getTotal()))).append('\n');
        text.append(RULE).append('\n');
        text.append(pair("Payment", "Credit".equals(order.getPaymentType()) ? "Charged to account" : "Cash")).append('\n');
        text.append(pair("Containers", "Borrowed " + order.getGallonsBorrowed()
                + ", returned " + order.getGallonsReturned())).append('\n');
        text.append(pair("Served by", order.getServedBy())).append('\n');
        text.append(RULE).append('\n');
        text.append(center("Thank you for your business!")).append('\n');
        return text.toString();
    }

    private static String line(String item, String qty, String amount) {
        return String.format(Locale.US, "%-22s %4s %14s", item, qty, amount);
    }

    private static String pair(String label, String value) {
        String shown = value == null || value.isEmpty() ? "-" : value;
        return String.format(Locale.US, "%-11s: %s", label, truncate(shown, WIDTH - 13));
    }

    private static String money(BigDecimal amount) {
        return "PHP " + String.format(Locale.US, "%,.2f", amount);
    }

    private static String center(String text) {
        int pad = Math.max(0, (WIDTH - text.length()) / 2);
        return repeat(' ', pad) + text;
    }

    /** Splits text into lines of at most width characters, breaking at spaces where possible. */
    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            String piece = truncate(word, width);
            if (current.length() > 0 && current.length() + 1 + piece.length() > width) {
                lines.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append(' ');
            }
            current.append(piece);
        }
        lines.add(current.toString());
        return lines;
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }

    private static String repeat(char c, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, c);
        return new String(chars);
    }
}
