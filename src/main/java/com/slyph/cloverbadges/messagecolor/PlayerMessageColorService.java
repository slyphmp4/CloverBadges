package com.slyph.cloverbadges.messagecolor;

import com.slyph.cloverbadges.CloverBadges;
import com.slyph.cloverbadges.messagecolor.render.MessageGradientRenderer;
import com.slyph.cloverbadges.messagecolor.storage.MessageColorStore;
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

public final class PlayerMessageColorService {
    private final CloverBadges plugin;
    private final MessageColorRegistry registry;
    private final MessageColorStore store;
    private final Map<UUID, String> selectedColors;
    private final Map<UUID, Map<String, MessageColorGrant>> grants;

    public PlayerMessageColorService(CloverBadges plugin, MessageColorRegistry registry, MessageColorStore store) {
        this.plugin = plugin;
        this.registry = registry;
        this.store = store;
        MessageColorStore.Snapshot snapshot = store.loadAll();
        this.selectedColors = new ConcurrentHashMap<>(snapshot.selectedColors());
        this.grants = new ConcurrentHashMap<>();
        for (Map.Entry<UUID, Map<String, MessageColorGrant>> entry : snapshot.grants().entrySet()) {
            this.grants.put(entry.getKey(), new ConcurrentHashMap<>(entry.getValue()));
        }
        cleanupInvalidData();
    }

    public synchronized void reload() {
        registry.reload();
        cleanupInvalidData();
    }

    public synchronized void ensure(Player player) {
        UUID uuid = player.getUniqueId();
        boolean changed = cleanupExpired(uuid);
        String selected = selectedColors.get(uuid);
        if (selected != null && !hasColorInternal(uuid, selected, System.currentTimeMillis())) {
            selectedColors.remove(uuid);
            changed = true;
        }
        if (changed) {
            saveIfConfigured();
        }
    }

    public List<MessageColorDefinition> allColors() {
        return registry.sorted();
    }

    public List<String> allColorIds() {
        return registry.allIds();
    }

    public Optional<MessageColorDefinition> getDefinition(String id) {
        return registry.get(id);
    }

    public boolean isAvailable(Player player, MessageColorDefinition definition) {
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
        Map<String, MessageColorGrant> playerGrants = grants.get(uuid);
        if (playerGrants == null || playerGrants.isEmpty()) {
            return List.of();
        }
        return playerGrants.keySet().stream()
                .filter(id -> registry.get(id).isPresent())
                .sorted(Comparator
                        .comparingInt((String id) -> registry.get(id).map(MessageColorDefinition::priority).orElse(0))
                        .reversed()
                        .thenComparing(id -> id))
                .toList();
    }

    public synchronized List<MessageColorDefinition> getOwnedColors(OfflinePlayer player) {
        List<MessageColorDefinition> result = new ArrayList<>();
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
        Optional<MessageColorDefinition> definition = registry.get(id);
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
                .put(id, new MessageColorGrant(expiresAt));
        saveIfConfigured();
        return true;
    }

    public synchronized boolean revoke(OfflinePlayer player, String colorId) {
        String id = normalize(colorId);
        UUID uuid = player.getUniqueId();
        Map<String, MessageColorGrant> playerGrants = grants.get(uuid);
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
        return registry.get(id).map(MessageColorDefinition::name).orElse(id);
    }

    public String preview(MessageColorDefinition definition) {
        return render(registry.previewText(), definition);
    }

    public String render(String text, MessageColorDefinition definition) {
        if (definition.hasGradient()) {
            return MessageGradientRenderer.render(text, definition.gradient());
        }
        return definition.format() + text;
    }

    public String selectedGradient(OfflinePlayer player) {
        return selectedId(player)
                .flatMap(registry::get)
                .filter(MessageColorDefinition::hasGradient)
                .map(definition -> String.join(",", definition.gradient()))
                .orElse("");
    }

    public String selectedFormat(OfflinePlayer player) {
        return selectedId(player)
                .flatMap(registry::get)
                .map(MessageColorDefinition::format)
                .orElse("");
    }

    public String selectedStyle(OfflinePlayer player) {
        String gradient = selectedGradient(player);
        return gradient.isBlank() ? selectedFormat(player) : gradient;
    }

    public String coloredMessageLegacy(OfflinePlayer player, String text) {
        Optional<String> selected = selectedId(player);
        if (selected.isEmpty()) {
            return text == null ? "" : text;
        }
        MessageColorDefinition definition = registry.get(selected.get()).orElse(null);
        if (definition == null) {
            return text == null ? "" : text;
        }
        return ColorUtil.legacySection(render(text == null ? "" : text, definition) + "&r");
    }

    public synchronized String formatRemaining(OfflinePlayer player, String colorId) {
        String id = normalize(colorId);
        Map<String, MessageColorGrant> playerGrants = grants.get(player.getUniqueId());
        MessageColorGrant grant = playerGrants == null ? null : playerGrants.get(id);
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

    private MessageColorStore.Snapshot snapshot() {
        Map<UUID, Map<String, MessageColorGrant>> grantSnapshot = new HashMap<>();
        for (Map.Entry<UUID, Map<String, MessageColorGrant>> entry : grants.entrySet()) {
            grantSnapshot.put(entry.getKey(), Map.copyOf(entry.getValue()));
        }
        return new MessageColorStore.Snapshot(Map.copyOf(selectedColors), Map.copyOf(grantSnapshot));
    }

    private void cleanupInvalidData() {
        boolean changed = false;
        long now = System.currentTimeMillis();
        for (UUID uuid : new HashSet<>(grants.keySet())) {
            Map<String, MessageColorGrant> playerGrants = grants.get(uuid);
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
        Map<String, MessageColorGrant> playerGrants = grants.get(uuid);
        if (playerGrants == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        Set<String> expired = new HashSet<>();
        for (Map.Entry<String, MessageColorGrant> entry : playerGrants.entrySet()) {
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
        Map<String, MessageColorGrant> playerGrants = grants.get(uuid);
        if (playerGrants == null) {
            return false;
        }
        MessageColorGrant grant = playerGrants.get(id);
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
        Map<String, MessageColorGrant> playerGrants = grants.get(uuid);
        if (playerGrants == null) {
            return false;
        }
        MessageColorGrant grant = playerGrants.get(id);
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
