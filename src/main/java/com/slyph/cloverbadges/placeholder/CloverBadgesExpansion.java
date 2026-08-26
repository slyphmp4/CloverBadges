package com.slyph.cloverbadges.placeholder;

import com.slyph.cloverbadges.CloverBadges;
import com.slyph.cloverbadges.player.PlayerBadgeService;
import com.slyph.cloverbadges.util.ColorUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;

public final class CloverBadgesExpansion extends PlaceholderExpansion {
    private final CloverBadges plugin;
    private final PlayerBadgeService service;

    public CloverBadgesExpansion(CloverBadges plugin, PlayerBadgeService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "cloverbadges";
    }

    @Override
    public @NotNull String getAuthor() {
        return "slyph";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        String empty = plugin.getConfig().getString("placeholders.empty-value", "");
        if (player == null) {
            return empty;
        }

        String parameter = params.toLowerCase(Locale.ROOT);
        return switch (parameter) {
            case "badge" -> service.getActiveBadgeLegacy(player);
            case "badge_spaced" -> {
                String badge = service.getActiveBadgeLegacy(player);
                yield badge.isEmpty() ? empty : badge + " ";
            }
            case "badge_id" -> service.getActiveBadgeId(player).orElse(empty);
            case "badge_name" -> service.getActiveBadgeId(player)
                    .map(service::getBadgeName)
                    .map(ColorUtil::legacySection)
                    .orElse(empty);
            case "badge_plain" -> service.getActiveBadgeId(player)
                    .map(service::getBadgeText)
                    .map(ColorUtil::plain)
                    .orElse(empty);
            case "badge_name_plain" -> service.getActiveBadgeId(player)
                    .map(service::getBadgeName)
                    .map(ColorUtil::plain)
                    .orElse(empty);
            case "newcomer" -> Boolean.toString(service.isNewcomer(player));
            case "newcomer_remaining" -> service.formatNewcomerRemaining(player);
            case "owned_count" -> Integer.toString(service.getOwnedBadgeIds(player).size());
            default -> dynamic(player, parameter, empty).orElse(null);
        };
    }

    private Optional<String> dynamic(OfflinePlayer player, String parameter, String empty) {
        if (parameter.startsWith("has_")) {
            String id = parameter.substring(4);
            return Optional.of(Boolean.toString(service.hasBadge(player, id)));
        }
        if (parameter.startsWith("expires_")) {
            String id = parameter.substring(8);
            if (service.getDefinition(id).isEmpty()) {
                return Optional.of(empty);
            }
            return Optional.of(service.formatRemaining(player, id));
        }
        return Optional.empty();
    }
}
