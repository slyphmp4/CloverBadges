package com.slyph.cloverbadges.config;

import com.slyph.cloverbadges.CloverBadges;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class ConfigManager {
    private final CloverBadges plugin;
    private final File badgesFile;
    private final File messagesFile;
    private volatile YamlConfiguration badges;
    private volatile YamlConfiguration messages;

    public ConfigManager(CloverBadges plugin) {
        this.plugin = plugin;
        this.badgesFile = new File(plugin.getDataFolder(), "badges.yml");
        this.messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        ensureFiles();
        reload();
    }

    private void ensureFiles() {
        plugin.saveDefaultConfig();
        if (!badgesFile.exists()) {
            plugin.saveResource("badges.yml", false);
        }
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
    }

    public void reload() {
        plugin.reloadConfig();
        badges = loadWithDefaults(badgesFile, "badges.yml");
        messages = loadWithDefaults(messagesFile, "messages.yml");
    }

    private YamlConfiguration loadWithDefaults(File file, String resourceName) {
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        if (resourceName.equals("messages.yml")) {
            migrateMessageKeys(configuration);
        }
        try (InputStream inputStream = plugin.getResource(resourceName)) {
            if (inputStream == null) {
                return configuration;
            }
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8)
            );
            configuration.setDefaults(defaults);
            configuration.options().copyDefaults(true);
            configuration.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to update " + resourceName + ": " + exception.getMessage());
        }
        return configuration;
    }

    private void migrateMessageKeys(YamlConfiguration configuration) {
        migratePath(configuration, "messages.help-take", "messages.help-remove");
        migratePath(configuration, "messages.badge-taken", "messages.badge-remove-success");
    }

    private void migratePath(YamlConfiguration configuration, String oldPath, String newPath) {
        if (!configuration.contains(oldPath)) {
            return;
        }
        if (!configuration.contains(newPath)) {
            configuration.set(newPath, configuration.get(oldPath));
        }
        configuration.set(oldPath, null);
    }

    public YamlConfiguration badges() {
        return badges;
    }

    public YamlConfiguration messages() {
        return messages;
    }
}
