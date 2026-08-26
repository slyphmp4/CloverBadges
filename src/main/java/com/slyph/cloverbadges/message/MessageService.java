package com.slyph.cloverbadges.message;

import com.slyph.cloverbadges.config.ConfigManager;
import com.slyph.cloverbadges.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class MessageService {
    private final ConfigManager configManager;

    public MessageService(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Map.of());
    }

    public void send(CommandSender sender, String key, Map<String, String> replacements) {
        for (Component component : render(key, replacements)) {
            sender.sendMessage(component);
        }
    }

    public List<Component> render(String key, Map<String, String> replacements) {
        List<String> lines = configManager.messages().getStringList("messages." + key);
        List<Component> result = new ArrayList<>();
        for (String line : lines) {
            String rendered = line;
            for (Map.Entry<String, String> replacement : replacements.entrySet()) {
                rendered = rendered.replace("{" + replacement.getKey() + "}", replacement.getValue());
            }
            result.add(ColorUtil.component(rendered));
        }
        return result;
    }

    public String firstPlain(String key) {
        List<String> lines = configManager.messages().getStringList("messages." + key);
        if (lines.isEmpty()) {
            return "";
        }
        return ColorUtil.plain(lines.getFirst());
    }

    public String firstRaw(String key) {
        List<String> lines = configManager.messages().getStringList("messages." + key);
        return lines.isEmpty() ? "" : lines.getFirst();
    }
}
