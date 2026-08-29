package com.slyph.cloverbadges.nicknamecolor;

import com.slyph.cloverbadges.CloverBadges;
import com.slyph.cloverbadges.nicknamecolor.storage.NicknameColorStore;
import com.slyph.cloverbadges.util.ColorUtil;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerNicknameColorService {
    private final CloverBadges plugin;
    private final NicknameColorRegistry registry;
    private final NicknameColorStore store;
    private final Map<UUID, String> selectedColors;

    public PlayerNicknameColorService(CloverBadges plugin, NicknameColorRegistry registry, NicknameColorStore store) {
        this.plugin = plugin;
        this.registry = registry;
        this.store = store;
        this.selectedColors = new ConcurrentHashMap<>(store.loadAll());
        cleanupInvalidSelections();
    }

    public void reload() {
        registry.reload();
        cleanupInvalidSelections();
    }

    public List<NicknameColorDefinition> allColors() {
        return registry.sorted();
    }

    public Optional<NicknameColorDefinition> getDefinition(String id) {
        return registry.get(id);
    }

    public boolean isAvailable(Player player, NicknameColorDefinition definition) {
        return !definition.hasPermission() || player.hasPermission(definition.permission());
    }

    public Optional<String> selectedId(OfflinePlayer player) {
        String id = selectedColors.get(player.getUniqueId());
        if (id == null) {
            return Optional.empty();
        }
        Optional<NicknameColorDefinition> definition = registry.get(id);
        if (definition.isEmpty()) {
            return Optional.empty();
        }
        Player online = player.getPlayer();
        if (definition.get().hasPermission() && (online == null || !online.hasPermission(definition.get().permission()))) {
            return Optional.empty();
        }
        return Optional.of(id);
    }

    public synchronized boolean select(Player player, String colorId) {
        Optional<NicknameColorDefinition> definition = registry.get(colorId);
        if (definition.isEmpty() || !isAvailable(player, definition.get())) {
            return false;
        }
        selectedColors.put(player.getUniqueId(), definition.get().id());
        saveIfConfigured();
        return true;
    }

    public synchronized void clear(OfflinePlayer player) {
        if (selectedColors.remove(player.getUniqueId()) != null) {
            saveIfConfigured();
        }
    }

    public String getColorName(String id) {
        return registry.get(id).map(NicknameColorDefinition::name).orElse(id);
    }

    public String preview(Player player, NicknameColorDefinition definition) {
        return definition.format().replace("{player}", player.getName());
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
        String rendered = definition.format().replace("{player}", playerName);
        return ColorUtil.legacySection(rendered + "&r");
    }

    public void saveAll() {
        store.saveAll(selectedColors);
    }

    private void cleanupInvalidSelections() {
        boolean changed = selectedColors.entrySet().removeIf(entry -> registry.get(entry.getValue()).isEmpty());
        if (changed) {
            saveIfConfigured();
        }
    }

    private void saveIfConfigured() {
        if (plugin.getConfig().getBoolean("storage.save-on-change", true)) {
            saveAll();
        }
    }
}
