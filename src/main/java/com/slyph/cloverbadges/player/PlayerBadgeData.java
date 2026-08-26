package com.slyph.cloverbadges.player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerBadgeData {
    private final UUID uuid;
    private final Map<String, BadgeGrant> grants;
    private volatile long firstSeen;
    private volatile String selectedBadge;
    private volatile boolean selectionDisabled;

    public PlayerBadgeData(UUID uuid, long firstSeen, String selectedBadge, boolean selectionDisabled, Map<String, BadgeGrant> grants) {
        this.uuid = uuid;
        this.firstSeen = firstSeen;
        this.selectedBadge = selectedBadge;
        this.selectionDisabled = selectionDisabled;
        this.grants = new ConcurrentHashMap<>(grants);
    }

    public UUID uuid() {
        return uuid;
    }

    public long firstSeen() {
        return firstSeen;
    }

    public void firstSeen(long firstSeen) {
        this.firstSeen = firstSeen;
    }

    public String selectedBadge() {
        return selectedBadge;
    }

    public void selectedBadge(String selectedBadge) {
        this.selectedBadge = selectedBadge;
    }

    public boolean selectionDisabled() {
        return selectionDisabled;
    }

    public void selectionDisabled(boolean selectionDisabled) {
        this.selectionDisabled = selectionDisabled;
    }

    public Map<String, BadgeGrant> grants() {
        return grants;
    }
}
