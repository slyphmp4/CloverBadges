package com.slyph.cloverbadges.listener;

import com.slyph.cloverbadges.messagecolor.PlayerMessageColorService;
import com.slyph.cloverbadges.nametag.NametagService;
import com.slyph.cloverbadges.nicknamecolor.PlayerNicknameColorService;
import com.slyph.cloverbadges.player.PlayerBadgeService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerListener implements Listener {
    private final PlayerBadgeService badgeService;
    private final PlayerNicknameColorService paintService;
    private final PlayerMessageColorService messageColorService;
    private final NametagService nametagService;

    public PlayerListener(
            PlayerBadgeService badgeService,
            PlayerNicknameColorService paintService,
            PlayerMessageColorService messageColorService,
            NametagService nametagService
    ) {
        this.badgeService = badgeService;
        this.paintService = paintService;
        this.messageColorService = messageColorService;
        this.nametagService = nametagService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        badgeService.ensure(event.getPlayer());
        paintService.ensure(event.getPlayer());
        messageColorService.ensure(event.getPlayer());
        nametagService.handleJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        nametagService.handleQuit(event.getPlayer());
        badgeService.saveAll();
        paintService.saveAll();
        messageColorService.saveAll();
    }
}
