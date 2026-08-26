package com.slyph.cloverbadges.player;

import com.slyph.cloverbadges.CloverBadges;
import com.slyph.cloverbadges.api.BadgeApi;
import com.slyph.cloverbadges.badge.BadgeDefinition;
import com.slyph.cloverbadges.badge.BadgeRegistry;
import com.slyph.cloverbadges.storage.PlayerDataStore;
import com.slyph.cloverbadges.util.ColorUtil;
import com.slyph.cloverbadges.util.DurationParser;
import net.kyori.adventure.text.Component;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerBadgeService implements BadgeApi {
    private final CloverBadges plugin;
    private final BadgeRegistry registry;
    private final PlayerDataStore dataStore;
    private final Map<UUID, PlayerBadgeData> data;

    public PlayerBadgeService(CloverBadges plugin, BadgeRegistry registry, PlayerDataStore dataStore) {
        this.plugin = plugin;
        this.registry = registry;
        this.dataStore = dataStore;
        this.data = new ConcurrentHashMap<>(dataStore.loadAll());
    }

    public PlayerBadgeData ensure(OfflinePlayer player) {
        PlayerBadgeData playerData = data.computeIfAbsent(player.getUniqueId(), uuid -> {
            long firstPlayed = player.getFirstPlayed();
            long firstSeen = firstPlayed > 0L ? firstPlayed : System.currentTimeMillis();
            return new PlayerBadgeData(uuid, firstSeen, null, false, Map.of());
        });

        if (playerData.firstSeen() <= 0L) {
            long firstPlayed = player.getFirstPlayed();
            playerData.firstSeen(firstPlayed > 0L ? firstPlayed : System.currentTimeMillis());
            saveIfConfigured();
        }
        return playerData;
    }

    public synchronized void grant(OfflinePlayer player, String badgeId, DurationParser.ParsedDuration duration) {
        String id = badgeId.toLowerCase();
        PlayerBadgeData playerData = ensure(player);
        long expiresAt = duration.permanent() ? 0L : System.currentTimeMillis() + duration.millis();
        playerData.grants().put(id, new BadgeGrant(expiresAt));

        if (plugin.getConfig().getBoolean("selection.auto-select-first-granted-badge", true)
                && playerData.selectedBadge() == null
                && !playerData.selectionDisabled()) {
            playerData.selectedBadge(id);
        }
        saveIfConfigured();
    }

    public synchronized boolean revoke(OfflinePlayer player, String badgeId) {
        String id = badgeId.toLowerCase();
        PlayerBadgeData playerData = ensure(player);
        boolean removed = playerData.grants().remove(id) != null;
        if (removed && id.equalsIgnoreCase(playerData.selectedBadge()) && !hasBadge(player, id)) {
            playerData.selectedBadge(null);
        }
        if (removed) {
            saveIfConfigured();
        }
        return removed;
    }

    public synchronized boolean select(OfflinePlayer player, String badgeId) {
        String id = badgeId.toLowerCase();
        if (!hasBadge(player, id)) {
            return false;
        }
        PlayerBadgeData playerData = ensure(player);
        playerData.selectedBadge(id);
        playerData.selectionDisabled(false);
        saveIfConfigured();
        return true;
    }

    public synchronized void clearSelection(OfflinePlayer player) {
        PlayerBadgeData playerData = ensure(player);
        playerData.selectedBadge(null);
        playerData.selectionDisabled(true);
        saveIfConfigured();
    }

    public synchronized void enableAutomaticSelection(OfflinePlayer player) {
        PlayerBadgeData playerData = ensure(player);
        playerData.selectionDisabled(false);
        saveIfConfigured();
    }

    @Override
    public Optional<String> getActiveBadgeId(OfflinePlayer player) {
        PlayerBadgeData playerData = ensure(player);
        if (playerData.selectionDisabled()) {
            return Optional.empty();
        }

        String selected = playerData.selectedBadge();
        if (selected != null && hasBadge(player, selected)) {
            return Optional.of(selected);
        }

        if (plugin.getConfig().getBoolean("newcomer.auto-display", true) && isNewcomer(player)) {
            String newcomerId = newcomerBadgeId();
            if (registry.contains(newcomerId)) {
                return Optional.of(newcomerId);
            }
        }
        return Optional.empty();
    }

    @Override
    public Component getActiveBadgeComponent(OfflinePlayer player) {
        return getActiveBadgeId(player)
                .flatMap(registry::get)
                .map(BadgeDefinition::text)
                .map(ColorUtil::component)
                .orElse(Component.empty());
    }

    @Override
    public String getActiveBadgeLegacy(OfflinePlayer player) {
        return getActiveBadgeId(player)
                .flatMap(registry::get)
                .map(BadgeDefinition::text)
                .map(ColorUtil::legacySection)
                .orElse(plugin.getConfig().getString("placeholders.empty-value", ""));
    }

    @Override
    public boolean hasBadge(OfflinePlayer player, String badgeId) {
        if (badgeId == null) {
            return false;
        }
        String id = badgeId.toLowerCase();
        Optional<BadgeDefinition> optionalDefinition = registry.get(id);
        if (optionalDefinition.isEmpty()) {
            return false;
        }

        if (id.equals(newcomerBadgeId()) && isNewcomer(player)) {
            return true;
        }

        BadgeDefinition definition = optionalDefinition.get();
        if (definition.hasPermissionSource()) {
            Player online = player.getPlayer();
            if (online != null && online.hasPermission(definition.permission())) {
                return true;
            }
        }

        BadgeGrant grant = ensure(player).grants().get(id);
        return grant != null && !grant.expired(System.currentTimeMillis());
    }

    @Override
    public Set<String> getOwnedBadgeIds(OfflinePlayer player) {
        Set<String> result = new LinkedHashSet<>();
        for (BadgeDefinition definition : registry.all()) {
            if (hasBadge(player, definition.id())) {
                result.add(definition.id());
            }
        }
        return Collections.unmodifiableSet(result);
    }

    @Override
    public long getRemainingMillis(OfflinePlayer player, String badgeId) {
        if (badgeId == null || registry.get(badgeId).isEmpty()) {
            return 0L;
        }
        String id = badgeId.toLowerCase();

        if (id.equals(newcomerBadgeId()) && isNewcomer(player)) {
            return getNewcomerRemainingMillis(player);
        }

        BadgeDefinition definition = registry.get(id).orElseThrow();
        if (definition.hasPermissionSource()) {
            Player online = player.getPlayer();
            if (online != null && online.hasPermission(definition.permission())) {
                return Long.MAX_VALUE;
            }
        }

        BadgeGrant grant = ensure(player).grants().get(id);
        if (grant == null || grant.expired(System.currentTimeMillis())) {
            return 0L;
        }
        return grant.remaining(System.currentTimeMillis());
    }

    @Override
    public boolean isNewcomer(OfflinePlayer player) {
        if (!plugin.getConfig().getBoolean("newcomer.enabled", true)) {
            return false;
        }
        long remaining = getNewcomerRemainingMillis(player);
        return remaining > 0L;
    }

    @Override
    public long getNewcomerRemainingMillis(OfflinePlayer player) {
        if (!plugin.getConfig().getBoolean("newcomer.enabled", true)) {
            return 0L;
        }
        DurationParser.ParsedDuration duration = DurationParser.parse(plugin.getConfig().getString("newcomer.duration", "7d"))
                .orElse(new DurationParser.ParsedDuration(false, 604_800_000L));
        if (duration.permanent()) {
            return Long.MAX_VALUE;
        }
        long firstSeen = ensure(player).firstSeen();
        return Math.max(0L, firstSeen + duration.millis() - System.currentTimeMillis());
    }

    public String getBadgeName(String badgeId) {
        return registry.get(badgeId).map(BadgeDefinition::name).orElse(badgeId);
    }

    public String getBadgeText(String badgeId) {
        return registry.get(badgeId).map(BadgeDefinition::text).orElse("");
    }

    public Optional<BadgeDefinition> getDefinition(String badgeId) {
        return registry.get(badgeId);
    }

    public Collection<String> allBadgeIds() {
        return registry.ids();
    }

    public String formatRemaining(OfflinePlayer player, String badgeId) {
        long remaining = getRemainingMillis(player, badgeId);
        if (remaining == Long.MAX_VALUE) {
            return plugin.getConfig().getString("placeholders.permanent-text", "навсегда");
        }
        if (remaining <= 0L) {
            return plugin.getConfig().getString("placeholders.expired-text", "0с");
        }
        return DurationParser.format(remaining);
    }

    public String formatNewcomerRemaining(OfflinePlayer player) {
        long remaining = getNewcomerRemainingMillis(player);
        if (remaining == Long.MAX_VALUE) {
            return plugin.getConfig().getString("placeholders.permanent-text", "навсегда");
        }
        if (remaining <= 0L) {
            return plugin.getConfig().getString("placeholders.expired-text", "0с");
        }
        return DurationParser.format(remaining);
    }

    public synchronized int cleanupExpired() {
        long now = System.currentTimeMillis();
        int removed = 0;
        boolean changed = false;

        for (PlayerBadgeData playerData : data.values()) {
            ArrayList<String> expired = new ArrayList<>();
            for (Map.Entry<String, BadgeGrant> entry : playerData.grants().entrySet()) {
                if (entry.getValue().expired(now)) {
                    expired.add(entry.getKey());
                }
            }
            for (String id : expired) {
                playerData.grants().remove(id);
                removed++;
                changed = true;
            }

            String selected = playerData.selectedBadge();
            if (selected != null
                    && plugin.getConfig().getBoolean("selection.clear-invalid-selection", true)
                    && !isOwnedWithoutEnsuring(playerData.uuid(), selected)) {
                playerData.selectedBadge(null);
                changed = true;
            }
        }

        if (changed) {
            saveAll();
        }
        return removed;
    }

    private boolean isOwnedWithoutEnsuring(UUID uuid, String badgeId) {
        PlayerBadgeData playerData = data.get(uuid);
        if (playerData == null || registry.get(badgeId).isEmpty()) {
            return false;
        }
        BadgeGrant grant = playerData.grants().get(badgeId.toLowerCase());
        if (grant != null && !grant.expired(System.currentTimeMillis())) {
            return true;
        }
        OfflinePlayer player = plugin.getServer().getOfflinePlayer(uuid);
        if (badgeId.equalsIgnoreCase(newcomerBadgeId()) && isNewcomer(player)) {
            return true;
        }
        BadgeDefinition definition = registry.get(badgeId).orElseThrow();
        Player online = player.getPlayer();
        return definition.hasPermissionSource() && online != null && online.hasPermission(definition.permission());
    }

    public void saveAll() {
        dataStore.saveAll(data.values());
    }

    private void saveIfConfigured() {
        if (plugin.getConfig().getBoolean("storage.save-on-change", true)) {
            saveAll();
        }
    }

    private String newcomerBadgeId() {
        return plugin.getConfig().getString("newcomer.badge-id", "newcomer").toLowerCase();
    }
}
