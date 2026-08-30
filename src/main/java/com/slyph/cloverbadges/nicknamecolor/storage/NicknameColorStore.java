package com.slyph.cloverbadges.nicknamecolor.storage;

import com.slyph.cloverbadges.CloverBadges;
import com.slyph.cloverbadges.nicknamecolor.NicknameColorGrant;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class NicknameColorStore {
    private final CloverBadges plugin;
    private final File file;

    public NicknameColorStore(CloverBadges plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "nickname-colors-data.yml");
    }

    public Snapshot loadAll() {
        Map<UUID, String> selected = new HashMap<>();
        Map<UUID, Map<String, NicknameColorGrant>> grants = new HashMap<>();
        Set<UUID> starterInitialized = new HashSet<>();
        if (!file.exists()) {
            return new Snapshot(selected, grants, starterInitialized);
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return new Snapshot(selected, grants, starterInitialized);
        }

        for (String rawUuid : players.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(rawUuid);
            } catch (IllegalArgumentException exception) {
                continue;
            }

            String base = "players." + rawUuid + ".";
            String selectedId = yaml.getString(base + "selected", "");
            if (selectedId != null && !selectedId.isBlank()) {
                selectedId = selectedId.toLowerCase(Locale.ROOT);
                selected.put(uuid, selectedId);
            }

            Map<String, NicknameColorGrant> playerGrants = new HashMap<>();
            ConfigurationSection grantsSection = yaml.getConfigurationSection(base + "grants");
            if (grantsSection != null) {
                for (String rawId : grantsSection.getKeys(false)) {
                    String id = rawId.toLowerCase(Locale.ROOT);
                    long expiresAt = yaml.getLong(base + "grants." + rawId + ".expires-at", 0L);
                    playerGrants.put(id, new NicknameColorGrant(expiresAt));
                }
            }

            if (playerGrants.isEmpty() && selectedId != null && !selectedId.isBlank()) {
                playerGrants.put(selectedId, new NicknameColorGrant(0L));
                starterInitialized.add(uuid);
            }

            if (!playerGrants.isEmpty()) {
                grants.put(uuid, playerGrants);
            }

            if (yaml.getBoolean(base + "starter-initialized", false)) {
                starterInitialized.add(uuid);
            }
        }

        return new Snapshot(selected, grants, starterInitialized);
    }

    public synchronized void saveAll(
            Map<UUID, String> selectedColors,
            Map<UUID, Map<String, NicknameColorGrant>> grants,
            Set<UUID> starterInitialized
    ) {
        YamlConfiguration yaml = new YamlConfiguration();
        Set<UUID> players = new HashSet<>();
        players.addAll(selectedColors.keySet());
        players.addAll(grants.keySet());
        players.addAll(starterInitialized);

        for (UUID uuid : players) {
            String base = "players." + uuid + ".";
            String selected = selectedColors.get(uuid);
            if (selected != null && !selected.isBlank()) {
                yaml.set(base + "selected", selected);
            }
            if (starterInitialized.contains(uuid)) {
                yaml.set(base + "starter-initialized", true);
            }
            Map<String, NicknameColorGrant> playerGrants = grants.get(uuid);
            if (playerGrants != null) {
                for (Map.Entry<String, NicknameColorGrant> entry : playerGrants.entrySet()) {
                    yaml.set(base + "grants." + entry.getKey() + ".expires-at", entry.getValue().expiresAt());
                }
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
            plugin.getLogger().severe("Не удалось сохранить nickname-colors-data.yml: " + exception.getMessage());
        }
    }

    public record Snapshot(
            Map<UUID, String> selectedColors,
            Map<UUID, Map<String, NicknameColorGrant>> grants,
            Set<UUID> starterInitialized
    ) {
    }
}
