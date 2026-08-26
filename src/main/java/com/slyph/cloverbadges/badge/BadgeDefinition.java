package com.slyph.cloverbadges.badge;

public record BadgeDefinition(String id, String name, String text, String permission, int priority) {
    public boolean hasPermissionSource() {
        return permission != null && !permission.isBlank();
    }
}
