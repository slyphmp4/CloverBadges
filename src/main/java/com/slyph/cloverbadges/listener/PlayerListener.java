package com.slyph.cloverbadges.listener;

import com.slyph.cloverbadges.nicknamecolor.PlayerNicknameColorService;
import com.slyph.cloverbadges.player.PlayerBadgeService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerListener implements Listener {
    private final PlayerBadgeService badgeService;
    private final PlayerNicknameColorService paintService;

    public PlayerListener(PlayerBadgeService badgeService, PlayerNicknameColorService paintService) {
        this.badgeService = badgeService;
        this.paintService = paintService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        badgeService.ensure(event.getPlayer());
        paintService.ensure(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        badgeService.saveAll();
        paintService.saveAll();
    }
}
