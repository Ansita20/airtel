package com.udyam.digital.engine;

/** Indian-style rupee formatting: 123456 -> "₹1,23,456". */
public final class Money {
    private Money() {}

    public static String inr(long amount) {
        return "₹" + group(amount);
    }

    public static String group(long amount) {
        String s = Long.toString(Math.abs(amount));
        if (s.length() > 3) {
            String last3 = s.substring(s.length() - 3);
            String rest = s.substring(0, s.length() - 3);
            StringBuilder b = new StringBuilder();
            int len = rest.length();
            for (int i = 0; i < len; i++) {
                b.append(rest.charAt(i));
                int remaining = len - 1 - i;
                if (remaining > 0 && remaining % 2 == 0) b.append(',');
            }
            s = b + "," + last3;
        }
        return (amount < 0 ? "-" : "") + s;
    }
}
