package com.slyph.cloverbadges.nametag.render;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

public interface NametagRenderer {
    String type();

    void setText(Component text);

    boolean sync(Player owner);

    void setVisible(Player viewer, boolean visible);

    void retainViewers(Set<UUID> viewers);

    void remove();
}
