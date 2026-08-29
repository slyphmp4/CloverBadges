package com.slyph.cloverbadges.gui;

public enum MenuPage {
    BADGES,
    NICKNAME_COLORS;

    public MenuPage opposite() {
        return this == BADGES ? NICKNAME_COLORS : BADGES;
    }
}
