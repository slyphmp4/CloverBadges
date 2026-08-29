package com.slyph.cloverbadges.gui;

import com.slyph.cloverbadges.config.ConfigManager;
import com.slyph.cloverbadges.gui.action.BadgeActionExecutor;
import com.slyph.cloverbadges.head.CustomHeadService;
import com.slyph.cloverbadges.player.PlayerBadgeService;
import com.slyph.cloverbadges.util.ColorUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
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
    private final BadgeActionExecutor actionExecutor;
    private final CustomHeadService customHeadService;

    public BadgeMenuManager(ConfigManager configManager, PlayerBadgeService badgeService, BadgeActionExecutor actionExecutor, CustomHeadService customHeadService) {
        this.configManager = configManager;
        this.badgeService = badgeService;
        this.actionExecutor = actionExecutor;
        this.customHeadService = customHeadService;
    }

    public void open(Player player) {
        BadgeMenuHolder holder = new BadgeMenuHolder(player.getUniqueId());
        int ownedCount = badgeService.getOwnedBadgeIds(player).size();
        int activeCount = badgeService.getActiveBadgeIds(player).size();
        int size = inventorySize(ownedCount == 0);
        String title = applyPlaceholders(
                configManager.gui().getString("menu.title", "&8Значки"),
                player,
                null,
                ownedCount,
                activeCount
        );
        Inventory inventory = Bukkit.createInventory(holder, size, ColorUtil.legacySection(title));
        holder.inventory(inventory);
        render(holder, player);
        player.openInventory(inventory);
    }

    public void handleClick(Player player, BadgeMenuHolder holder, int rawSlot, ClickType clickType) {
        if (!holder.playerId().equals(player.getUniqueId())) {
            return;
        }

        Inventory inventory = holder.getInventory();
        if (inventory == null) {
            return;
        }

        int clearSlot = validSlot(configManager.gui().getInt("clear-all.slot", 50), inventory.getSize(), 50);
        if (rawSlot == clearSlot && !badgeService.getOwnedBadgeIds(player).isEmpty()) {
            badgeService.clearSelection(player);
            render(holder, player);
            return;
        }

        String badgeId = holder.badgeAt(rawSlot);
        if (badgeId == null) {
            return;
        }

        int ownedCount = badgeService.getOwnedBadgeIds(player).size();
        int activeCount = badgeService.getActiveBadgeIds(player).size();
        boolean active = badgeService.getActiveBadgeIds(player).contains(badgeId);
        List<String> actions = badgeActions(badgeId, active, clickType);
        BadgeActionExecutor.Result result = actionExecutor.execute(
                player,
                badgeId,
                actions,
                input -> applyPlaceholders(input, player, badgeId, ownedCount, activeCount)
        );

        if (result.close()) {
            player.closeInventory();
            return;
        }
        if (result.refresh()) {
            render(holder, player);
        }
    }

    private void render(BadgeMenuHolder holder, Player player) {
        Inventory inventory = holder.getInventory();
        if (inventory == null) {
            return;
        }
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

        int infoBookSlot = validSlot(configManager.gui().getInt("info-book.slot", 48), inventory.getSize(), 48);
        inventory.setItem(infoBookSlot, configuredItem("info-book", player, null, owned.size(), active.size(), Material.BOOK));

        int clearSlot = validSlot(configManager.gui().getInt("clear-all.slot", 50), inventory.getSize(), 50);
        inventory.setItem(clearSlot, configuredItem("clear-all", player, null, owned.size(), active.size(), Material.BARRIER));
    }

    private void renderEmptyState(Inventory inventory, Player player) {
        int paperSlot = validSlot(configManager.gui().getInt("empty-state.paper.slot", 20), inventory.getSize(), 20);
        int appleSlot = validSlot(configManager.gui().getInt("empty-state.apple.slot", 24), inventory.getSize(), 24);
        inventory.setItem(paperSlot, configuredItem("empty-state.paper", player, null, 0, 0, Material.PAPER));
        inventory.setItem(appleSlot, configuredItem("empty-state.apple", player, null, 0, 0, Material.APPLE));
    }

    private ItemStack badgeItem(Player player, String badgeId, boolean active, int ownedCount, int activeCount) {
        YamlConfiguration gui = configManager.gui();
        String base = "badges." + badgeId + ".";
        String state = active ? "active" : "inactive";

        String materialName = badgeString(gui, base, "material-" + state, "material", "PLAYER_HEAD");
        Material material = material(materialName, Material.PLAYER_HEAD);
        ItemStack item = new ItemStack(material);
        item.setAmount(Math.max(1, Math.min(material.getMaxStackSize(), badgeInt(gui, base, "amount-" + state, "amount", 1))));
        ItemMeta meta = item.getItemMeta();

        String headName = badgeHeadString(gui, base, state, "minecraft-heads");
        String headValue = badgeHeadString(gui, base, state, "value");
        customHeadService.apply(meta, headName, headValue);

        String name = badgeString(gui, base, "name-" + state, "name", "{name}");
        meta.displayName(guiText(applyPlaceholders(name, player, badgeId, ownedCount, activeCount)));

        List<String> lore = badgeLore(gui, base, state);
        meta.lore(renderLore(lore, player, badgeId, ownedCount, activeCount));
        item.setItemMeta(meta);
        return item;
    }

    private String badgeString(YamlConfiguration gui, String base, String stateKey, String commonKey, String fallback) {
        if (gui.contains(base + stateKey)) {
            return gui.getString(base + stateKey, fallback);
        }
        if (gui.contains(base + commonKey)) {
            return gui.getString(base + commonKey, fallback);
        }
        if (gui.contains("badge." + stateKey)) {
            return gui.getString("badge." + stateKey, fallback);
        }
        return gui.getString("badge." + commonKey, fallback);
    }

    private String badgeHeadString(YamlConfiguration gui, String base, String state, String key) {
        String badgeStatePath = base + "head-" + state + "." + key;
        if (gui.contains(badgeStatePath)) {
            return gui.getString(badgeStatePath, "");
        }
        String badgePath = base + "head." + key;
        if (gui.contains(badgePath)) {
            return gui.getString(badgePath, "");
        }
        String globalStatePath = "badge.head-" + state + "." + key;
        if (gui.contains(globalStatePath)) {
            return gui.getString(globalStatePath, "");
        }
        return gui.getString("badge.head." + key, "");
    }

    private int badgeInt(YamlConfiguration gui, String base, String stateKey, String commonKey, int fallback) {
        if (gui.contains(base + stateKey)) {
            return gui.getInt(base + stateKey, fallback);
        }
        if (gui.contains(base + commonKey)) {
            return gui.getInt(base + commonKey, fallback);
        }
        if (gui.contains("badge." + stateKey)) {
            return gui.getInt("badge." + stateKey, fallback);
        }
        return gui.getInt("badge." + commonKey, fallback);
    }

    private List<String> badgeLore(YamlConfiguration gui, String base, String state) {
        String statePath = base + "lore-" + state;
        if (gui.isList(statePath)) {
            return gui.getStringList(statePath);
        }
        if (gui.isList(base + "lore")) {
            return gui.getStringList(base + "lore");
        }
        if (gui.isList("badge.lore-" + state)) {
            return gui.getStringList("badge.lore-" + state);
        }
        return gui.getStringList("badge.lore");
    }

    private List<String> badgeActions(String badgeId, boolean active, ClickType clickType) {
        YamlConfiguration gui = configManager.gui();
        String base = "badges." + badgeId + ".";
        String click = clickKey(clickType);
        String state = active ? "actions-active." : "actions-inactive.";

        List<String> paths = List.of(
                state + click,
                state + "any",
                "actions." + click,
                "actions.any"
        );

        for (String path : paths) {
            List<String> configured = actionList(gui, base + path);
            if (configured != null) {
                return configured;
            }
        }
        for (String path : paths) {
            List<String> configured = actionList(gui, "badge." + path);
            if (configured != null) {
                return configured;
            }
        }
        return List.of("[toggle]");
    }

    private List<String> actionList(YamlConfiguration configuration, String path) {
        if (!configuration.contains(path)) {
            return null;
        }
        if (configuration.isList(path)) {
            return configuration.getStringList(path);
        }
        String value = configuration.getString(path);
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value);
    }

    private String clickKey(ClickType clickType) {
        if (clickType == ClickType.SHIFT_LEFT) {
            return "shift-left";
        }
        if (clickType == ClickType.SHIFT_RIGHT) {
            return "shift-right";
        }
        if (clickType == ClickType.LEFT) {
            return "left";
        }
        if (clickType == ClickType.RIGHT) {
            return "right";
        }
        if (clickType == ClickType.MIDDLE) {
            return "middle";
        }
        if (clickType == ClickType.DROP) {
            return "drop";
        }
        if (clickType == ClickType.CONTROL_DROP) {
            return "control-drop";
        }
        if (clickType == ClickType.DOUBLE_CLICK) {
            return "double";
        }
        if (clickType == ClickType.NUMBER_KEY) {
            return "number";
        }
        if (clickType == ClickType.SWAP_OFFHAND) {
            return "swap-offhand";
        }
        return "any";
    }

    private ItemStack configuredItem(String path, Player player, String badgeId, int ownedCount, int activeCount, Material fallbackMaterial) {
        YamlConfiguration gui = configManager.gui();
        Material material = material(gui.getString(path + ".material", fallbackMaterial.name()), fallbackMaterial);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        String name = gui.getString(path + ".name", " ");
        meta.displayName(guiText(applyPlaceholders(name, player, badgeId, ownedCount, activeCount)));
        meta.lore(renderLore(gui.getStringList(path + ".lore"), player, badgeId, ownedCount, activeCount));
        item.setItemMeta(meta);
        return item;
    }

    private List<Component> renderLore(List<String> lines, Player player, String badgeId, int ownedCount, int activeCount) {
        List<Component> result = new ArrayList<>();
        for (String line : lines) {
            result.add(guiText(applyPlaceholders(line, player, badgeId, ownedCount, activeCount)));
        }
        return result;
    }

    private Component guiText(String text) {
        return ColorUtil.component(text).decoration(TextDecoration.ITALIC, false);
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
            boolean active = badgeService.getActiveBadgeIds(player).contains(badgeId);
            replacements.put("id", badgeId);
            replacements.put("name", badgeService.getBadgeName(badgeId));
            replacements.put("remaining", badgeService.formatRemaining(player, badgeId));
            replacements.put("state", active ? "active" : "inactive");
            replacements.put("active", Boolean.toString(active));
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

    private int inventorySize(boolean emptyState) {
        String path = emptyState ? "empty-state.size" : "menu.size";
        int fallback = emptyState ? 45 : 54;
        int requested = configManager.gui().getInt(path, fallback);
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
