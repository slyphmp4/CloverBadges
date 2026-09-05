package com.slyph.cloverbadges.nametag.render;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class ArmorStandNametagRenderer extends AbstractNametagRenderer {
    private final ArmorStand armorStand;
    private final double yOffset;

    public ArmorStandNametagRenderer(Plugin plugin, Player owner, double yOffset) {
        this(plugin, spawn(owner, yOffset), yOffset);
    }

    private ArmorStandNametagRenderer(Plugin plugin, ArmorStand armorStand, double yOffset) {
        super(plugin, armorStand);
        this.armorStand = armorStand;
        this.yOffset = yOffset;

        armorStand.setPersistent(false);
        armorStand.setGravity(false);
        armorStand.setVisible(false);
        armorStand.setMarker(true);
        armorStand.setSmall(true);
        armorStand.setBasePlate(false);
        armorStand.setArms(false);
        armorStand.setCustomNameVisible(true);
        armorStand.setSilent(true);
        armorStand.setInvulnerable(true);
        armorStand.setCollidable(false);
        armorStand.customName(Component.empty());
        try {
            armorStand.setVisibleByDefault(false);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public String type() {
        return "ARMOR_STAND";
    }

    @Override
    public void setText(Component text) {
        armorStand.customName(text == null ? Component.empty() : text);
        armorStand.setCustomNameVisible(true);
    }

    @Override
    public boolean sync(Player owner) {
        if (!valid()) {
            return false;
        }
        if (!armorStand.getWorld().equals(owner.getWorld())) {
            return false;
        }

        try {
            Location target = owner.getLocation().clone().add(0.0D, yOffset, 0.0D);
            return armorStand.teleport(target);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static ArmorStand spawn(Player owner, double yOffset) {
        Location location = owner.getLocation().clone().add(0.0D, yOffset, 0.0D);
        return owner.getWorld().spawn(location, ArmorStand.class);
    }
}
