package com.slyph.cloverbadges.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BadgeMenuListener implements Listener {
    private static final long NAVIGATION_COOLDOWN_NANOS = 150_000_000L;
    private final BadgeMenuManager menuManager;
    private final Map<UUID, Long> navigationCooldowns = new HashMap<>();

    public BadgeMenuListener(BadgeMenuManager menuManager) {
        this.menuManager = menuManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory eventInventory = event.getView().getTopInventory();
        if (!(eventInventory.getHolder() instanceof BadgeMenuHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!holder.playerId().equals(player.getUniqueId())) {
            return;
        }
        if (player.getOpenInventory().getTopInventory() != eventInventory) {
            return;
        }

        UUID playerId = player.getUniqueId();
        long now = System.nanoTime();
        Long cooldownUntil = navigationCooldowns.get(playerId);
        if (cooldownUntil != null) {
            if (now < cooldownUntil) {
                return;
            }
            navigationCooldowns.remove(playerId);
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= eventInventory.getSize()) {
            return;
        }

        int nicknameColorPageBefore = holder.nicknameColorPage();
        menuManager.handleClick(player, holder, rawSlot, event.getClick());

        Inventory currentInventory = player.getOpenInventory().getTopInventory();
        boolean switchedMenuPage = currentInventory != eventInventory
                && currentInventory.getHolder() instanceof BadgeMenuHolder currentHolder
                && currentHolder.playerId().equals(playerId);
        boolean switchedNicknameColorPage = currentInventory == eventInventory
                && holder.nicknameColorPage() != nicknameColorPageBefore;

        if (switchedMenuPage || switchedNicknameColorPage) {
            navigationCooldowns.put(playerId, System.nanoTime() + NAVIGATION_COOLDOWN_NANOS);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BadgeMenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        navigationCooldowns.remove(event.getPlayer().getUniqueId());
    }
}
