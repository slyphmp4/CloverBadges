package com.slyph.cloverbadges.api;

import net.kyori.adventure.text.Component;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface BadgeApi {
    List<String> getActiveBadgeIds(OfflinePlayer player);

    Optional<String> getActiveBadgeId(OfflinePlayer player);

    Component getActiveBadgeComponent(OfflinePlayer player);

    String getActiveBadgeLegacy(OfflinePlayer player);

    boolean hasBadge(OfflinePlayer player, String badgeId);

    Set<String> getOwnedBadgeIds(OfflinePlayer player);

    long getRemainingMillis(OfflinePlayer player, String badgeId);

    boolean isNewcomer(OfflinePlayer player);

    long getNewcomerRemainingMillis(OfflinePlayer player);
}
