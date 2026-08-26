package com.slyph.cloverbadges.storage;

import com.slyph.cloverbadges.CloverBadges;
import com.slyph.cloverbadges.player.BadgeGrant;
import com.slyph.cloverbadges.player.PlayerBadgeData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerDataStore {
    private final CloverBadges plugin;
    private final File file;

    public PlayerDataStore(CloverBadges plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
    }

    public Map<UUID, PlayerBadgeData> loadAll() {
        Map<UUID, PlayerBadgeData> result = new HashMap<>();
        if (!file.exists()) {
            return result;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return result;
        }

        for (String rawUuid : players.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(rawUuid);
            } catch (IllegalArgumentException exception) {
                continue;
            }

            String path = "players." + rawUuid;
            long firstSeen = yaml.getLong(path + ".first-seen", 0L);
            String selected = yaml.getString(path + ".selected", "");
            if (selected != null && selected.isBlank()) {
                selected = null;
            }
            boolean disabled = yaml.getBoolean(path + ".selection-disabled", false);
            Map<String, BadgeGrant> grants = new LinkedHashMap<>();
            ConfigurationSection grantSection = yaml.getConfigurationSection(path + ".grants");
            if (grantSection != null) {
                for (String badgeId : grantSection.getKeys(false)) {
                    long expiresAt = grantSection.getLong(badgeId + ".expires-at", 0L);
                    grants.put(badgeId.toLowerCase(), new BadgeGrant(expiresAt));
                }
            }
            result.put(uuid, new PlayerBadgeData(uuid, firstSeen, selected, disabled, grants));
        }
        return result;
    }

    public synchronized void saveAll(Collection<PlayerBadgeData> data) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (PlayerBadgeData playerData : data) {
            String path = "players." + playerData.uuid();
            yaml.set(path + ".first-seen", playerData.firstSeen());
            yaml.set(path + ".selected", playerData.selectedBadge() == null ? "" : playerData.selectedBadge());
            yaml.set(path + ".selection-disabled", playerData.selectionDisabled());
            for (Map.Entry<String, BadgeGrant> entry : playerData.grants().entrySet()) {
                yaml.set(path + ".grants." + entry.getKey() + ".expires-at", entry.getValue().expiresAt());
            }
        }

        File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            yaml.save(temporary);
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            plugin.getLogger().severe("Не удалось сохранить players.yml: " + exception.getMessage());
        }
    }
}
