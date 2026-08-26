package com.slyph.cloverbadges.command;

import com.slyph.cloverbadges.player.PlayerBadgeService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class BadgeTabCompleter implements TabCompleter {
    private final PlayerBadgeService service;

    public BadgeTabCompleter(PlayerBadgeService service) {
        this.service = service;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("cloverbadges.use")) {
            return List.of();
        }

        if (args.length == 1) {
            List<String> values = new ArrayList<>();
            addIf(sender, values, "list", "cloverbadges.list");
            addIf(sender, values, "select", "cloverbadges.select");
            addIf(sender, values, "off", "cloverbadges.select");
            addIf(sender, values, "info", "cloverbadges.info");
            addIf(sender, values, "info", "cloverbadges.admin.info");
            addIf(sender, values, "give", "cloverbadges.admin.give");
            addIf(sender, values, "remove", "cloverbadges.admin.remove");
            addIf(sender, values, "set", "cloverbadges.admin.set");
            addIf(sender, values, "reload", "cloverbadges.admin.reload");
            return filter(values, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("select") && args.length == 2 && sender instanceof Player player && sender.hasPermission("cloverbadges.select")) {
            List<String> values = new ArrayList<>(service.getOwnedBadgeIds(player));
            values.add("none");
            values.add("off");
            return filter(values, args[1]);
        }

        if (sub.equals("info") && args.length == 2 && sender.hasPermission("cloverbadges.admin.info")) {
            return filter(onlineNames(), args[1]);
        }

        if ((sub.equals("give") && sender.hasPermission("cloverbadges.admin.give"))
                || (sub.equals("remove") && sender.hasPermission("cloverbadges.admin.remove"))
                || (sub.equals("set") && sender.hasPermission("cloverbadges.admin.set"))) {
            if (args.length == 2) {
                return filter(onlineNames(), args[1]);
            }
            if (args.length == 3) {
                List<String> values = new ArrayList<>(service.allBadgeIds());
                if (sub.equals("set")) {
                    values.add("none");
                    values.add("off");
                }
                return filter(values, args[2]);
            }
        }

        if (sub.equals("give") && args.length == 4 && sender.hasPermission("cloverbadges.admin.give")) {
            return filter(List.of("permanent", "30m", "1h", "1d", "7d", "30d"), args[3]);
        }

        return List.of();
    }

    private void addIf(CommandSender sender, List<String> target, String value, String permission) {
        if (sender.hasPermission(permission) && !target.contains(value)) {
            target.add(value);
        }
    }

    private List<String> onlineNames() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted().toList();
    }

    private List<String> filter(Collection<String> values, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix))
                .distinct()
                .sorted()
                .toList();
    }
}
