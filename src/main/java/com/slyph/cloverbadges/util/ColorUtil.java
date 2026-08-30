package com.slyph.cloverbadges.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtil {
    private static final Pattern SIMPLE_HEX = Pattern.compile("(?i)&([0-9a-f]{6})");
    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.builder()
            .character('&')
            .hexCharacter('#')
            .hexColors()
            .build();
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.builder()
            .character('§')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private ColorUtil() {
    }

    public static Component component(String text) {
        return AMPERSAND.deserialize(normalizeHex(text == null ? "" : text));
    }

    public static String legacySection(String text) {
        return SECTION.serialize(component(text));
    }

    public static String legacyAmpersand(Component component) {
        return AMPERSAND.serialize(component == null ? Component.empty() : component);
    }

    public static String toAmpersand(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (text.indexOf('§') < 0) {
            return text;
        }
        return legacyAmpersand(SECTION.deserialize(text));
    }

    public static String plain(String text) {
        return PLAIN.serialize(component(text));
    }

    public static String normalizeHex(String text) {
        Matcher matcher = SIMPLE_HEX.matcher(text);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(builder, Matcher.quoteReplacement("&#" + matcher.group(1)));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }
}
