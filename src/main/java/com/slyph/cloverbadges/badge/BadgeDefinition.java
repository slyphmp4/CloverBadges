package com.slyph.cloverbadges.badge;

import java.util.List;

public record BadgeDefinition(
        String id,
        String name,
        String text,
        String permission,
        int priority,
        List<String> hover
) {
    public BadgeDefinition {
        hover = hover == null ? List.of() : List.copyOf(hover);
    }

    public boolean hasPermissionSource() {
        return permission != null && !permission.isBlank();
    }
}
