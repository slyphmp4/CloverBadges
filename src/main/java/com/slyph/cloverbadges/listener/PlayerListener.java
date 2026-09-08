package com.slyph.cloverbadges.listener;

import com.slyph.cloverbadges.CloverBadges;
import com.slyph.cloverbadges.messagecolor.MessageColorDefinition;
import com.slyph.cloverbadges.messagecolor.PlayerMessageColorService;
import com.slyph.cloverbadges.nametag.NametagService;
import com.slyph.cloverbadges.nicknamecolor.PlayerNicknameColorService;
import com.slyph.cloverbadges.player.PlayerBadgeService;
import com.slyph.cloverbadges.util.DurationParser;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class PlayerListener implements Listener {
    private final CloverBadges plugin;
    private final PlayerBadgeService badgeService;
    private final PlayerNicknameColorService paintService;
    private final PlayerMessageColorService messageColorService;
    private final NametagService nametagService;

    public PlayerListener(
            CloverBadges plugin,
            PlayerBadgeService badgeService,
            PlayerNicknameColorService paintService,
            PlayerMessageColorService messageColorService,
            NametagService nametagService
    ) {
        this.plugin = plugin;
        this.badgeService = badgeService;
        this.paintService = paintService;
        this.messageColorService = messageColorService;
        this.nametagService = nametagService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean firstJoin = !player.hasPlayedBefore();

        badgeService.ensure(player);
        paintService.ensure(player);
        messageColorService.ensure(player);

        if (firstJoin) {
            grantFirstJoinMessageColor(player);
        }

        nametagService.handleJoin(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        nametagService.handleQuit(event.getPlayer());
        badgeService.saveAll();
        paintService.saveAll();
        messageColorService.saveAll();
    }

    private void grantFirstJoinMessageColor(Player player) {
        if (!plugin.getConfig().getBoolean("first-join-message-color.enabled", true)) {
            return;
        }

        List<String> configuredIds = plugin.getConfig().getStringList("first-join-message-color.colors");
        List<String> candidateIds = configuredIds.isEmpty()
                ? new ArrayList<>(messageColorService.allColorIds())
                : configuredIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(id -> id.toLowerCase(Locale.ROOT))
                .distinct()
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));

        candidateIds.removeIf(id -> {
            MessageColorDefinition definition = messageColorService.getDefinition(id).orElse(null);
            return definition == null
                    || !messageColorService.isAvailable(player, definition)
                    || messageColorService.hasColor(player, id);
        });

        if (candidateIds.isEmpty()) {
            return;
        }

        Collections.shuffle(candidateIds);

        String durationInput = plugin.getConfig().getString("first-join-message-color.duration", "permanent");
        DurationParser.ParsedDuration duration = DurationParser.parse(durationInput)
                .orElseGet(() -> {
                    plugin.getLogger().warning("Invalid first-join-message-color.duration: " + durationInput + ". Using permanent.");
                    return new DurationParser.ParsedDuration(true, 0L);
                });

        for (String colorId : candidateIds) {
            if (!messageColorService.grant(player, colorId, duration)) {
                continue;
            }
            if (plugin.getConfig().getBoolean("first-join-message-color.auto-select", false)) {
                messageColorService.select(player, colorId);
            }
            return;
        }
    }
}
