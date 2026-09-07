package com.slyph.cloverbadges.messagecolor.storage;

import com.slyph.cloverbadges.CloverBadges;
import com.slyph.cloverbadges.messagecolor.MessageColorGrant;
import com.slyph.cloverbadges.storage.AsyncYamlWriter;
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

public final class MessageColorStore {
    private final File file;
    private final AsyncYamlWriter<Snapshot> writer;

    public MessageColorStore(CloverBadges plugin) {
        this.file = new File(plugin.getDataFolder(), "message-colors-data.yml");
        this.writer = new AsyncYamlWriter<>(plugin, "CloverBadges-MessageColors", file.getName(), this::write);
    }

    public Snapshot loadAll() {
        Map<UUID, String> selected = new HashMap<>();
        Map<UUID, Map<String, MessageColorGrant>> grants = new HashMap<>();
        if (!file.exists()) {
            return new Snapshot(selected, grants);
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return new Snapshot(selected, grants);
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
                selected.put(uuid, selectedId.toLowerCase(Locale.ROOT));
            }

            Map<String, MessageColorGrant> playerGrants = new HashMap<>();
            ConfigurationSection grantsSection = yaml.getConfigurationSection(base + "grants");
            if (grantsSection != null) {
                for (String rawId : grantsSection.getKeys(false)) {
                    String id = rawId.toLowerCase(Locale.ROOT);
                    long expiresAt = yaml.getLong(base + "grants." + rawId + ".expires-at", 0L);
                    playerGrants.put(id, new MessageColorGrant(expiresAt));
                }
            }
            if (!playerGrants.isEmpty()) {
                grants.put(uuid, playerGrants);
            }
        }

        return new Snapshot(selected, grants);
    }

    public void saveAsync(Snapshot snapshot) {
        writer.submit(snapshot);
    }

    public void flushAndClose(Snapshot snapshot) {
        writer.close(snapshot);
    }

    private void write(Snapshot snapshot) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        Set<UUID> players = new HashSet<>();
        players.addAll(snapshot.selectedColors().keySet());
        players.addAll(snapshot.grants().keySet());

        for (UUID uuid : players) {
            String base = "players." + uuid + ".";
            String selected = snapshot.selectedColors().get(uuid);
            if (selected != null && !selected.isBlank()) {
                yaml.set(base + "selected", selected);
            }
            Map<String, MessageColorGrant> playerGrants = snapshot.grants().get(uuid);
            if (playerGrants != null) {
                for (Map.Entry<String, MessageColorGrant> entry : playerGrants.entrySet()) {
                    yaml.set(base + "grants." + entry.getKey() + ".expires-at", entry.getValue().expiresAt());
                }
            }
        }

        File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
        yaml.save(temporary);
        try {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public record Snapshot(
            Map<UUID, String> selectedColors,
            Map<UUID, Map<String, MessageColorGrant>> grants
    ) {
    }
}
