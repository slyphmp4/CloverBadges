package com.slyph.cloverbadges.nicknamecolor;

import com.slyph.cloverbadges.config.ConfigManager;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class NicknameColorRegistry {
    private final ConfigManager configManager;
    private volatile Map<String, NicknameColorDefinition> colors = Map.of();

    public NicknameColorRegistry(ConfigManager configManager) {
        this.configManager = configManager;
        reload();
    }

    public void reload() {
        ConfigurationSection section = configManager.nicknameColors().getConfigurationSection("colors");
        if (section == null) {
            colors = Map.of();
            return;
        }

        Map<String, NicknameColorDefinition> loaded = new LinkedHashMap<>();
        for (String rawId : section.getKeys(false)) {
            String id = rawId.toLowerCase(Locale.ROOT);
            String base = "colors." + rawId + ".";
            String format = configManager.nicknameColors().getString(base + "format", "");
            if (format == null || format.isBlank()) {
                continue;
            }
            String name = configManager.nicknameColors().getString(base + "name", id);
            String permission = configManager.nicknameColors().getString(base + "permission", "");
            int priority = configManager.nicknameColors().getInt(base + "priority", 0);
            loaded.put(id, new NicknameColorDefinition(id, name, format, permission, priority));
        }
        colors = Map.copyOf(loaded);
    }

    public Optional<NicknameColorDefinition> get(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(colors.get(id.toLowerCase(Locale.ROOT)));
    }

    public Collection<NicknameColorDefinition> all() {
        return colors.values();
    }

    public List<NicknameColorDefinition> sorted() {
        return colors.values().stream()
                .sorted(Comparator.comparingInt(NicknameColorDefinition::priority).reversed().thenComparing(NicknameColorDefinition::id))
                .toList();
    }
}
