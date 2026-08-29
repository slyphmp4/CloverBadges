package com.slyph.cloverbadges.gui.action;

import com.slyph.cloverbadges.CloverBadges;
import com.slyph.cloverbadges.message.MessageService;
import com.slyph.cloverbadges.player.BadgeToggleResult;
import com.slyph.cloverbadges.player.PlayerBadgeService;
import com.slyph.cloverbadges.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;

public final class BadgeActionExecutor {
    private final CloverBadges plugin;
    private final PlayerBadgeService badgeService;
    private final MessageService messages;

    public BadgeActionExecutor(CloverBadges plugin, PlayerBadgeService badgeService, MessageService messages) {
        this.plugin = plugin;
        this.badgeService = badgeService;
        this.messages = messages;
    }

    public Result execute(Player player, String badgeId, List<String> actions, UnaryOperator<String> resolver) {
        boolean refresh = false;
        boolean close = false;

        for (String rawAction : actions) {
            String action = resolver.apply(rawAction == null ? "" : rawAction).trim();
            if (action.isEmpty()) {
                continue;
            }

            String normalized = action.toLowerCase(Locale.ROOT);
            if (normalized.equals("[toggle]")) {
                refresh |= toggle(player, badgeId);
                continue;
            }
            if (normalized.equals("[enable]")) {
                if (!badgeService.getActiveBadgeIds(player).contains(badgeId)) {
                    refresh |= toggle(player, badgeId);
                }
                continue;
            }
            if (normalized.equals("[disable]")) {
                if (badgeService.getActiveBadgeIds(player).contains(badgeId)) {
                    refresh |= toggle(player, badgeId);
                }
                continue;
            }
            if (normalized.equals("[close]")) {
                close = true;
                continue;
            }
            if (normalized.equals("[refresh]")) {
                refresh = true;
                continue;
            }
            if (startsWith(action, "[message]")) {
                player.sendMessage(ColorUtil.component(payload(action, "[message]")));
                continue;
            }
            if (startsWith(action, "[player]")) {
                String command = command(payload(action, "[player]"));
                if (!command.isEmpty()) {
                    player.performCommand(command);
                }
                continue;
            }
            if (startsWith(action, "[console]")) {
                String command = command(payload(action, "[console]"));
                if (!command.isEmpty()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                }
                continue;
            }

            plugin.getLogger().warning("Unknown badge GUI action for " + badgeId + ": " + action);
        }

        return new Result(refresh, close);
    }

    private boolean toggle(Player player, String badgeId) {
        BadgeToggleResult result = badgeService.toggleDisplay(player, badgeId);
        if (result == BadgeToggleResult.LIMIT_REACHED) {
            messages.send(player, "gui-max-active", Map.of(
                    "max", Integer.toString(badgeService.maxVisibleBadges())
            ));
        }
        return true;
    }

    private boolean startsWith(String value, String prefix) {
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private String payload(String action, String prefix) {
        return action.substring(prefix.length()).trim();
    }

    private String command(String value) {
        String command = value.trim();
        while (command.startsWith("/")) {
            command = command.substring(1);
        }
        return command;
    }

    public record Result(boolean refresh, boolean close) {
    }
}
