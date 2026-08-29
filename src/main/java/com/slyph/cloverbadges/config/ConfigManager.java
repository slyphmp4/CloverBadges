package com.slyph.cloverbadges.config;

import com.slyph.cloverbadges.CloverBadges;
import org.bukkit.configuration.ConfigurationSection;
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
    private final File guiFile;
    private final File nicknameColorsFile;
    private volatile YamlConfiguration badges;
    private volatile YamlConfiguration messages;
    private volatile YamlConfiguration gui;
    private volatile YamlConfiguration nicknameColors;

    public ConfigManager(CloverBadges plugin) {
        this.plugin = plugin;
        this.badgesFile = new File(plugin.getDataFolder(), "badges.yml");
        this.messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        this.guiFile = new File(plugin.getDataFolder(), "gui.yml");
        this.nicknameColorsFile = new File(plugin.getDataFolder(), "nickname-colors.yml");
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
        if (!guiFile.exists()) {
            plugin.saveResource("gui.yml", false);
        }
        if (!nicknameColorsFile.exists()) {
            plugin.saveResource("nickname-colors.yml", false);
        }
    }

    public void reload() {
        plugin.reloadConfig();
        plugin.getConfig().options().copyDefaults(true);
        plugin.saveConfig();

        badges = loadWithDefaults(badgesFile, "badges.yml");
        messages = loadWithDefaults(messagesFile, "messages.yml");
        nicknameColors = loadWithDefaults(nicknameColorsFile, "nickname-colors.yml");

        gui = YamlConfiguration.loadConfiguration(guiFile);
        migrateGuiKeys(gui);
        boolean migratedBadgeGui = migrateBadgeGuiSettings();
        gui = applyDefaults(gui, guiFile, "gui.yml");

        if (migratedBadgeGui) {
            try {
                badges.save(badgesFile);
            } catch (IOException exception) {
                plugin.getLogger().warning("Failed to migrate badge GUI settings from badges.yml: " + exception.getMessage());
            }
        }
    }

    private YamlConfiguration loadWithDefaults(File file, String resourceName) {
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        if (resourceName.equals("badges.yml")) {
            migrateBadgeKeys(configuration);
        }
        if (resourceName.equals("messages.yml")) {
            migrateMessageKeys(configuration);
        }
        return applyDefaults(configuration, file, resourceName);
    }

    private YamlConfiguration applyDefaults(YamlConfiguration configuration, File file, String resourceName) {
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

    private void migrateBadgeKeys(YamlConfiguration configuration) {
        ConfigurationSection section = configuration.getConfigurationSection("badges");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            configuration.set("badges." + key + ".description", null);
            configuration.set("badges." + key + ".how-to-get", null);
        }
    }

    private void migrateMessageKeys(YamlConfiguration configuration) {
        migratePath(configuration, "messages.help-take", "messages.help-remove");
        migratePath(configuration, "messages.badge-taken", "messages.badge-remove-success");
    }

    private void migrateGuiKeys(YamlConfiguration configuration) {
        int layoutVersion = configuration.getInt("menu.layout-version", 1);
        if (layoutVersion < 2) {
            if (configuration.getInt("clear-all.slot", 40) == 40) {
                configuration.set("clear-all.slot", 49);
            }
            configuration.set("menu.layout-version", 2);
            layoutVersion = 2;
        }
        if (layoutVersion < 3) {
            if (configuration.getInt("clear-all.slot", 49) == 49) {
                configuration.set("clear-all.slot", 50);
            }
            configuration.set("menu.layout-version", 3);
            layoutVersion = 3;
        }
        if (layoutVersion < 4) {
            configuration.set("menu.layout-version", 4);
            layoutVersion = 4;
        }
        if (layoutVersion < 5) {
            if (configuration.getInt("menu.size", 54) == 54) {
                configuration.set("menu.size", 45);
            }
            if (configuration.getInt("info-book.slot", 48) == 48) {
                configuration.set("info-book.slot", 39);
            }
            if (configuration.getInt("clear-all.slot", 50) == 50) {
                configuration.set("clear-all.slot", 41);
            }
            configuration.set("menu.layout-version", 5);
            layoutVersion = 5;
        }
        if (layoutVersion < 6) {
            if (configuration.getInt("menu.size", 45) == 45) {
                configuration.set("menu.size", 54);
            }
            if (configuration.getInt("info-book.slot", 39) == 39) {
                configuration.set("info-book.slot", 48);
            }
            if (configuration.getInt("clear-all.slot", 41) == 41) {
                configuration.set("clear-all.slot", 50);
            }
            if (!configuration.contains("empty-state.size")) {
                configuration.set("empty-state.size", 45);
            }
            configuration.set("menu.layout-version", 6);
            layoutVersion = 6;
        }
        if (layoutVersion < 7) {
            configuration.set("menu.layout-version", 7);
        }
    }

    private boolean migrateBadgeGuiSettings() {
        ConfigurationSection section = badges.getConfigurationSection("badges");
        if (section == null) {
            return false;
        }

        boolean changed = false;
        for (String badgeId : section.getKeys(false)) {
            String sourcePath = "badges." + badgeId + ".gui";
            ConfigurationSection source = badges.getConfigurationSection(sourcePath);
            if (source == null) {
                continue;
            }

            String targetPath = "badges." + badgeId;
            if (!gui.contains(targetPath)) {
                copySection(source, gui, targetPath);
            }

            badges.set(sourcePath, null);
            changed = true;
        }
        return changed;
    }

    private void copySection(ConfigurationSection source, YamlConfiguration target, String targetPath) {
        for (String key : source.getKeys(true)) {
            Object value = source.get(key);
            if (value instanceof ConfigurationSection) {
                continue;
            }
            target.set(targetPath + "." + key, value);
        }
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

    public YamlConfiguration gui() {
        return gui;
    }

    public YamlConfiguration nicknameColors() {
        return nicknameColors;
    }
}
