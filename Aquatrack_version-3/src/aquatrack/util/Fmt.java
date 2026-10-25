package aquatrack.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Display formats for money and dates, so every screen shows them the same way. */
public final class Fmt {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.ENGLISH);

    private Fmt() {
    }

    /** Philippine peso, for example P1,250.00 with the peso sign. */
    public static String peso(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        return String.format(Locale.US, "\u20B1%,.2f", value);
    }

    public static String date(LocalDate day) {
        return day == null ? "" : day.format(DATE);
    }

    public static String dateTime(LocalDateTime when) {
        return when == null ? "" : when.format(DATE_TIME);
    }
}
