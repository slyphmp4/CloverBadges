package com.slyph.cloverbadges.util;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationParser {
    private static final Pattern PART = Pattern.compile("(\\d+)([smhdw])", Pattern.CASE_INSENSITIVE);

    private DurationParser() {
    }

    public static Optional<ParsedDuration> parse(String input) {
        if (input == null || input.isBlank() || input.equalsIgnoreCase("permanent") || input.equalsIgnoreCase("perm") || input.equalsIgnoreCase("forever")) {
            return Optional.of(new ParsedDuration(true, 0L));
        }

        String value = input.toLowerCase(Locale.ROOT).replace(" ", "");
        Matcher matcher = PART.matcher(value);
        long total = 0L;
        int end = 0;
        boolean found = false;

        while (matcher.find()) {
            if (matcher.start() != end) {
                return Optional.empty();
            }
            found = true;
            long amount;
            try {
                amount = Long.parseLong(matcher.group(1));
            } catch (NumberFormatException exception) {
                return Optional.empty();
            }
            long multiplier = switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
                case "s" -> 1000L;
                case "m" -> 60_000L;
                case "h" -> 3_600_000L;
                case "d" -> 86_400_000L;
                case "w" -> 604_800_000L;
                default -> 0L;
            };
            try {
                total = Math.addExact(total, Math.multiplyExact(amount, multiplier));
            } catch (ArithmeticException exception) {
                return Optional.empty();
            }
            end = matcher.end();
        }

        if (!found || end != value.length() || total <= 0L) {
            return Optional.empty();
        }
        return Optional.of(new ParsedDuration(false, total));
    }

    public static String format(long millis) {
        if (millis <= 0L) {
            return "0с";
        }

        long seconds = Math.max(1L, millis / 1000L);
        long weeks = seconds / 604800L;
        seconds %= 604800L;
        long days = seconds / 86400L;
        seconds %= 86400L;
        long hours = seconds / 3600L;
        seconds %= 3600L;
        long minutes = seconds / 60L;
        seconds %= 60L;

        StringBuilder result = new StringBuilder();
        append(result, weeks, "н");
        append(result, days, "д");
        append(result, hours, "ч");
        append(result, minutes, "м");
        append(result, seconds, "с");
        return result.toString().trim();
    }

    private static void append(StringBuilder builder, long value, String suffix) {
        if (value <= 0L) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(' ');
        }
        builder.append(value).append(suffix);
    }

    public record ParsedDuration(boolean permanent, long millis) {
    }
}
