package com.slyph.cloverbadges.badge;

import java.util.List;

public record BadgeDefinition(
        String id,
        String name,
        String text,
        String permission,
        int priority,
        List<String> hover,
        List<String> description,
        List<String> howToGet
) {
    public BadgeDefinition {
        hover = hover == null ? List.of() : List.copyOf(hover);
        description = description == null ? List.of() : List.copyOf(description);
        howToGet = howToGet == null ? List.of() : List.copyOf(howToGet);
    }

    public boolean hasPermissionSource() {
        return permission != null && !permission.isBlank();
    }
}
