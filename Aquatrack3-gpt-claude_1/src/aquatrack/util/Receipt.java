package aquatrack.util;

import aquatrack.model.CustomerOrder;
import aquatrack.model.OrderLine;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Captures compact roll receipts and a complete plain-text representation for legacy callers. */
public final class Receipt {

    /** Printed at the top of every receipt. Change this to your station's name. */
    public static final String STATION_NAME = "AQUATRACK WATER REFILLING";

    private static final int WIDTH = 42;
    private static final int ITEM_WIDTH = 22;
    private static final String RULE = repeat('-', WIDTH);

    private Receipt() {
    }

    /** Captures receipt values; preview and printing never reread or change the saved sale. */
    public static PrintDocument document(CustomerOrder order) {
        List<String> details = new ArrayList<>();
        details.add("Date: " + Fmt.dateTime(order.getDate()));
        details.add("Customer: " + shown(order.getCustomerName()));
        details.add("Contact: " + shown(order.getCustomerContact()));
        details.add("Address: " + shown(order.getCustomerAddress()));
        details.add("Order type: " + shown(order.getOrderType())
                + ("Delivery".equals(order.getOrderType()) ? " | " + shown(order.getStatus()) : ""));
        if ("Delivery".equals(order.getOrderType()) && order.getRiderName() != null) {
            details.add("Rider: " + shown(order.getRiderName()));
        }
        List<List<String>> rows = new ArrayList<>();
        for (OrderLine line : order.getLines()) {
            rows.add(Arrays.asList(line.getProductName(), Integer.toString(line.getQuantity()),
                    Fmt.peso(line.getUnitPrice()), Fmt.peso(line.getLineTotal())));
        }
        List<String> summary = Arrays.asList("TOTAL: " + Fmt.peso(order.getTotal()),
                "Payment: " + ("Credit".equals(order.getPaymentType()) ? "Charged to account" : "Cash"),
                "Containers borrowed: " + order.getGallonsBorrowed() + " | Returned: " + order.getGallonsReturned(),
                "Served by: " + shown(order.getServedBy()), "Thank you for your business!");
        return PrintDocument.receipt("Sales receipt #" + order.getId(), details, rows, summary);
    }

    private static String shown(String value) { return value == null || value.isBlank() ? "-" : value; }

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
        List<String> lines = wrap(shown, WIDTH - 13);
        return String.format(Locale.US, "%-11s: %s", label, lines.get(0))
                + (lines.size() == 1 ? "" : "\n" + repeat(' ', 13)
                        + String.join("\n" + repeat(' ', 13), lines.subList(1, lines.size())));
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
        for (String paragraph : shown(text).split("\\R", -1)) {
            String remaining = paragraph;
            if (remaining.isEmpty()) lines.add("");
            while (!remaining.isEmpty()) {
                int end = remaining.offsetByCodePoints(0, Math.min(width, remaining.codePointCount(0, remaining.length())));
                if (end < remaining.length()) {
                    int space = remaining.lastIndexOf(' ', end);
                    if (space > 0) end = space;
                }
                lines.add(remaining.substring(0, end).stripTrailing());
                remaining = remaining.substring(end).stripLeading();
            }
        }
        return lines;
    }

    private static String repeat(char c, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, c);
        return new String(chars);
    }
}
