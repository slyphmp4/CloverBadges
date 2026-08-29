package com.slyph.cloverbadges.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BadgeMenuHolder implements InventoryHolder {
    private final UUID playerId;
    private final Map<Integer, String> badgeSlots = new HashMap<>();
    private Inventory inventory;

    public BadgeMenuHolder(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID playerId() {
        return playerId;
    }

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public void clearBadgeSlots() {
        badgeSlots.clear();
    }

    public void badgeSlot(int slot, String badgeId) {
        badgeSlots.put(slot, badgeId);
    }

    public String badgeAt(int slot) {
        return badgeSlots.get(slot);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
