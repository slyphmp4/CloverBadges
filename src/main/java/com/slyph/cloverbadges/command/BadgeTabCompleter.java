package com.slyph.cloverbadges.command;

import com.slyph.cloverbadges.nicknamecolor.PlayerNicknameColorService;
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
    private final PlayerBadgeService badgeService;
    private final PlayerNicknameColorService paintService;

    public BadgeTabCompleter(PlayerBadgeService badgeService, PlayerNicknameColorService paintService) {
        this.badgeService = badgeService;
        this.paintService = paintService;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("cloverbadges.use")) {
            return List.of();
        }

        if (args.length == 1) {
            List<String> values = new ArrayList<>();
            addIf(sender, values, "give", "cloverbadges.admin.give");
            addIf(sender, values, "remove", "cloverbadges.admin.remove");
            addIf(sender, values, "reload", "cloverbadges.admin.reload");
            return filter(values, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        boolean give = sub.equals("give") && sender.hasPermission("cloverbadges.admin.give");
        boolean remove = sub.equals("remove") && sender.hasPermission("cloverbadges.admin.remove");
        if (!give && !remove) {
            return List.of();
        }

        if (args.length == 2) {
            return filter(onlineNames(), args[1]);
        }
        if (args.length == 3) {
            return filter(List.of("badge", "paint"), args[2]);
        }
        if (args.length == 4) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                return List.of();
            }
            String category = args[2].toLowerCase(Locale.ROOT);
            if (category.equals("badge")) {
                if (give) {
                    return filter(badgeService.allBadgeIds().stream()
                            .filter(id -> !badgeService.hasBadge(target, id))
                            .toList(), args[3]);
                }
                return filter(badgeService.getOwnedBadgeIds(target), args[3]);
            }
            if (category.equals("paint")) {
                if (give) {
                    return filter(paintService.allColorIds().stream()
                            .filter(id -> !paintService.hasColor(target, id))
                            .toList(), args[3]);
                }
                return filter(paintService.getOwnedColorIds(target), args[3]);
            }
        }

        if (give && args.length == 5) {
            return filter(List.of("permanent", "30m", "1h", "1d", "7d", "30d"), args[4]);
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
