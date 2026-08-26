package com.slyph.cloverbadges.listener;

import com.slyph.cloverbadges.player.PlayerBadgeService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerListener implements Listener {
    private final PlayerBadgeService service;

    public PlayerListener(PlayerBadgeService service) {
        this.service = service;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        service.ensure(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        service.saveAll();
    }
}
