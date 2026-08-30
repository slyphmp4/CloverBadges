package com.slyph.cloverbadges.nicknamecolor.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class NicknameGradientRenderer {
    private NicknameGradientRenderer() {
    }

    public static String render(String text, List<String> colorStops) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        List<Rgb> colors = parseColors(colorStops);
        if (colors.size() < 2) {
            return text;
        }

        int[] codePoints = text.codePoints().toArray();
        StringBuilder result = new StringBuilder(text.length() * 9);
        for (int index = 0; index < codePoints.length; index++) {
            double progress = codePoints.length <= 1 ? 0.0D : (double) index / (double) (codePoints.length - 1);
            Rgb color = interpolate(colors, progress);
            result.append('&').append(color.hex()).appendCodePoint(codePoints[index]);
        }
        return result.toString();
    }

    private static List<Rgb> parseColors(List<String> colorStops) {
        if (colorStops == null || colorStops.isEmpty()) {
            return List.of();
        }
        List<Rgb> result = new ArrayList<>();
        for (String value : colorStops) {
            parseColor(value).ifPresent(result::add);
        }
        return List.copyOf(result);
    }

    private static java.util.Optional<Rgb> parseColor(String value) {
        if (value == null) {
            return java.util.Optional.empty();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("&#")) {
            normalized = normalized.substring(2);
        } else if (normalized.startsWith("#") || normalized.startsWith("&")) {
            normalized = normalized.substring(1);
        }
        if (!normalized.matches("[0-9A-F]{6}")) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new Rgb(
                Integer.parseInt(normalized.substring(0, 2), 16),
                Integer.parseInt(normalized.substring(2, 4), 16),
                Integer.parseInt(normalized.substring(4, 6), 16)
        ));
    }

    private static Rgb interpolate(List<Rgb> colors, double progress) {
        double scaled = Math.max(0.0D, Math.min(1.0D, progress)) * (colors.size() - 1);
        int segment = Math.min(colors.size() - 2, (int) Math.floor(scaled));
        double local = scaled - segment;
        Rgb start = colors.get(segment);
        Rgb end = colors.get(segment + 1);
        return new Rgb(
                lerp(start.red(), end.red(), local),
                lerp(start.green(), end.green(), local),
                lerp(start.blue(), end.blue(), local)
        );
    }

    private static int lerp(int start, int end, double progress) {
        return (int) Math.round(start + (end - start) * progress);
    }

    private record Rgb(int red, int green, int blue) {
        private String hex() {
            return String.format(Locale.ROOT, "%02X%02X%02X", red, green, blue);
        }
    }
}
