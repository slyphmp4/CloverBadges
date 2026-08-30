package com.slyph.cloverbadges.nicknamecolor;

import com.slyph.cloverbadges.CloverBadges;
import com.slyph.cloverbadges.nicknamecolor.preview.NicknamePreviewService;
import com.slyph.cloverbadges.nicknamecolor.render.NicknameGradientRenderer;
import com.slyph.cloverbadges.nicknamecolor.storage.NicknameColorStore;
import com.slyph.cloverbadges.util.ColorUtil;
import com.slyph.cloverbadges.util.DurationParser;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerNicknameColorService {
    private final CloverBadges plugin;
    private final NicknameColorRegistry registry;
    private final NicknameColorStore store;
    private final NicknamePreviewService previewService;
    private final Map<UUID, String> selectedColors;
    private final Map<UUID, Map<String, NicknameColorGrant>> grants;
    private final Set<UUID> starterInitialized;

    public PlayerNicknameColorService(
            CloverBadges plugin,
            NicknameColorRegistry registry,
            NicknameColorStore store,
            NicknamePreviewService previewService
    ) {
        this.plugin = plugin;
        this.registry = registry;
        this.store = store;
        this.previewService = previewService;
        NicknameColorStore.Snapshot snapshot = store.loadAll();
        this.selectedColors = new ConcurrentHashMap<>(snapshot.selectedColors());
        this.grants = new ConcurrentHashMap<>();
        for (Map.Entry<UUID, Map<String, NicknameColorGrant>> entry : snapshot.grants().entrySet()) {
            this.grants.put(entry.getKey(), new ConcurrentHashMap<>(entry.getValue()));
        }
        this.starterInitialized = ConcurrentHashMap.newKeySet();
        this.starterInitialized.addAll(snapshot.starterInitialized());
        cleanupInvalidData();
    }

    public synchronized void reload() {
        registry.reload();
        cleanupInvalidData();
    }

    public synchronized void ensure(Player player) {
        UUID uuid = player.getUniqueId();
        boolean changed = cleanupExpired(uuid);

        if (starterInitialized.add(uuid)) {
            changed = true;
            if (registry.starterEnabled()) {
                String starterId = registry.starterId();
                Optional<NicknameColorDefinition> starter = registry.get(starterId);
                if (starter.isPresent() && !hasColorInternal(uuid, starterId, System.currentTimeMillis())) {
                    grants.computeIfAbsent(uuid, ignored -> new ConcurrentHashMap<>())
                            .put(starterId, new NicknameColorGrant(0L));
                    changed = true;
                }
                if (starter.isPresent() && registry.starterAutoSelect() && isAvailable(player, starter.get())) {
                    selectedColors.put(uuid, starterId);
                    changed = true;
                }
            }
        }

        String selected = selectedColors.get(uuid);
        if (selected != null && !hasColorInternal(uuid, selected, System.currentTimeMillis())) {
            selectedColors.remove(uuid);
            changed = true;
        }

        if (changed) {
            saveIfConfigured();
        }
    }

    public List<NicknameColorDefinition> allColors() {
        return registry.sorted();
    }

    public List<String> allColorIds() {
        return registry.allIds();
    }

    public Optional<NicknameColorDefinition> getDefinition(String id) {
        return registry.get(id);
    }

    public boolean isAvailable(Player player, NicknameColorDefinition definition) {
        return !definition.hasPermission() || player.hasPermission(definition.permission());
    }

    public synchronized boolean hasColor(OfflinePlayer player, String colorId) {
        String id = normalize(colorId);
        if (registry.get(id).isEmpty()) {
            return false;
        }
        boolean owned = hasColorInternal(player.getUniqueId(), id, System.currentTimeMillis());
        if (!owned && removeExpiredGrant(player.getUniqueId(), id)) {
            saveIfConfigured();
        }
        return owned;
    }

    public synchronized List<String> getOwnedColorIds(OfflinePlayer player) {
        UUID uuid = player.getUniqueId();
        if (cleanupExpired(uuid)) {
            saveIfConfigured();
        }
        Map<String, NicknameColorGrant> playerGrants = grants.get(uuid);
        if (playerGrants == null || playerGrants.isEmpty()) {
            return List.of();
        }
        return playerGrants.keySet().stream()
                .filter(id -> registry.get(id).isPresent())
                .sorted(Comparator
                        .comparingInt((String id) -> registry.get(id).map(NicknameColorDefinition::priority).orElse(0))
                        .reversed()
                        .thenComparing(id -> id))
                .toList();
    }

    public synchronized List<NicknameColorDefinition> getOwnedColors(OfflinePlayer player) {
        List<NicknameColorDefinition> result = new ArrayList<>();
        for (String id : getOwnedColorIds(player)) {
            registry.get(id).ifPresent(result::add);
        }
        return List.copyOf(result);
    }

    public synchronized Optional<String> selectedId(OfflinePlayer player) {
        UUID uuid = player.getUniqueId();
        if (cleanupExpired(uuid)) {
            saveIfConfigured();
        }
        String id = selectedColors.get(uuid);
        if (id == null || registry.get(id).isEmpty() || !hasColorInternal(uuid, id, System.currentTimeMillis())) {
            if (id != null) {
                selectedColors.remove(uuid);
                saveIfConfigured();
            }
            return Optional.empty();
        }
        return Optional.of(id);
    }

    public synchronized boolean select(Player player, String colorId) {
        String id = normalize(colorId);
        Optional<NicknameColorDefinition> definition = registry.get(id);
        if (definition.isEmpty() || !hasColorInternal(player.getUniqueId(), id, System.currentTimeMillis()) || !isAvailable(player, definition.get())) {
            return false;
        }
        selectedColors.put(player.getUniqueId(), id);
        saveIfConfigured();
        return true;
    }

    public synchronized void clear(OfflinePlayer player) {
        if (selectedColors.remove(player.getUniqueId()) != null) {
            saveIfConfigured();
        }
    }

    public synchronized boolean grant(OfflinePlayer player, String colorId, DurationParser.ParsedDuration duration) {
        String id = normalize(colorId);
        if (registry.get(id).isEmpty()) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        cleanupExpired(uuid);
        if (hasColorInternal(uuid, id, now)) {
            return false;
        }
        long expiresAt = 0L;
        if (!duration.permanent()) {
            try {
                expiresAt = Math.addExact(now, duration.millis());
            } catch (ArithmeticException exception) {
                expiresAt = Long.MAX_VALUE;
            }
        }
        grants.computeIfAbsent(uuid, ignored -> new ConcurrentHashMap<>())
                .put(id, new NicknameColorGrant(expiresAt));
        saveIfConfigured();
        return true;
    }

    public synchronized boolean revoke(OfflinePlayer player, String colorId) {
        String id = normalize(colorId);
        UUID uuid = player.getUniqueId();
        Map<String, NicknameColorGrant> playerGrants = grants.get(uuid);
        if (playerGrants == null || playerGrants.remove(id) == null) {
            return false;
        }
        if (playerGrants.isEmpty()) {
            grants.remove(uuid);
        }
        if (id.equals(selectedColors.get(uuid))) {
            selectedColors.remove(uuid);
        }
        saveIfConfigured();
        return true;
    }

    public String getColorName(String id) {
        return registry.get(id).map(NicknameColorDefinition::name).orElse(id);
    }

    public String preview(Player player, NicknameColorDefinition definition) {
        return previewService.render(player, renderNickname(player.getName(), definition));
    }

    public String nicknamePreview(Player player, NicknameColorDefinition definition) {
        return renderNickname(player.getName(), definition);
    }

    public String coloredNicknameLegacy(OfflinePlayer player) {
        String playerName = player.getName();
        if (playerName == null) {
            return "";
        }
        Optional<String> selected = selectedId(player);
        if (selected.isEmpty()) {
            return playerName;
        }
        NicknameColorDefinition definition = registry.get(selected.get()).orElse(null);
        if (definition == null) {
            return playerName;
        }
        return ColorUtil.legacySection(renderNickname(playerName, definition) + "&r");
    }

    public synchronized String formatRemaining(OfflinePlayer player, String colorId) {
        String id = normalize(colorId);
        Map<String, NicknameColorGrant> playerGrants = grants.get(player.getUniqueId());
        NicknameColorGrant grant = playerGrants == null ? null : playerGrants.get(id);
        if (grant == null) {
            return plugin.getConfig().getString("placeholders.expired-text", "0с");
        }
        if (grant.permanent()) {
            return plugin.getConfig().getString("placeholders.permanent-text", "навсегда");
        }
        long remaining = grant.remaining(System.currentTimeMillis());
        if (remaining <= 0L) {
            return plugin.getConfig().getString("placeholders.expired-text", "0с");
        }
        return DurationParser.format(remaining);
    }

    public synchronized void cleanupExpired() {
        boolean changed = false;
        for (UUID uuid : new HashSet<>(grants.keySet())) {
            changed |= cleanupExpired(uuid);
        }
        if (changed) {
            saveIfConfigured();
        }
    }

    public synchronized void saveAll() {
        store.saveAsync(snapshot());
    }

    public synchronized void flushStorage() {
        store.flushAndClose(snapshot());
    }

    private NicknameColorStore.Snapshot snapshot() {
        Map<UUID, Map<String, NicknameColorGrant>> grantSnapshot = new HashMap<>();
        for (Map.Entry<UUID, Map<String, NicknameColorGrant>> entry : grants.entrySet()) {
            grantSnapshot.put(entry.getKey(), Map.copyOf(entry.getValue()));
        }
        return new NicknameColorStore.Snapshot(
                Map.copyOf(selectedColors),
                Map.copyOf(grantSnapshot),
                Set.copyOf(starterInitialized)
        );
    }

    private String renderNickname(String playerName, NicknameColorDefinition definition) {
        if (definition.hasGradient()) {
            return NicknameGradientRenderer.render(playerName, definition.gradient());
        }
        return definition.format().replace("{player}", playerName);
    }

    private void cleanupInvalidData() {
        boolean changed = false;
        long now = System.currentTimeMillis();
        for (UUID uuid : new HashSet<>(grants.keySet())) {
            Map<String, NicknameColorGrant> playerGrants = grants.get(uuid);
            if (playerGrants == null) {
                continue;
            }
            changed |= playerGrants.entrySet().removeIf(entry -> registry.get(entry.getKey()).isEmpty() || entry.getValue().expired(now));
            if (playerGrants.isEmpty()) {
                grants.remove(uuid);
            }
        }
        changed |= selectedColors.entrySet().removeIf(entry -> registry.get(entry.getValue()).isEmpty()
                || !hasColorInternal(entry.getKey(), entry.getValue(), now));
        if (changed) {
            saveIfConfigured();
        }
    }

    private boolean cleanupExpired(UUID uuid) {
        Map<String, NicknameColorGrant> playerGrants = grants.get(uuid);
        if (playerGrants == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        Set<String> expired = new HashSet<>();
        for (Map.Entry<String, NicknameColorGrant> entry : playerGrants.entrySet()) {
            if (entry.getValue().expired(now)) {
                expired.add(entry.getKey());
            }
        }
        if (expired.isEmpty()) {
            return false;
        }
        expired.forEach(playerGrants::remove);
        String selected = selectedColors.get(uuid);
        if (selected != null && expired.contains(selected)) {
            selectedColors.remove(uuid);
        }
        if (playerGrants.isEmpty()) {
            grants.remove(uuid);
        }
        return true;
    }

    private boolean removeExpiredGrant(UUID uuid, String id) {
        Map<String, NicknameColorGrant> playerGrants = grants.get(uuid);
        if (playerGrants == null) {
            return false;
        }
        NicknameColorGrant grant = playerGrants.get(id);
        if (grant == null || !grant.expired(System.currentTimeMillis())) {
            return false;
        }
        playerGrants.remove(id);
        if (id.equals(selectedColors.get(uuid))) {
            selectedColors.remove(uuid);
        }
        if (playerGrants.isEmpty()) {
            grants.remove(uuid);
        }
        return true;
    }

    private boolean hasColorInternal(UUID uuid, String id, long now) {
        Map<String, NicknameColorGrant> playerGrants = grants.get(uuid);
        if (playerGrants == null) {
            return false;
        }
        NicknameColorGrant grant = playerGrants.get(id);
        return grant != null && !grant.expired(now);
    }

    private String normalize(String id) {
        return id == null ? "" : id.toLowerCase(Locale.ROOT);
    }

    private void saveIfConfigured() {
        if (plugin.getConfig().getBoolean("storage.save-on-change", true)) {
            saveAll();
        }
    }
}
