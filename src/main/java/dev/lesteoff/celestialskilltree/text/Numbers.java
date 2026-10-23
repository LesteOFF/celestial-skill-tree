package dev.lesteoff.celestialskilltree.text;

import dev.lesteoff.celestialskilltree.text.Part.Amount;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Numbers {
    private static final int DECIMALS = 2;
    private static final int PERCENT = 100;
    private static final int[] ROMAN_VALUES = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
    private static final String[] ROMAN_SYMBOLS = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};

    private Numbers() {
    }

    public static String format(Amount amount) {
        boolean percent = amount.style() == Amount.Style.PERCENT || amount.style() == Amount.Style.SIGNED_PERCENT;
        boolean signed = amount.style() == Amount.Style.SIGNED || amount.style() == Amount.Style.SIGNED_PERCENT;
        BigDecimal value = BigDecimal.valueOf(percent ? amount.value() * PERCENT : amount.value())
                .setScale(DECIMALS, RoundingMode.HALF_UP).stripTrailingZeros();
        String digits = value.abs().toPlainString();
        String sign = value.signum() < 0 ? "-" : signed ? "+" : "";
        return sign + digits + (percent ? "%" : "");
    }

    public static String roman(int number) {
        StringBuilder numeral = new StringBuilder();
        int remaining = number;
        for (int index = 0; index < ROMAN_VALUES.length; index++) {
            while (remaining >= ROMAN_VALUES[index]) {
                numeral.append(ROMAN_SYMBOLS[index]);
                remaining -= ROMAN_VALUES[index];
            }
        }
        return numeral.toString();
    }
}
