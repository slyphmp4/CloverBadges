package com.slyph.cloverbadges.nametag.render;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

abstract class AbstractNametagRenderer implements NametagRenderer {
    protected final Plugin plugin;
    protected final Entity entity;
    private final Map<UUID, Boolean> visibility = new ConcurrentHashMap<>();

    protected AbstractNametagRenderer(Plugin plugin, Entity entity) {
        this.plugin = plugin;
        this.entity = entity;
    }

    @Override
    public void setVisible(Player viewer, boolean visible) {
        UUID uuid = viewer.getUniqueId();
        Boolean previous = visibility.get(uuid);
        if (previous != null && previous == visible) {
            return;
        }

        try {
            if (visible) {
                viewer.showEntity(plugin, entity);
            } else {
                viewer.hideEntity(plugin, entity);
            }
            visibility.put(uuid, visible);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void retainViewers(Set<UUID> viewers) {
        visibility.keySet().retainAll(viewers);
    }

    @Override
    public void remove() {
        try {
            entity.remove();
        } catch (Throwable ignored) {
        }
        visibility.clear();
    }

    protected boolean valid() {
        try {
            return entity.isValid() && !entity.isDead();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
