package com.slyph.cloverbadges.gui;

public enum MenuPage {
    BADGES,
    NICKNAME_COLORS,
    MESSAGE_COLORS;

    public MenuPage nicknameTarget() {
        return this == NICKNAME_COLORS ? BADGES : NICKNAME_COLORS;
    }

    public MenuPage messageTarget() {
        return this == MESSAGE_COLORS ? BADGES : MESSAGE_COLORS;
    }
}
