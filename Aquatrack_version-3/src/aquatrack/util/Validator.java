package aquatrack.util;

import java.math.BigDecimal;

/**
 * Input checks used by every form. Each method returns a cleaned value, or throws
 * IllegalArgumentException with a message that can be shown to the user as it is.
 */
public final class Validator {

    private static final BigDecimal MAX_MONEY = new BigDecimal("99999999.99");

    private Validator() {
    }

    public static String requireText(String value, String label, int maxLength) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        if (text.length() > maxLength) {
            throw new IllegalArgumentException(label + " must be " + maxLength + " characters or fewer.");
        }
        return text;
    }

    /** Philippine mobile number: 11 digits, starting with 09. */
    public static String requireMobile(String value, String label) {
        String text = value == null ? "" : value.trim();
        if (!text.matches("09\\d{9}")) {
            throw new IllegalArgumentException(label + " must be 11 digits and start with 09, for example 09171234567.");
        }
        return text;
    }

    public static int requirePositiveInt(String value, String label) {
        int number = parseInt(value, label);
        if (number <= 0) {
            throw new IllegalArgumentException(label + " must be more than zero.");
        }
        return number;
    }

    public static int requireNonNegativeInt(String value, String label) {
        int number = parseInt(value, label);
        if (number < 0) {
            throw new IllegalArgumentException(label + " cannot be negative.");
        }
        return number;
    }

    /** Money with at most two decimal places, never negative, returned with scale 2. */
    public static BigDecimal requireMoney(String value, String label) {
        BigDecimal amount;
        try {
            amount = new BigDecimal(value == null ? "" : value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " must be a number, for example 25.00.");
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException(label + " cannot be negative.");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException(label + " can have at most 2 decimal places.");
        }
        if (amount.compareTo(MAX_MONEY) > 0) {
            throw new IllegalArgumentException(label + " is too large.");
        }
        return amount.setScale(2);
    }

    private static int parseInt(String value, String label) {
        try {
            return Integer.parseInt(value == null ? "" : value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + " must be a whole number.");
        }
    }
}
