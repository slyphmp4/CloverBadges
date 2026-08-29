package com.slyph.cloverbadges.gui;

import com.slyph.cloverbadges.config.ConfigManager;
import com.slyph.cloverbadges.message.MessageService;
import com.slyph.cloverbadges.player.BadgeToggleResult;
import com.slyph.cloverbadges.player.PlayerBadgeService;
import com.slyph.cloverbadges.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class BadgeMenuManager {
    private static final List<Integer> DEFAULT_BADGE_SLOTS = List.of(20, 21, 22, 23, 24, 29, 30, 31, 32, 33);
    private final ConfigManager configManager;
    private final PlayerBadgeService badgeService;
    private final MessageService messages;

    public BadgeMenuManager(ConfigManager configManager, PlayerBadgeService badgeService, MessageService messages) {
        this.configManager = configManager;
        this.badgeService = badgeService;
        this.messages = messages;
    }

    public void open(Player player) {
        BadgeMenuHolder holder = new BadgeMenuHolder(player.getUniqueId());
        int size = inventorySize();
        String title = applyPlaceholders(
                configManager.gui().getString("menu.title", "&8Значки"),
                player,
                null,
                badgeService.getOwnedBadgeIds(player).size(),
                badgeService.getActiveBadgeIds(player).size()
        );
        Inventory inventory = Bukkit.createInventory(holder, size, ColorUtil.component(title));
        holder.inventory(inventory);
        render(holder, player);
        player.openInventory(inventory);
    }

    public void handleClick(Player player, BadgeMenuHolder holder, int rawSlot) {
        if (!holder.playerId().equals(player.getUniqueId())) {
            return;
        }

        int clearSlot = validSlot(configManager.gui().getInt("clear-all.slot", 40), holder.getInventory().getSize(), 40);
        if (rawSlot == clearSlot && !badgeService.getOwnedBadgeIds(player).isEmpty()) {
            badgeService.clearSelection(player);
            render(holder, player);
            return;
        }

        String badgeId = holder.badgeAt(rawSlot);
        if (badgeId == null) {
            return;
        }

        BadgeToggleResult result = badgeService.toggleDisplay(player, badgeId);
        if (result == BadgeToggleResult.LIMIT_REACHED) {
            messages.send(player, "gui-max-active", Map.of(
                    "max", Integer.toString(badgeService.maxVisibleBadges())
            ));
        }
        render(holder, player);
    }

    private void render(BadgeMenuHolder holder, Player player) {
        Inventory inventory = holder.getInventory();
        inventory.clear();
        holder.clearBadgeSlots();

        List<String> owned = new ArrayList<>(badgeService.getOwnedBadgeIds(player));
        if (owned.isEmpty()) {
            renderEmptyState(inventory, player);
            return;
        }

        List<Integer> slots = badgeSlots(inventory.getSize());
        Set<String> active = new LinkedHashSet<>(badgeService.getActiveBadgeIds(player));
        int visibleOwned = Math.min(Math.min(owned.size(), badgeService.maxOwnedBadges()), slots.size());

        for (int index = 0; index < slots.size(); index++) {
            int slot = slots.get(index);
            if (index < visibleOwned) {
                String badgeId = owned.get(index);
                inventory.setItem(slot, badgeItem(player, badgeId, active.contains(badgeId), owned.size(), active.size()));
                holder.badgeSlot(slot, badgeId);
            } else {
                inventory.setItem(slot, configuredItem("empty-slot", player, null, owned.size(), active.size(), Material.LIGHT_GRAY_STAINED_GLASS_PANE));
            }
        }

        int clearSlot = validSlot(configManager.gui().getInt("clear-all.slot", 40), inventory.getSize(), 40);
        inventory.setItem(clearSlot, configuredItem("clear-all", player, null, owned.size(), active.size(), Material.BARRIER));
    }

    private void renderEmptyState(Inventory inventory, Player player) {
        int paperSlot = validSlot(configManager.gui().getInt("empty-state.paper.slot", 20), inventory.getSize(), 20);
        int appleSlot = validSlot(configManager.gui().getInt("empty-state.apple.slot", 24), inventory.getSize(), 24);
        inventory.setItem(paperSlot, configuredItem("empty-state.paper", player, null, 0, 0, Material.PAPER));
        inventory.setItem(appleSlot, configuredItem("empty-state.apple", player, null, 0, 0, Material.APPLE));
    }

    private ItemStack badgeItem(Player player, String badgeId, boolean active, int ownedCount, int activeCount) {
        YamlConfiguration badges = configManager.badges();
        YamlConfiguration gui = configManager.gui();
        String base = "badges." + badgeId + ".gui.";
        String stateKey = active ? "lore-active" : "lore-inactive";

        String materialName = badges.contains(base + "material")
                ? badges.getString(base + "material", gui.getString("badge.material", "PLAYER_HEAD"))
                : gui.getString("badge.material", "PLAYER_HEAD");
        Material material = material(materialName, Material.PLAYER_HEAD);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String name = badges.contains(base + "name")
                ? badges.getString(base + "name", gui.getString("badge.name", "{name}"))
                : gui.getString("badge.name", "{name}");
        meta.displayName(ColorUtil.component(applyPlaceholders(name, player, badgeId, ownedCount, activeCount)));

        List<String> lore = badges.isList(base + stateKey)
                ? badges.getStringList(base + stateKey)
                : gui.getStringList("badge." + stateKey);
        meta.lore(renderLore(lore, player, badgeId, ownedCount, activeCount));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack configuredItem(String path, Player player, String badgeId, int ownedCount, int activeCount, Material fallbackMaterial) {
        YamlConfiguration gui = configManager.gui();
        Material material = material(gui.getString(path + ".material", fallbackMaterial.name()), fallbackMaterial);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        String name = gui.getString(path + ".name", " ");
        meta.displayName(ColorUtil.component(applyPlaceholders(name, player, badgeId, ownedCount, activeCount)));
        meta.lore(renderLore(gui.getStringList(path + ".lore"), player, badgeId, ownedCount, activeCount));
        item.setItemMeta(meta);
        return item;
    }

    private List<Component> renderLore(List<String> lines, Player player, String badgeId, int ownedCount, int activeCount) {
        List<Component> result = new ArrayList<>();
        for (String line : lines) {
            result.add(ColorUtil.component(applyPlaceholders(line, player, badgeId, ownedCount, activeCount)));
        }
        return result;
    }

    private String applyPlaceholders(String input, Player player, String badgeId, int ownedCount, int activeCount) {
        String value = input == null ? "" : input;
        Map<String, String> replacements = new HashMap<>();
        replacements.put("player", player.getName());
        replacements.put("owned_count", Integer.toString(ownedCount));
        replacements.put("active_count", Integer.toString(activeCount));
        replacements.put("max_owned", Integer.toString(badgeService.maxOwnedBadges()));
        replacements.put("max_active", Integer.toString(badgeService.maxVisibleBadges()));
        if (badgeId != null) {
            replacements.put("id", badgeId);
            replacements.put("name", badgeService.getBadgeName(badgeId));
            replacements.put("remaining", badgeService.formatRemaining(player, badgeId));
        }
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            value = value.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return value;
    }

    private List<Integer> badgeSlots(int inventorySize) {
        List<Integer> configured = configManager.gui().getIntegerList("menu.badge-slots");
        List<Integer> source = configured.isEmpty() ? DEFAULT_BADGE_SLOTS : configured;
        LinkedHashSet<Integer> result = new LinkedHashSet<>();
        for (int slot : source) {
            if (slot >= 0 && slot < inventorySize) {
                result.add(slot);
            }
            if (result.size() >= badgeService.maxOwnedBadges()) {
                break;
            }
        }
        if (result.size() < badgeService.maxOwnedBadges()) {
            for (int slot : DEFAULT_BADGE_SLOTS) {
                if (slot >= 0 && slot < inventorySize) {
                    result.add(slot);
                }
                if (result.size() >= badgeService.maxOwnedBadges()) {
                    break;
                }
            }
        }
        return List.copyOf(result);
    }

    private int inventorySize() {
        int requested = configManager.gui().getInt("menu.size", 54);
        int clamped = Math.max(9, Math.min(54, requested));
        int rounded = ((clamped + 8) / 9) * 9;
        return Math.min(54, rounded);
    }

    private int validSlot(int configured, int size, int fallback) {
        if (configured >= 0 && configured < size) {
            return configured;
        }
        return Math.max(0, Math.min(size - 1, fallback));
    }

    private Material material(String name, Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        Material material = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
        return material == null ? fallback : material;
    }
}
