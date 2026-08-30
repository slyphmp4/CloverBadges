package com.slyph.cloverbadges.nicknamecolor;

import java.util.List;

public record NicknameColorDefinition(
        String id,
        String name,
        String format,
        List<String> gradient,
        String permission,
        int priority
) {
    public NicknameColorDefinition {
        gradient = gradient == null ? List.of() : List.copyOf(gradient);
    }

    public boolean hasPermission() {
        return permission != null && !permission.isBlank();
    }

    public boolean hasGradient() {
        return gradient.size() >= 2;
    }
}
