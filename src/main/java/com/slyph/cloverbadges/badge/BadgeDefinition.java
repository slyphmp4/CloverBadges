package com.slyph.cloverbadges.badge;

public record BadgeDefinition(String id, String name, String text, String permission) {
    public boolean hasPermissionSource() {
        return permission != null && !permission.isBlank();
    }
}
