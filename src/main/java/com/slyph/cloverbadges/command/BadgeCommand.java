package com.slyph.cloverbadges.command;

import com.slyph.cloverbadges.CloverBadges;
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
import java.util.Set;

public final class BadgeCommand implements CommandExecutor {
    private final CloverBadges plugin;
    private final PlayerBadgeService service;
    private final MessageService messages;

    public BadgeCommand(CloverBadges plugin, PlayerBadgeService service, MessageService messages) {
        this.plugin = plugin;
        this.service = service;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("cloverbadges.use")) {
            messages.send(sender, "no-permission");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "list" -> list(sender);
            case "select" -> select(sender, args);
            case "off" -> off(sender);
            case "info" -> info(sender, args);
            case "give" -> give(sender, args);
            case "remove" -> remove(sender, args);
            case "set" -> set(sender, args);
            case "reload" -> reload(sender);
            default -> {
                sendHelp(sender);
                yield true;
            }
        };
    }

    private boolean list(CommandSender sender) {
        if (!sender.hasPermission("cloverbadges.list")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return true;
        }

        messages.send(sender, "list-header");
        Set<String> owned = service.getOwnedBadgeIds(player);
        Optional<String> active = service.getActiveBadgeId(player);
        if (owned.isEmpty()) {
            messages.send(sender, "list-empty");
        } else {
            for (String id : owned) {
                String selected = active.filter(id::equalsIgnoreCase).isPresent() ? messages.firstRaw("selected-mark") : "";
                messages.send(sender, "list-entry", Map.of(
                        "id", id,
                        "name", service.getBadgeName(id),
                        "remaining", service.formatRemaining(player, id),
                        "selected", selected
                ));
            }
        }
        messages.send(sender, "list-footer");
        return true;
    }

    private boolean select(CommandSender sender, String[] args) {
        if (!sender.hasPermission("cloverbadges.select")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return true;
        }
        if (args.length < 2) {
            sendHelp(sender);
            return true;
        }

        if (args[1].equalsIgnoreCase("none") || args[1].equalsIgnoreCase("off")) {
            service.clearSelection(player);
            messages.send(sender, "badge-cleared");
            return true;
        }

        String id = args[1].toLowerCase();
        if (service.getDefinition(id).isEmpty()) {
            messages.send(sender, "badge-not-found", Map.of("badge", id));
            return true;
        }
        if (!service.select(player, id)) {
            messages.send(sender, "badge-not-owned", Map.of("badge", id));
            return true;
        }

        messages.send(sender, "badge-selected", Map.of("badge", service.getBadgeName(id)));
        return true;
    }

    private boolean off(CommandSender sender) {
        if (!sender.hasPermission("cloverbadges.select")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return true;
        }

        service.clearSelection(player);
        messages.send(sender, "badge-cleared");
        return true;
    }

    private boolean info(CommandSender sender, String[] args) {
        OfflinePlayer target;
        if (args.length >= 2) {
            if (!sender.hasPermission("cloverbadges.admin.info")) {
                messages.send(sender, "no-permission");
                return true;
            }
            target = findPlayer(args[1]);
            if (target == null) {
                messages.send(sender, "player-not-found", Map.of("player", args[1]));
                return true;
            }
        } else {
            if (!sender.hasPermission("cloverbadges.info")) {
                messages.send(sender, "no-permission");
                return true;
            }
            if (!(sender instanceof Player player)) {
                messages.send(sender, "player-only");
                return true;
            }
            target = player;
        }

        String active = service.getActiveBadgeId(target)
                .map(service::getBadgeName)
                .orElse("&B8B8B8нет");
        messages.send(sender, "info", Map.of(
                "player", displayName(target),
                "active", active,
                "newcomer", service.isNewcomer(target) ? "да" : "нет",
                "newcomer_remaining", service.formatNewcomerRemaining(target)
        ));
        return true;
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

        Optional<DurationParser.ParsedDuration> parsed = DurationParser.parse(args.length >= 4 ? args[3] : "permanent");
        if (parsed.isEmpty()) {
            messages.send(sender, "invalid-duration");
            return true;
        }

        service.grant(target, id, parsed.get());
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
        if (!service.revoke(target, id)) {
            messages.send(sender, "target-badge-not-owned", Map.of(
                    "player", displayName(target),
                    "badge", id
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

    private boolean set(CommandSender sender, String[] args) {
        if (!sender.hasPermission("cloverbadges.admin.set")) {
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

        if (args[2].equalsIgnoreCase("none") || args[2].equalsIgnoreCase("off")) {
            service.clearSelection(target);
            messages.send(sender, "badge-set-none-other", Map.of("player", displayName(target)));
            return true;
        }

        String id = args[2].toLowerCase();
        if (service.getDefinition(id).isEmpty()) {
            messages.send(sender, "badge-not-found", Map.of("badge", id));
            return true;
        }
        if (!service.select(target, id)) {
            messages.send(sender, "target-badge-not-owned", Map.of(
                    "player", displayName(target),
                    "badge", id
            ));
            return true;
        }

        messages.send(sender, "badge-set-other", Map.of(
                "player", displayName(target),
                "badge", service.getBadgeName(id)
        ));
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
        if (sender.hasPermission("cloverbadges.list")) {
            messages.send(sender, "help-list");
        }
        if (sender.hasPermission("cloverbadges.select")) {
            messages.send(sender, "help-select");
            messages.send(sender, "help-off");
        }
        if (sender.hasPermission("cloverbadges.info")) {
            messages.send(sender, "help-info");
        }
        if (sender.hasPermission("cloverbadges.admin.info")) {
            messages.send(sender, "help-admin-info");
        }
        if (sender.hasPermission("cloverbadges.admin.give")) {
            messages.send(sender, "help-give");
        }
        if (sender.hasPermission("cloverbadges.admin.remove")) {
            messages.send(sender, "help-remove");
        }
        if (sender.hasPermission("cloverbadges.admin.set")) {
            messages.send(sender, "help-set");
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
