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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
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
            return new PlayerBadgeData(uuid, firstSeen, Set.of(), false, Map.of(), Set.of());
        });

        if (playerData.firstSeen() <= 0L) {
            long firstPlayed = player.getFirstPlayed();
            playerData.firstSeen(firstPlayed > 0L ? firstPlayed : System.currentTimeMillis());
            saveIfConfigured();
        }
        return playerData;
    }

    public synchronized boolean grant(OfflinePlayer player, String badgeId, DurationParser.ParsedDuration duration) {
        String id = badgeId.toLowerCase();
        if (!hasBadge(player, id) && getOwnedBadgeIds(player).size() >= maxOwnedBadges()) {
            return false;
        }

        PlayerBadgeData playerData = ensure(player);
        long expiresAt = duration.permanent() ? 0L : System.currentTimeMillis() + duration.millis();
        playerData.grants().put(id, new BadgeGrant(expiresAt));
        playerData.suppressedAutomaticBadges().remove(id);
        saveIfConfigured();
        return true;
    }

    public synchronized boolean revoke(OfflinePlayer player, String badgeId) {
        String id = badgeId.toLowerCase();
        if (registry.get(id).isEmpty()) {
            return false;
        }

        boolean hadBadge = hasBadge(player, id);
        PlayerBadgeData playerData = ensure(player);
        boolean changed = playerData.grants().remove(id) != null;
        boolean hadManualSelection = !playerData.selectedBadges().isEmpty();

        if (id.equals(newcomerBadgeId()) && isNewcomerEligibleByTime(player)) {
            changed |= playerData.suppressedAutomaticBadges().add(id);
        }

        if (playerData.selectedBadges().remove(id)) {
            changed = true;
            if (hadManualSelection && playerData.selectedBadges().isEmpty()) {
                playerData.selectionDisabled(true);
            }
        }

        if (changed) {
            saveIfConfigured();
        }
        return hadBadge || changed;
    }

    public synchronized boolean select(OfflinePlayer player, String badgeId) {
        String id = badgeId.toLowerCase();
        if (!hasBadge(player, id)) {
            return false;
        }

        List<String> currentlyActive = new ArrayList<>(getActiveBadgeIds(player));
        PlayerBadgeData playerData = ensure(player);
        playerData.selectedBadges().clear();
        playerData.selectedBadges().add(id);
        for (String activeId : currentlyActive) {
            if (playerData.selectedBadges().size() >= maxVisibleBadges()) {
                break;
            }
            playerData.selectedBadges().add(activeId);
        }
        playerData.selectionDisabled(false);
        saveIfConfigured();
        return true;
    }

    public synchronized BadgeToggleResult toggleDisplay(OfflinePlayer player, String badgeId) {
        String id = badgeId.toLowerCase();
        if (!hasBadge(player, id)) {
            return BadgeToggleResult.NOT_OWNED;
        }

        PlayerBadgeData playerData = ensure(player);
        List<String> active = new ArrayList<>(getActiveBadgeIds(player));
        if (active.contains(id)) {
            if (playerData.selectedBadges().isEmpty() && !playerData.selectionDisabled()) {
                playerData.selectedBadges().addAll(active);
            }
            playerData.selectedBadges().remove(id);
            playerData.selectionDisabled(playerData.selectedBadges().isEmpty());
            saveIfConfigured();
            return BadgeToggleResult.DISABLED;
        }

        if (active.size() >= maxVisibleBadges()) {
            return BadgeToggleResult.LIMIT_REACHED;
        }

        if (playerData.selectionDisabled()) {
            playerData.selectedBadges().clear();
        } else if (playerData.selectedBadges().isEmpty()) {
            playerData.selectedBadges().addAll(active);
        }
        playerData.selectedBadges().add(id);
        playerData.selectionDisabled(false);
        saveIfConfigured();
        return BadgeToggleResult.ENABLED;
    }

    public synchronized void clearSelection(OfflinePlayer player) {
        PlayerBadgeData playerData = ensure(player);
        playerData.selectedBadges().clear();
        playerData.selectionDisabled(true);
        saveIfConfigured();
    }

    public synchronized void enableAutomaticSelection(OfflinePlayer player) {
        PlayerBadgeData playerData = ensure(player);
        playerData.selectedBadges().clear();
        playerData.selectionDisabled(false);
        saveIfConfigured();
    }

    @Override
    public List<String> getActiveBadgeIds(OfflinePlayer player) {
        PlayerBadgeData playerData = ensure(player);
        if (playerData.selectionDisabled()) {
            return List.of();
        }

        List<String> owned = getOwnedBadgeIds(player).stream()
                .sorted(badgeComparator())
                .toList();
        if (owned.isEmpty()) {
            return List.of();
        }

        int maxBadges = maxVisibleBadges();
        if (!playerData.selectedBadges().isEmpty()) {
            return playerData.selectedBadges().stream()
                    .map(String::toLowerCase)
                    .filter(owned::contains)
                    .sorted(badgeComparator())
                    .limit(maxBadges)
                    .toList();
        }

        return owned.stream()
                .filter(id -> isAutomaticDisplayEnabledForSource(player, id))
                .limit(maxBadges)
                .toList();
    }

    @Override
    public Optional<String> getActiveBadgeId(OfflinePlayer player) {
        List<String> active = getActiveBadgeIds(player);
        return active.isEmpty() ? Optional.empty() : Optional.of(active.getFirst());
    }

    @Override
    public Component getActiveBadgeComponent(OfflinePlayer player) {
        List<String> active = getActiveBadgeIds(player);
        if (active.isEmpty()) {
            return Component.empty();
        }

        Component separator = ColorUtil.component(plugin.getConfig().getString("display.separator", " "));
        Component result = Component.empty();
        boolean first = true;
        for (String id : active) {
            if (!first) {
                result = result.append(separator);
            }
            result = result.append(ColorUtil.component(getBadgeText(id)));
            first = false;
        }
        return result;
    }

    @Override
    public String getActiveBadgeLegacy(OfflinePlayer player) {
        List<String> active = getActiveBadgeIds(player);
        if (active.isEmpty()) {
            return plugin.getConfig().getString("placeholders.empty-value", "");
        }

        String separator = ColorUtil.legacySection(plugin.getConfig().getString("display.separator", " "));
        return active.stream()
                .map(this::getBadgeText)
                .map(ColorUtil::legacySection)
                .reduce((left, right) -> left + separator + right)
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

        BadgeGrant grant = ensure(player).grants().get(id);
        if (grant != null && !grant.expired(System.currentTimeMillis())) {
            return true;
        }

        BadgeDefinition definition = optionalDefinition.get();
        if (definition.hasPermissionSource()) {
            Player online = player.getPlayer();
            if (online != null && online.hasPermission(definition.permission())) {
                return true;
            }
        }

        return id.equals(newcomerBadgeId()) && isNewcomer(player);
    }

    @Override
    public Set<String> getOwnedBadgeIds(OfflinePlayer player) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        registry.all().stream()
                .filter(definition -> hasBadge(player, definition.id()))
                .sorted(Comparator.comparingInt(BadgeDefinition::priority).reversed().thenComparing(BadgeDefinition::id))
                .map(BadgeDefinition::id)
                .forEach(result::add);
        return Collections.unmodifiableSet(result);
    }

    @Override
    public long getRemainingMillis(OfflinePlayer player, String badgeId) {
        if (badgeId == null || registry.get(badgeId).isEmpty()) {
            return 0L;
        }
        String id = badgeId.toLowerCase();

        BadgeGrant grant = ensure(player).grants().get(id);
        if (grant != null && !grant.expired(System.currentTimeMillis())) {
            return grant.remaining(System.currentTimeMillis());
        }

        BadgeDefinition definition = registry.get(id).orElseThrow();
        if (definition.hasPermissionSource()) {
            Player online = player.getPlayer();
            if (online != null && online.hasPermission(definition.permission())) {
                return Long.MAX_VALUE;
            }
        }

        if (id.equals(newcomerBadgeId()) && isNewcomer(player)) {
            return getNewcomerRemainingMillis(player);
        }
        return 0L;
    }

    @Override
    public boolean isNewcomer(OfflinePlayer player) {
        if (!plugin.getConfig().getBoolean("newcomer.enabled", true)) {
            return false;
        }
        PlayerBadgeData playerData = ensure(player);
        if (playerData.suppressedAutomaticBadges().contains(newcomerBadgeId())) {
            return false;
        }
        return isNewcomerEligibleByTime(player);
    }

    @Override
    public long getNewcomerRemainingMillis(OfflinePlayer player) {
        if (!isNewcomer(player)) {
            return 0L;
        }
        return getNewcomerRawRemainingMillis(player);
    }

    public String getBadgeName(String badgeId) {
        return registry.get(badgeId).map(BadgeDefinition::name).orElse(badgeId);
    }

    public String getBadgeText(String badgeId) {
        return registry.get(badgeId).map(BadgeDefinition::text).orElse("");
    }

    public int getBadgePriority(String badgeId) {
        return registry.get(badgeId).map(BadgeDefinition::priority).orElse(0);
    }

    public Optional<BadgeDefinition> getDefinition(String badgeId) {
        return registry.get(badgeId);
    }

    public Collection<String> allBadgeIds() {
        return registry.all().stream()
                .sorted(Comparator.comparingInt(BadgeDefinition::priority).reversed().thenComparing(BadgeDefinition::id))
                .map(BadgeDefinition::id)
                .toList();
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

            if (plugin.getConfig().getBoolean("selection.clear-invalid-selection", true) && !playerData.selectedBadges().isEmpty()) {
                ArrayList<String> invalid = new ArrayList<>();
                for (String selected : playerData.selectedBadges()) {
                    if (!isOwnedWithoutEnsuring(playerData.uuid(), selected)) {
                        invalid.add(selected);
                    }
                }
                if (!invalid.isEmpty()) {
                    playerData.selectedBadges().removeAll(invalid);
                    if (playerData.selectedBadges().isEmpty()) {
                        playerData.selectionDisabled(true);
                    }
                    changed = true;
                }
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

        String id = badgeId.toLowerCase();
        BadgeGrant grant = playerData.grants().get(id);
        if (grant != null && !grant.expired(System.currentTimeMillis())) {
            return true;
        }

        OfflinePlayer player = plugin.getServer().getOfflinePlayer(uuid);
        BadgeDefinition definition = registry.get(id).orElseThrow();
        Player online = player.getPlayer();
        if (definition.hasPermissionSource() && online != null && online.hasPermission(definition.permission())) {
            return true;
        }

        return id.equals(newcomerBadgeId())
                && !playerData.suppressedAutomaticBadges().contains(id)
                && isNewcomerEligibleByTime(player);
    }

    private boolean isAutomaticDisplayEnabledForSource(OfflinePlayer player, String badgeId) {
        String id = badgeId.toLowerCase();
        if (!id.equals(newcomerBadgeId()) || plugin.getConfig().getBoolean("newcomer.auto-display", true)) {
            return true;
        }

        PlayerBadgeData playerData = ensure(player);
        BadgeGrant grant = playerData.grants().get(id);
        if (grant != null && !grant.expired(System.currentTimeMillis())) {
            return true;
        }

        BadgeDefinition definition = registry.get(id).orElse(null);
        Player online = player.getPlayer();
        return definition != null
                && definition.hasPermissionSource()
                && online != null
                && online.hasPermission(definition.permission());
    }

    private boolean isNewcomerEligibleByTime(OfflinePlayer player) {
        return plugin.getConfig().getBoolean("newcomer.enabled", true)
                && getNewcomerRawRemainingMillis(player) > 0L;
    }

    private long getNewcomerRawRemainingMillis(OfflinePlayer player) {
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

    private Comparator<String> badgeComparator() {
        return Comparator.comparingInt(this::getBadgePriority)
                .reversed()
                .thenComparing(String::compareTo);
    }

    public int maxVisibleBadges() {
        return Math.max(1, Math.min(2, plugin.getConfig().getInt("display.max-badges", 2)));
    }

    public int maxOwnedBadges() {
        return Math.max(1, Math.min(10, plugin.getConfig().getInt("limits.max-owned-badges", 10)));
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
