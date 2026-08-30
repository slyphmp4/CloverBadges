package com.slyph.cloverbadges.nicknamecolor;

public record NicknameColorGrant(long expiresAt) {
    public boolean permanent() {
        return expiresAt <= 0L;
    }

    public boolean expired(long now) {
        return !permanent() && expiresAt <= now;
    }

    public long remaining(long now) {
        if (permanent()) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, expiresAt - now);
    }
}
