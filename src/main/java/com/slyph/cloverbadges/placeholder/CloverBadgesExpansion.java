package com.slyph.cloverbadges.placeholder;

import com.slyph.cloverbadges.CloverBadges;
import com.slyph.cloverbadges.nicknamecolor.PlayerNicknameColorService;
import com.slyph.cloverbadges.player.PlayerBadgeService;
import com.slyph.cloverbadges.util.ColorUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class CloverBadgesExpansion extends PlaceholderExpansion {
    private final CloverBadges plugin;
    private final PlayerBadgeService service;
    private final PlayerNicknameColorService nicknameColorService;

    public CloverBadgesExpansion(
            CloverBadges plugin,
            PlayerBadgeService service,
            PlayerNicknameColorService nicknameColorService
    ) {
        this.plugin = plugin;
        this.service = service;
        this.nicknameColorService = nicknameColorService;
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
            case "badge", "badges" -> service.getActiveBadgeLegacy(player);
            case "badge_spaced", "badges_spaced" -> {
                String badges = service.getActiveBadgeLegacy(player);
                yield badges.isEmpty() ? empty : badges + " ";
            }
            case "badge_id" -> service.getActiveBadgeId(player).orElse(empty);
            case "badge_ids" -> String.join(",", service.getActiveBadgeIds(player));
            case "badge_name" -> service.getActiveBadgeId(player)
                    .map(service::getBadgeName)
                    .map(ColorUtil::legacySection)
                    .orElse(empty);
            case "badge_plain", "badges_plain" -> joinPlainBadges(player, empty);
            case "badge_name_plain" -> service.getActiveBadgeId(player)
                    .map(service::getBadgeName)
                    .map(ColorUtil::plain)
                    .orElse(empty);
            case "badge_1" -> badgeAt(player, 0).map(service::getBadgeText).map(ColorUtil::legacySection).orElse(empty);
            case "badge_2" -> badgeAt(player, 1).map(service::getBadgeText).map(ColorUtil::legacySection).orElse(empty);
            case "badge_1_id" -> badgeAt(player, 0).orElse(empty);
            case "badge_2_id" -> badgeAt(player, 1).orElse(empty);
            case "badge_1_name" -> badgeAt(player, 0).map(service::getBadgeName).map(ColorUtil::legacySection).orElse(empty);
            case "badge_2_name" -> badgeAt(player, 1).map(service::getBadgeName).map(ColorUtil::legacySection).orElse(empty);
            case "colored_nickname", "nickname_colored" -> nicknameColorService.coloredNicknameLegacy(player);
            case "nickname_color_id" -> nicknameColorService.selectedId(player).orElse(empty);
            case "nickname_color_name" -> nicknameColorService.selectedId(player)
                    .map(nicknameColorService::getColorName)
                    .map(ColorUtil::legacySection)
                    .orElse(empty);
            case "separator" -> ColorUtil.legacySection(plugin.getConfig().getString("display.separator", " "));
            case "newcomer" -> Boolean.toString(service.isNewcomer(player));
            case "newcomer_remaining" -> service.formatNewcomerRemaining(player);
            case "owned_count" -> Integer.toString(service.getOwnedBadgeIds(player).size());
            case "active_count" -> Integer.toString(service.getActiveBadgeIds(player).size());
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
        if (parameter.startsWith("priority_")) {
            String id = parameter.substring(9);
            if (service.getDefinition(id).isEmpty()) {
                return Optional.of(empty);
            }
            return Optional.of(Integer.toString(service.getBadgePriority(id)));
        }
        if (parameter.startsWith("name_")) {
            String id = parameter.substring(5);
            if (service.getDefinition(id).isEmpty()) {
                return Optional.of(empty);
            }
            return Optional.of(ColorUtil.legacySection(service.getBadgeName(id)));
        }
        if (parameter.startsWith("text_")) {
            String id = parameter.substring(5);
            if (service.getDefinition(id).isEmpty()) {
                return Optional.of(empty);
            }
            return Optional.of(ColorUtil.legacySection(service.getBadgeText(id)));
        }
        if (parameter.startsWith("hover_")) {
            String id = parameter.substring(6);
            return service.getDefinition(id)
                    .map(definition -> joinColoredLines(definition.hover()))
                    .or(() -> Optional.of(empty));
        }
        return Optional.empty();
    }

    private Optional<String> badgeAt(OfflinePlayer player, int index) {
        List<String> active = service.getActiveBadgeIds(player);
        if (index < 0 || index >= active.size()) {
            return Optional.empty();
        }
        return Optional.of(active.get(index));
    }

    private String joinPlainBadges(OfflinePlayer player, String empty) {
        List<String> active = service.getActiveBadgeIds(player);
        if (active.isEmpty()) {
            return empty;
        }
        String separator = ColorUtil.plain(plugin.getConfig().getString("display.separator", " "));
        return active.stream()
                .map(service::getBadgeText)
                .map(ColorUtil::plain)
                .reduce((left, right) -> left + separator + right)
                .orElse(empty);
    }

    private String joinColoredLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        return ColorUtil.legacySection(String.join("\n", lines));
    }
}
