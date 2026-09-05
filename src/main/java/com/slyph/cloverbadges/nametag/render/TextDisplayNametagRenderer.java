package com.slyph.cloverbadges.nametag.render;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

public final class TextDisplayNametagRenderer extends AbstractNametagRenderer {
    private final TextDisplay display;

    public TextDisplayNametagRenderer(
            Plugin plugin,
            Player owner,
            boolean shadowed,
            boolean seeThrough,
            boolean defaultBackground,
            int lineWidth,
            float viewRange
    ) {
        this(plugin, owner, spawn(owner), shadowed, seeThrough, defaultBackground, lineWidth, viewRange);
    }

    private TextDisplayNametagRenderer(
            Plugin plugin,
            Player owner,
            TextDisplay display,
            boolean shadowed,
            boolean seeThrough,
            boolean defaultBackground,
            int lineWidth,
            float viewRange
    ) {
        super(plugin, display);
        this.display = display;

        try {
            display.setPersistent(false);
            display.setGravity(false);
            display.setBillboard(Display.Billboard.CENTER);
            display.setAlignment(TextDisplay.TextAlignment.CENTER);
            display.setShadowed(shadowed);
            display.setSeeThrough(seeThrough);
            display.setDefaultBackground(defaultBackground);
            display.setLineWidth(Math.max(1, lineWidth));
            display.setViewRange(Math.max(0.1F, viewRange));
            display.text(Component.empty());
            try {
                display.setVisibleByDefault(false);
            } catch (Throwable ignored) {
            }
            if (!owner.addPassenger(display)) {
                throw new IllegalStateException("Unable to attach TextDisplay to player");
            }
        } catch (RuntimeException | Error throwable) {
            display.remove();
            throw throwable;
        }
    }

    @Override
    public String type() {
        return "TEXT_DISPLAY";
    }

    @Override
    public void setText(Component text) {
        display.text(text == null ? Component.empty() : text);
    }

    @Override
    public boolean sync(Player owner) {
        if (!valid()) {
            return false;
        }
        if (!display.getWorld().equals(owner.getWorld())) {
            return false;
        }
        if (display.getVehicle() == owner) {
            return true;
        }

        try {
            display.teleport(owner.getLocation());
            return owner.addPassenger(display);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static TextDisplay spawn(Player owner) {
        Location location = owner.getLocation();
        return owner.getWorld().spawn(location, TextDisplay.class);
    }
}
