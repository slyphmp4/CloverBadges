package com.slyph.cloverbadges.config;

import com.slyph.cloverbadges.CloverBadges;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

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
        badges = YamlConfiguration.loadConfiguration(badgesFile);
        messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public YamlConfiguration badges() {
        return badges;
    }

    public YamlConfiguration messages() {
        return messages;
    }
}
