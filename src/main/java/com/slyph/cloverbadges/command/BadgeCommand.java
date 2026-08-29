package com.slyph.cloverbadges.command;

import com.slyph.cloverbadges.CloverBadges;
import com.slyph.cloverbadges.gui.BadgeMenuManager;
import com.slyph.cloverbadges.message.MessageService;
import com.slyph.cloverbadges.player.PlayerBadgeService;
import com.slyph.cloverbadges.util.DurationParser;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;

public final class BadgeCommand implements CommandExecutor {
    private final CloverBadges plugin;
    private final PlayerBadgeService service;
    private final MessageService messages;
    private final BadgeMenuManager menuManager;

    public BadgeCommand(CloverBadges plugin, PlayerBadgeService service, MessageService messages, BadgeMenuManager menuManager) {
        this.plugin = plugin;
        this.service = service;
        this.messages = messages;
        this.menuManager = menuManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("cloverbadges.use")) {
            messages.send(sender, "no-permission");
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player player && sender.hasPermission("cloverbadges.menu")) {
                menuManager.open(player);
            } else {
                sendHelp(sender);
            }
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "give" -> give(sender, args);
            case "remove" -> remove(sender, args);
            case "reload" -> reload(sender);
            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }

    private boolean give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("cloverbadges.admin.give")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length < 3) {
            sendHelp(sender);
            return true;
        }

        OfflinePlayer target = findPlayer(args[1]);
        if (target == null) {
            messages.send(sender, "player-not-found", Map.of("player", args[1]));
            return true;
        }

        String id = args[2].toLowerCase();
        if (service.getDefinition(id).isEmpty()) {
            messages.send(sender, "badge-not-found", Map.of("badge", id));
            return true;
        }

        if (service.hasBadge(target, id)) {
            messages.send(sender, "badge-already-owned", Map.of(
                    "player", displayName(target),
                    "badge", service.getBadgeName(id)
            ));
            return true;
        }

        Optional<DurationParser.ParsedDuration> parsed = DurationParser.parse(args.length >= 4 ? args[3] : "permanent");
        if (parsed.isEmpty()) {
            messages.send(sender, "invalid-duration");
            return true;
        }

        if (!service.grant(target, id, parsed.get())) {
            messages.send(sender, "badge-limit-owned", Map.of(
                    "player", displayName(target),
                    "max", Integer.toString(service.maxOwnedBadges())
            ));
            return true;
        }

        String duration = parsed.get().permanent()
                ? plugin.getConfig().getString("placeholders.permanent-text", "навсегда")
                : DurationParser.format(parsed.get().millis());
        Map<String, String> replacements = Map.of(
                "player", displayName(target),
                "badge", service.getBadgeName(id),
                "duration", duration
        );
        messages.send(sender, "badge-given", replacements);
        Player online = target.getPlayer();
        if (online != null && !online.equals(sender)) {
            messages.send(online, "badge-received", replacements);
        }
        return true;
    }

    private boolean remove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("cloverbadges.admin.remove")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length < 3) {
            sendHelp(sender);
            return true;
        }

        OfflinePlayer target = findPlayer(args[1]);
        if (target == null) {
            messages.send(sender, "player-not-found", Map.of("player", args[1]));
            return true;
        }

        String id = args[2].toLowerCase();
        if (service.getDefinition(id).isEmpty()) {
            messages.send(sender, "badge-not-found", Map.of("badge", id));
            return true;
        }

        if (!service.hasBadge(target, id)) {
            messages.send(sender, "target-badge-not-owned", Map.of(
                    "player", displayName(target),
                    "badge", service.getBadgeName(id)
            ));
            return true;
        }

        if (!service.revoke(target, id)) {
            messages.send(sender, "target-badge-not-owned", Map.of(
                    "player", displayName(target),
                    "badge", service.getBadgeName(id)
            ));
            return true;
        }

        Map<String, String> replacements = Map.of(
                "player", displayName(target),
                "badge", service.getBadgeName(id)
        );
        messages.send(sender, "badge-remove-success", replacements);
        Player online = target.getPlayer();
        if (online != null && !online.equals(sender)) {
            messages.send(online, "badge-removed", replacements);
        }
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!sender.hasPermission("cloverbadges.admin.reload")) {
            messages.send(sender, "no-permission");
            return true;
        }
        plugin.reloadPlugin();
        messages.send(sender, "reload");
        return true;
    }

    private void sendHelp(CommandSender sender) {
        messages.send(sender, "help-header");
        if (sender.hasPermission("cloverbadges.admin.give")) {
            messages.send(sender, "help-give");
        }
        if (sender.hasPermission("cloverbadges.admin.remove")) {
            messages.send(sender, "help-remove");
        }
        if (sender.hasPermission("cloverbadges.admin.reload")) {
            messages.send(sender, "help-reload");
        }
        messages.send(sender, "help-footer");
    }

    private OfflinePlayer findPlayer(String input) {
        Player online = Bukkit.getPlayerExact(input);
        if (online != null) {
            return online;
        }
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            String name = offline.getName();
            if (name != null && name.equalsIgnoreCase(input)) {
                return offline;
            }
        }
        return null;
    }

    private String displayName(OfflinePlayer player) {
        String name = player.getName();
        return name == null ? player.getUniqueId().toString() : name;
    }
}
