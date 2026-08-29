package com.slyph.cloverbadges.player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerBadgeData {
    private final UUID uuid;
    private final Map<String, BadgeGrant> grants;
    private final Set<String> selectedBadges;
    private final Set<String> suppressedAutomaticBadges;
    private volatile long firstSeen;
    private volatile boolean selectionDisabled;

    public PlayerBadgeData(UUID uuid, long firstSeen, Set<String> selectedBadges, boolean selectionDisabled, Map<String, BadgeGrant> grants, Set<String> suppressedAutomaticBadges) {
        this.uuid = uuid;
        this.firstSeen = firstSeen;
        this.selectionDisabled = selectionDisabled;
        this.grants = new ConcurrentHashMap<>(grants);
        this.selectedBadges = ConcurrentHashMap.newKeySet();
        this.selectedBadges.addAll(selectedBadges);
        this.suppressedAutomaticBadges = ConcurrentHashMap.newKeySet();
        this.suppressedAutomaticBadges.addAll(suppressedAutomaticBadges);
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

    public Set<String> selectedBadges() {
        return selectedBadges;
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

    public Set<String> suppressedAutomaticBadges() {
        return suppressedAutomaticBadges;
    }
}
