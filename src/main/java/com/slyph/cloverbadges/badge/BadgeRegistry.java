package com.slyph.cloverbadges.badge;

import com.slyph.cloverbadges.config.ConfigManager;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class BadgeRegistry {
    private final ConfigManager configManager;
    private volatile Map<String, BadgeDefinition> badges = Map.of();

    public BadgeRegistry(ConfigManager configManager) {
        this.configManager = configManager;
        reload();
    }

    public void reload() {
        ConfigurationSection section = configManager.badges().getConfigurationSection("badges");
        if (section == null) {
            badges = Map.of();
            return;
        }

        Map<String, BadgeDefinition> loaded = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            String id = key.toLowerCase();
            String name = section.getString(key + ".name", id);
            String text = section.getString(key + ".text", "");
            String permission = section.getString(key + ".permission", "");
            loaded.put(id, new BadgeDefinition(id, name, text, permission));
        }
        badges = Collections.unmodifiableMap(loaded);
    }

    public Optional<BadgeDefinition> get(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(badges.get(id.toLowerCase()));
    }

    public boolean contains(String id) {
        return get(id).isPresent();
    }

    public Set<String> ids() {
        return badges.keySet();
    }

    public Collection<BadgeDefinition> all() {
        return badges.values();
    }
}
