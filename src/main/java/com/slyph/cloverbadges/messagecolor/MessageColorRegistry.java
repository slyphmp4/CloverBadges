package com.slyph.cloverbadges.messagecolor;

import com.slyph.cloverbadges.config.ConfigManager;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class MessageColorRegistry {
    private final ConfigManager configManager;
    private volatile Map<String, MessageColorDefinition> colors = Map.of();

    public MessageColorRegistry(ConfigManager configManager) {
        this.configManager = configManager;
        reload();
    }

    public void reload() {
        ConfigurationSection section = configManager.messageColors().getConfigurationSection("colors");
        if (section == null) {
            colors = Map.of();
            return;
        }

        Map<String, MessageColorDefinition> loaded = new LinkedHashMap<>();
        for (String rawId : section.getKeys(false)) {
            String id = rawId.toLowerCase(Locale.ROOT);
            String base = "colors." + rawId + ".";
            String format = configManager.messageColors().getString(base + "format", "");
            List<String> gradient = configManager.messageColors().getStringList(base + "gradient");
            if ((format == null || format.isBlank()) && gradient.size() < 2) {
                continue;
            }
            String name = configManager.messageColors().getString(base + "name", id);
            String permission = configManager.messageColors().getString(base + "permission", "");
            int priority = configManager.messageColors().getInt(base + "priority", 0);
            loaded.put(id, new MessageColorDefinition(id, name, format == null ? "" : format, gradient, permission, priority));
        }
        colors = Map.copyOf(loaded);
    }

    public Optional<MessageColorDefinition> get(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(colors.get(id.toLowerCase(Locale.ROOT)));
    }

    public Collection<MessageColorDefinition> all() {
        return colors.values();
    }

    public List<MessageColorDefinition> sorted() {
        return colors.values().stream()
                .sorted(Comparator.comparingInt(MessageColorDefinition::priority).reversed().thenComparing(MessageColorDefinition::id))
                .toList();
    }

    public List<String> allIds() {
        return sorted().stream().map(MessageColorDefinition::id).toList();
    }

    public String previewText() {
        String value = configManager.messageColors().getString("preview-text", "Пример сообщения");
        return value == null || value.isBlank() ? "Пример сообщения" : value;
    }
}
