package com.slyph.cloverbadges.messagecolor;

public record MessageColorGrant(long expiresAt) {
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
