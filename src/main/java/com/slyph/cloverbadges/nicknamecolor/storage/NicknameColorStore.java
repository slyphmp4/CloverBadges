package com.slyph.cloverbadges.nicknamecolor.storage;

import com.slyph.cloverbadges.CloverBadges;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class NicknameColorStore {
    private final CloverBadges plugin;
    private final File file;

    public NicknameColorStore(CloverBadges plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "nickname-colors-data.yml");
    }

    public Map<UUID, String> loadAll() {
        Map<UUID, String> result = new HashMap<>();
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
            String selected = yaml.getString("players." + rawUuid + ".selected", "");
            if (selected != null && !selected.isBlank()) {
                result.put(uuid, selected.toLowerCase());
            }
        }
        return result;
    }

    public synchronized void saveAll(Map<UUID, String> selectedColors) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, String> entry : selectedColors.entrySet()) {
            yaml.set("players." + entry.getKey() + ".selected", entry.getValue());
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
            plugin.getLogger().severe("Не удалось сохранить nickname-colors-data.yml: " + exception.getMessage());
        }
    }
}
