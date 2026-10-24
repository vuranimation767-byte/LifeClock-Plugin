package dev.lifeclock;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Times {
    private static final Pattern PART = Pattern.compile("(\\d+)(d|h|m|s)?");

    private Times() {}

    /**
     * Parses "45s", "90m", "2h30m", "1d" or plain seconds ("7200") into seconds.
     * Returns -1 if the input can't be parsed.
     */
    public static long parse(String in) {
        if (in == null || in.isBlank()) return -1;
        String s = in.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        Matcher m = PART.matcher(s);
        long total = 0;
        int pos = 0;
        while (pos < s.length()) {
            if (!m.find(pos) || m.start() != pos) return -1;
            long n = Long.parseLong(m.group(1));
            if (n > 1_000_000) return -1;
            total += switch (m.group(2) == null ? "" : m.group(2)) {
                case "d" -> n * 86400;
                case "h" -> n * 3600;
                case "m" -> n * 60;
                default -> n; // plain digits or an "s" suffix
            };
            pos = m.end();
        }
        return total;
    }

    /** "23h 59m 45s"; smaller units are used when the bigger ones are 0. */
    public static String format(long seconds) {
        long h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        if (h > 0) return h + "h " + m + "m " + s + "s";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    /** Clock style: "23:59:45" (BossBar title format). */
    public static String clock(long seconds) {
        long h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }
}
