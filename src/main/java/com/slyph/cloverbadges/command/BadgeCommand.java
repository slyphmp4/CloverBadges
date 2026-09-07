package com.slyph.cloverbadges.command;

import com.slyph.cloverbadges.CloverBadges;
import com.slyph.cloverbadges.gui.BadgeMenuManager;
import com.slyph.cloverbadges.message.MessageService;
import com.slyph.cloverbadges.messagecolor.PlayerMessageColorService;
import com.slyph.cloverbadges.nicknamecolor.PlayerNicknameColorService;
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
    private final PlayerBadgeService badgeService;
    private final PlayerNicknameColorService paintService;
    private final PlayerMessageColorService messageColorService;
    private final MessageService messages;
    private final BadgeMenuManager menuManager;

    public BadgeCommand(
            CloverBadges plugin,
            PlayerBadgeService badgeService,
            PlayerNicknameColorService paintService,
            PlayerMessageColorService messageColorService,
            MessageService messages,
            BadgeMenuManager menuManager
    ) {
        this.plugin = plugin;
        this.badgeService = badgeService;
        this.paintService = paintService;
        this.messageColorService = messageColorService;
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
        if (args.length < 4) {
            sendHelp(sender);
            return true;
        }

        OfflinePlayer target = findPlayer(args[1]);
        if (target == null) {
            messages.send(sender, "player-not-found", Map.of("player", args[1]));
            return true;
        }

        String category = normalizeCategory(args[2]);
        String id = args[3].toLowerCase();
        Optional<DurationParser.ParsedDuration> parsed = DurationParser.parse(args.length >= 5 ? args[4] : "permanent");
        if (parsed.isEmpty()) {
            messages.send(sender, "invalid-duration");
            return true;
        }

        return switch (category) {
            case "badge" -> giveBadge(sender, target, id, parsed.get());
            case "paint" -> givePaint(sender, target, id, parsed.get());
            case "messagepaint" -> giveMessagePaint(sender, target, id, parsed.get());
            default -> {
                messages.send(sender, "invalid-category");
                yield true;
            }
        };
    }

    private boolean giveBadge(CommandSender sender, OfflinePlayer target, String id, DurationParser.ParsedDuration duration) {
        if (badgeService.getDefinition(id).isEmpty()) {
            messages.send(sender, "badge-not-found", Map.of("badge", id));
            return true;
        }
        if (badgeService.hasBadge(target, id)) {
            messages.send(sender, "badge-already-owned", Map.of(
                    "player", displayName(target),
                    "badge", badgeService.getBadgeName(id)
            ));
            return true;
        }
        if (!badgeService.grant(target, id, duration)) {
            messages.send(sender, "badge-limit-owned", Map.of(
                    "player", displayName(target),
                    "max", Integer.toString(badgeService.maxOwnedBadges())
            ));
            return true;
        }

        String formattedDuration = formattedDuration(duration);
        Map<String, String> replacements = Map.of(
                "player", displayName(target),
                "badge", badgeService.getBadgeName(id),
                "duration", formattedDuration
        );
        messages.send(sender, "badge-given", replacements);
        Player online = target.getPlayer();
        if (online != null && !online.equals(sender)) {
            messages.send(online, "badge-received", replacements);
        }
        return true;
    }

    private boolean givePaint(CommandSender sender, OfflinePlayer target, String id, DurationParser.ParsedDuration duration) {
        if (paintService.getDefinition(id).isEmpty()) {
            messages.send(sender, "paint-not-found", Map.of("paint", id));
            return true;
        }
        if (paintService.hasColor(target, id)) {
            messages.send(sender, "paint-already-owned", Map.of(
                    "player", displayName(target),
                    "paint", paintService.getColorName(id)
            ));
            return true;
        }
        if (!paintService.grant(target, id, duration)) {
            messages.send(sender, "paint-already-owned", Map.of(
                    "player", displayName(target),
                    "paint", paintService.getColorName(id)
            ));
            return true;
        }

        String formattedDuration = formattedDuration(duration);
        Map<String, String> replacements = Map.of(
                "player", displayName(target),
                "paint", paintService.getColorName(id),
                "duration", formattedDuration
        );
        messages.send(sender, "paint-given", replacements);
        Player online = target.getPlayer();
        if (online != null && !online.equals(sender)) {
            messages.send(online, "paint-received", replacements);
        }
        return true;
    }

    private boolean giveMessagePaint(CommandSender sender, OfflinePlayer target, String id, DurationParser.ParsedDuration duration) {
        if (messageColorService.getDefinition(id).isEmpty()) {
            messages.send(sender, "message-paint-not-found", Map.of("message_paint", id));
            return true;
        }
        if (messageColorService.hasColor(target, id)) {
            messages.send(sender, "message-paint-already-owned", Map.of(
                    "player", displayName(target),
                    "message_paint", messageColorService.getColorName(id)
            ));
            return true;
        }
        if (!messageColorService.grant(target, id, duration)) {
            messages.send(sender, "message-paint-already-owned", Map.of(
                    "player", displayName(target),
                    "message_paint", messageColorService.getColorName(id)
            ));
            return true;
        }

        String formattedDuration = formattedDuration(duration);
        Map<String, String> replacements = Map.of(
                "player", displayName(target),
                "message_paint", messageColorService.getColorName(id),
                "duration", formattedDuration
        );
        messages.send(sender, "message-paint-given", replacements);
        Player online = target.getPlayer();
        if (online != null && !online.equals(sender)) {
            messages.send(online, "message-paint-received", replacements);
        }
        return true;
    }

    private boolean remove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("cloverbadges.admin.remove")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length < 4) {
            sendHelp(sender);
            return true;
        }

        OfflinePlayer target = findPlayer(args[1]);
        if (target == null) {
            messages.send(sender, "player-not-found", Map.of("player", args[1]));
            return true;
        }

        String category = normalizeCategory(args[2]);
        String id = args[3].toLowerCase();
        return switch (category) {
            case "badge" -> removeBadge(sender, target, id);
            case "paint" -> removePaint(sender, target, id);
            case "messagepaint" -> removeMessagePaint(sender, target, id);
            default -> {
                messages.send(sender, "invalid-category");
                yield true;
            }
        };
    }

    private boolean removeBadge(CommandSender sender, OfflinePlayer target, String id) {
        if (badgeService.getDefinition(id).isEmpty()) {
            messages.send(sender, "badge-not-found", Map.of("badge", id));
            return true;
        }
        if (!badgeService.hasBadge(target, id) || !badgeService.revoke(target, id)) {
            messages.send(sender, "target-badge-not-owned", Map.of(
                    "player", displayName(target),
                    "badge", badgeService.getBadgeName(id)
            ));
            return true;
        }

        Map<String, String> replacements = Map.of(
                "player", displayName(target),
                "badge", badgeService.getBadgeName(id)
        );
        messages.send(sender, "badge-remove-success", replacements);
        Player online = target.getPlayer();
        if (online != null && !online.equals(sender)) {
            messages.send(online, "badge-removed", replacements);
        }
        return true;
    }

    private boolean removePaint(CommandSender sender, OfflinePlayer target, String id) {
        if (paintService.getDefinition(id).isEmpty()) {
            messages.send(sender, "paint-not-found", Map.of("paint", id));
            return true;
        }
        if (!paintService.hasColor(target, id) || !paintService.revoke(target, id)) {
            messages.send(sender, "target-paint-not-owned", Map.of(
                    "player", displayName(target),
                    "paint", paintService.getColorName(id)
            ));
            return true;
        }

        Map<String, String> replacements = Map.of(
                "player", displayName(target),
                "paint", paintService.getColorName(id)
        );
        messages.send(sender, "paint-remove-success", replacements);
        Player online = target.getPlayer();
        if (online != null && !online.equals(sender)) {
            messages.send(online, "paint-removed", replacements);
        }
        return true;
    }

    private boolean removeMessagePaint(CommandSender sender, OfflinePlayer target, String id) {
        if (messageColorService.getDefinition(id).isEmpty()) {
            messages.send(sender, "message-paint-not-found", Map.of("message_paint", id));
            return true;
        }
        if (!messageColorService.hasColor(target, id) || !messageColorService.revoke(target, id)) {
            messages.send(sender, "target-message-paint-not-owned", Map.of(
                    "player", displayName(target),
                    "message_paint", messageColorService.getColorName(id)
            ));
            return true;
        }

        Map<String, String> replacements = Map.of(
                "player", displayName(target),
                "message_paint", messageColorService.getColorName(id)
        );
        messages.send(sender, "message-paint-remove-success", replacements);
        Player online = target.getPlayer();
        if (online != null && !online.equals(sender)) {
            messages.send(online, "message-paint-removed", replacements);
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

    private String formattedDuration(DurationParser.ParsedDuration duration) {
        return duration.permanent()
                ? plugin.getConfig().getString("placeholders.permanent-text", "навсегда")
                : DurationParser.format(duration.millis());
    }

    private String normalizeCategory(String input) {
        String category = input == null ? "" : input.toLowerCase().replace("_", "").replace("-", "");
        return switch (category) {
            case "messagepaint", "messagecolor", "chatpaint", "chatcolor" -> "messagepaint";
            default -> category;
        };
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
