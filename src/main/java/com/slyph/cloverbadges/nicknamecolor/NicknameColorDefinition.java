package com.slyph.cloverbadges.nicknamecolor;

public record NicknameColorDefinition(
        String id,
        String name,
        String format,
        String permission,
        int priority
) {
    public boolean hasPermission() {
        return permission != null && !permission.isBlank();
    }
}
