package com.slyph.cloverbadges.gui;

import com.slyph.cloverbadges.config.ConfigManager;
import com.slyph.cloverbadges.gui.action.BadgeActionExecutor;
import com.slyph.cloverbadges.head.CustomHeadService;
import com.slyph.cloverbadges.nicknamecolor.NicknameColorDefinition;
import com.slyph.cloverbadges.nicknamecolor.PlayerNicknameColorService;
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
import java.util.function.Function;

public final class BadgeMenuManager {
    private static final List<Integer> DEFAULT_SLOTS = List.of(20, 21, 22, 23, 24, 29, 30, 31, 32, 33);
    private final ConfigManager configManager;
    private final PlayerBadgeService badgeService;
    private final PlayerNicknameColorService paintService;
    private final BadgeActionExecutor actionExecutor;
    private final CustomHeadService customHeadService;

    public BadgeMenuManager(ConfigManager configManager, PlayerBadgeService badgeService, PlayerNicknameColorService paintService, BadgeActionExecutor actionExecutor, CustomHeadService customHeadService) {
        this.configManager = configManager;
        this.badgeService = badgeService;
        this.paintService = paintService;
        this.actionExecutor = actionExecutor;
        this.customHeadService = customHeadService;
    }

    public void open(Player player) {
        open(player, MenuPage.BADGES);
    }

    private void open(Player player, MenuPage page) {
        int badgeCount = badgeService.getOwnedBadgeIds(player).size();
        int activeCount = badgeService.getActiveBadgeIds(player).size();
        int size = page == MenuPage.BADGES ? badgeInventorySize(badgeCount == 0) : inventorySize("nickname-colors.size", 54);
        String titlePath = page == MenuPage.BADGES ? "menu.title" : "nickname-colors.title";
        String fallback = page == MenuPage.BADGES ? "&8Значки" : "&8";
        String title = pagePlaceholders(placeholders(configManager.gui().getString(titlePath, fallback), player, null, badgeCount, activeCount), page);
        BadgeMenuHolder holder = new BadgeMenuHolder(player.getUniqueId(), page);
        Inventory inventory = Bukkit.createInventory(holder, size, ColorUtil.legacySection(title));
        holder.inventory(inventory);
        render(holder, player);
        player.openInventory(inventory);
    }

    public void handleClick(Player player, BadgeMenuHolder holder, int rawSlot, ClickType clickType) {
        if (!holder.playerId().equals(player.getUniqueId()) || holder.getInventory() == null) {
            return;
        }
        Inventory inventory = holder.getInventory();
        int switcher = validSlot(configManager.gui().getInt("page-switcher.slot", 4), inventory.getSize(), 4);
        if (rawSlot == switcher) {
            open(player, holder.page().opposite());
            return;
        }
        if (holder.page() == MenuPage.NICKNAME_COLORS) {
            int previous = validSlot(configManager.gui().getInt("nickname-colors.pagination.previous.slot", 47), inventory.getSize(), 47);
            int next = validSlot(configManager.gui().getInt("nickname-colors.pagination.next.slot", 51), inventory.getSize(), 51);
            int pages = paintPageCount(player, inventory.getSize());
            if (rawSlot == previous && holder.nicknameColorPage() > 0) {
                holder.nicknameColorPage(holder.nicknameColorPage() - 1);
                render(holder, player);
                return;
            }
            if (rawSlot == next && holder.nicknameColorPage() + 1 < pages) {
                holder.nicknameColorPage(holder.nicknameColorPage() + 1);
                render(holder, player);
                return;
            }
            int clear = validSlot(configManager.gui().getInt("nickname-colors.clear.slot", 49), inventory.getSize(), 49);
            if (rawSlot == clear) {
                paintService.clear(player);
                render(holder, player);
                return;
            }
            String paintId = holder.nicknameColorAt(rawSlot);
            if (paintId != null && paintService.select(player, paintId)) {
                render(holder, player);
            }
            return;
        }
        int clear = validSlot(configManager.gui().getInt("clear-all.slot", 50), inventory.getSize(), 50);
        if (rawSlot == clear && !badgeService.getOwnedBadgeIds(player).isEmpty()) {
            badgeService.clearSelection(player);
            render(holder, player);
            return;
        }
        String badgeId = holder.badgeAt(rawSlot);
        if (badgeId == null) {
            return;
        }
        int owned = badgeService.getOwnedBadgeIds(player).size();
        int active = badgeService.getActiveBadgeIds(player).size();
        boolean enabled = badgeService.getActiveBadgeIds(player).contains(badgeId);
        BadgeActionExecutor.Result result = actionExecutor.execute(player, badgeId, badgeActions(badgeId, enabled, clickType), value -> placeholders(value, player, badgeId, owned, active));
        if (result.close()) {
            player.closeInventory();
        } else if (result.refresh()) {
            render(holder, player);
        }
    }

    private void render(BadgeMenuHolder holder, Player player) {
        Inventory inventory = holder.getInventory();
        if (inventory == null) {
            return;
        }
        inventory.clear();
        holder.clearSlots();
        int owned = badgeService.getOwnedBadgeIds(player).size();
        int active = badgeService.getActiveBadgeIds(player).size();
        renderSwitcher(inventory, player, holder.page(), owned, active);
        if (holder.page() == MenuPage.NICKNAME_COLORS) {
            renderPaints(holder, player);
        } else {
            renderBadges(holder, player);
        }
    }

    private void renderBadges(BadgeMenuHolder holder, Player player) {
        Inventory inventory = holder.getInventory();
        List<String> owned = new ArrayList<>(badgeService.getOwnedBadgeIds(player));
        if (owned.isEmpty()) {
            renderEmptyState(inventory, player);
            return;
        }
        List<Integer> slots = slots("menu.badge-slots", inventory.getSize());
        Set<String> active = new LinkedHashSet<>(badgeService.getActiveBadgeIds(player));
        int visible = Math.min(Math.min(owned.size(), badgeService.maxOwnedBadges()), slots.size());
        for (int index = 0; index < slots.size(); index++) {
            int slot = slots.get(index);
            if (index < visible) {
                String id = owned.get(index);
                inventory.setItem(slot, badgeItem(player, id, active.contains(id), owned.size(), active.size()));
                holder.badgeSlot(slot, id);
            } else {
                inventory.setItem(slot, configuredItem("empty-slot", player, null, owned.size(), active.size(), Material.LIGHT_GRAY_STAINED_GLASS_PANE));
            }
        }
        inventory.setItem(validSlot(configManager.gui().getInt("info-book.slot", 48), inventory.getSize(), 48), configuredItem("info-book", player, null, owned.size(), active.size(), Material.BOOK));
        inventory.setItem(validSlot(configManager.gui().getInt("clear-all.slot", 50), inventory.getSize(), 50), configuredItem("clear-all", player, null, owned.size(), active.size(), Material.BARRIER));
    }

    private void renderPaints(BadgeMenuHolder holder, Player player) {
        Inventory inventory = holder.getInventory();
        List<NicknameColorDefinition> owned = paintService.getOwnedColors(player);
        List<Integer> slots = slots("nickname-colors.color-slots", inventory.getSize());
        String selected = paintService.selectedId(player).orElse("");
        int pageSize = Math.max(1, slots.size());
        int pages = Math.max(1, (owned.size() + pageSize - 1) / pageSize);
        int page = Math.min(holder.nicknameColorPage(), pages - 1);
        holder.nicknameColorPage(page);
        int offset = page * pageSize;

        for (int index = 0; index < slots.size(); index++) {
            int slot = slots.get(index);
            int paintIndex = offset + index;
            if (paintIndex < owned.size()) {
                NicknameColorDefinition definition = owned.get(paintIndex);
                boolean selectedNow = definition.id().equals(selected);
                boolean available = paintService.isAvailable(player, definition);
                inventory.setItem(slot, paintItem(player, definition, selectedNow, available));
                holder.nicknameColorSlot(slot, definition.id());
            } else {
                inventory.setItem(slot, configuredItem("nickname-colors.empty-slot", player, null, owned.size(), selected.isEmpty() ? 0 : 1, Material.LIGHT_GRAY_STAINED_GLASS_PANE));
            }
        }

        int clear = validSlot(configManager.gui().getInt("nickname-colors.clear.slot", 49), inventory.getSize(), 49);
        inventory.setItem(clear, configuredItem("nickname-colors.clear", player, null, owned.size(), selected.isEmpty() ? 0 : 1, Material.GRAY_DYE));

        if (page > 0) {
            int previous = validSlot(configManager.gui().getInt("nickname-colors.pagination.previous.slot", 47), inventory.getSize(), 47);
            inventory.setItem(previous, paginationItem("nickname-colors.pagination.previous", player, page, pages, Material.ARROW));
        }
        if (page + 1 < pages) {
            int next = validSlot(configManager.gui().getInt("nickname-colors.pagination.next.slot", 51), inventory.getSize(), 51);
            inventory.setItem(next, paginationItem("nickname-colors.pagination.next", player, page, pages, Material.ARROW));
        }
    }

    private int paintPageCount(Player player, int inventorySize) {
        int pageSize = Math.max(1, slots("nickname-colors.color-slots", inventorySize).size());
        int owned = paintService.getOwnedColors(player).size();
        return Math.max(1, (owned + pageSize - 1) / pageSize);
    }

    private ItemStack paginationItem(String path, Player player, int page, int pages, Material fallback) {
        YamlConfiguration gui = configManager.gui();
        Material material = material(gui.getString(path + ".material", fallback.name()), fallback);
        ItemStack item = new ItemStack(material);
        item.setAmount(Math.max(1, Math.min(material.getMaxStackSize(), gui.getInt(path + ".amount", 1))));
        ItemMeta meta = item.getItemMeta();
        if (material == Material.PLAYER_HEAD) {
            customHeadService.apply(meta, gui.getString(path + ".head.minecraft-heads", ""), gui.getString(path + ".head.value", ""));
        }
        Function<String, String> replacer = value -> paginationPlaceholders(placeholders(value, player, null, paintService.getOwnedColorIds(player).size(), paintService.selectedId(player).isPresent() ? 1 : 0), page, pages);
        meta.displayName(guiText(replacer.apply(gui.getString(path + ".name", "&7"))));
        List<Component> lore = new ArrayList<>();
        for (String line : gui.getStringList(path + ".lore")) {
            lore.add(guiText(replacer.apply(line)));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack badgeItem(Player player, String id, boolean active, int owned, int activeCount) {
        String base = "badges." + id;
        String state = active ? "active" : "inactive";
        return stateItem(base, "badge", state, Material.PLAYER_HEAD, value -> placeholders(value, player, id, owned, activeCount));
    }

    private ItemStack paintItem(Player player, NicknameColorDefinition definition, boolean selected, boolean available) {
        String state = selected ? "selected" : available ? "available" : "locked";
        return stateItem("nickname-color-items." + definition.id(), "nickname-color", state, Material.PLAYER_HEAD, value -> paintPlaceholders(value, player, definition, selected, available));
    }

    private ItemStack stateItem(String base, String global, String state, Material fallback, Function<String, String> replacer) {
        YamlConfiguration gui = configManager.gui();
        Material material = material(stateString(gui, base, global, state, "material", fallback.name()), fallback);
        ItemStack item = new ItemStack(material);
        item.setAmount(Math.max(1, Math.min(material.getMaxStackSize(), stateInt(gui, base, global, state, "amount", 1))));
        ItemMeta meta = item.getItemMeta();
        if (material == Material.PLAYER_HEAD) {
            customHeadService.apply(meta, stateHead(gui, base, global, state, "minecraft-heads"), stateHead(gui, base, global, state, "value"));
        }
        meta.displayName(guiText(replacer.apply(stateString(gui, base, global, state, "name", "&7"))));
        List<Component> lore = new ArrayList<>();
        for (String line : stateLore(gui, base, global, state)) {
            lore.add(guiText(replacer.apply(line)));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String stateString(YamlConfiguration gui, String base, String global, String state, String key, String fallback) {
        for (String path : List.of(base + "." + key + "-" + state, base + "." + key, global + "." + key + "-" + state, global + "." + key)) {
            if (gui.contains(path)) {
                return gui.getString(path, fallback);
            }
        }
        return fallback;
    }

    private int stateInt(YamlConfiguration gui, String base, String global, String state, String key, int fallback) {
        for (String path : List.of(base + "." + key + "-" + state, base + "." + key, global + "." + key + "-" + state, global + "." + key)) {
            if (gui.contains(path)) {
                return gui.getInt(path, fallback);
            }
        }
        return fallback;
    }

    private String stateHead(YamlConfiguration gui, String base, String global, String state, String key) {
        for (String path : List.of(base + ".head-" + state + "." + key, base + ".head." + key, global + ".head-" + state + "." + key, global + ".head." + key)) {
            if (gui.contains(path)) {
                return gui.getString(path, "");
            }
        }
        return "";
    }

    private List<String> stateLore(YamlConfiguration gui, String base, String global, String state) {
        for (String path : List.of(base + ".lore-" + state, base + ".lore", global + ".lore-" + state, global + ".lore")) {
            if (gui.isList(path)) {
                return gui.getStringList(path);
            }
        }
        return List.of();
    }

    private void renderSwitcher(Inventory inventory, Player player, MenuPage page, int owned, int active) {
        int slot = validSlot(configManager.gui().getInt("page-switcher.slot", 4), inventory.getSize(), 4);
        YamlConfiguration gui = configManager.gui();
        String pageBase = page == MenuPage.BADGES ? "page-switcher.badges-page" : "page-switcher.nickname-colors-page";
        Material material = material(firstString(gui, List.of(pageBase + ".material", "page-switcher.material"), "PLAYER_HEAD"), Material.PLAYER_HEAD);
        ItemStack item = new ItemStack(material);
        item.setAmount(Math.max(1, Math.min(material.getMaxStackSize(), firstInt(gui, List.of(pageBase + ".amount", "page-switcher.amount"), 1))));
        ItemMeta meta = item.getItemMeta();
        if (material == Material.PLAYER_HEAD) {
            customHeadService.apply(meta, firstString(gui, List.of(pageBase + ".head.minecraft-heads", "page-switcher.head.minecraft-heads"), ""), firstString(gui, List.of(pageBase + ".head.value", "page-switcher.head.value"), ""));
        }
        Function<String, String> replacer = value -> pagePlaceholders(placeholders(value, player, null, owned, active), page);
        meta.displayName(guiText(replacer.apply(firstString(gui, List.of(pageBase + ".name", "page-switcher.name"), "&7"))));
        List<String> lines = gui.isList(pageBase + ".lore") ? gui.getStringList(pageBase + ".lore") : gui.getStringList("page-switcher.lore");
        List<Component> lore = new ArrayList<>();
        for (String line : lines) {
            lore.add(guiText(replacer.apply(line)));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }

    private ItemStack configuredItem(String path, Player player, String badgeId, int owned, int active, Material fallback) {
        YamlConfiguration gui = configManager.gui();
        Material material = material(gui.getString(path + ".material", fallback.name()), fallback);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (material == Material.PLAYER_HEAD) {
            customHeadService.apply(meta, gui.getString(path + ".head.minecraft-heads", ""), gui.getString(path + ".head.value", ""));
        }
        meta.displayName(guiText(placeholders(gui.getString(path + ".name", "&7"), player, badgeId, owned, active)));
        List<Component> lore = new ArrayList<>();
        for (String line : gui.getStringList(path + ".lore")) {
            lore.add(guiText(placeholders(line, player, badgeId, owned, active)));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void renderEmptyState(Inventory inventory, Player player) {
        int paper = validSlot(configManager.gui().getInt("empty-state.paper.slot", 20), inventory.getSize(), 20);
        int apple = validSlot(configManager.gui().getInt("empty-state.apple.slot", 24), inventory.getSize(), 24);
        inventory.setItem(paper, configuredItem("empty-state.paper", player, null, 0, 0, Material.PAPER));
        inventory.setItem(apple, configuredItem("empty-state.apple", player, null, 0, 0, Material.APPLE));
    }

    private List<String> badgeActions(String id, boolean active, ClickType clickType) {
        YamlConfiguration gui = configManager.gui();
        String click = clickKey(clickType);
        String state = active ? "actions-active" : "actions-inactive";
        for (String root : List.of("badges." + id, "badge")) {
            for (String path : List.of(root + "." + state + "." + click, root + "." + state + ".any", root + ".actions." + click, root + ".actions.any")) {
                if (gui.contains(path)) {
                    return gui.isList(path) ? gui.getStringList(path) : List.of(gui.getString(path, ""));
                }
            }
        }
        return List.of("[toggle]");
    }

    private String clickKey(ClickType clickType) {
        return switch (clickType) {
            case SHIFT_LEFT -> "shift-left";
            case SHIFT_RIGHT -> "shift-right";
            case LEFT -> "left";
            case RIGHT -> "right";
            case MIDDLE -> "middle";
            case DROP -> "drop";
            case CONTROL_DROP -> "control-drop";
            case DOUBLE_CLICK -> "double";
            case NUMBER_KEY -> "number";
            case SWAP_OFFHAND -> "swap-offhand";
            default -> "any";
        };
    }

    private String placeholders(String input, Player player, String badgeId, int owned, int active) {
        String value = input == null ? "" : input;
        Map<String, String> replacements = new HashMap<>();
        replacements.put("player", player.getName());
        replacements.put("owned_count", Integer.toString(owned));
        replacements.put("active_count", Integer.toString(active));
        replacements.put("max_owned", Integer.toString(badgeService.maxOwnedBadges()));
        replacements.put("max_active", Integer.toString(badgeService.maxVisibleBadges()));
        paintService.selectedId(player).ifPresent(id -> replacements.put("nickname_color", id));
        if (badgeId != null) {
            boolean enabled = badgeService.getActiveBadgeIds(player).contains(badgeId);
            replacements.put("id", badgeId);
            replacements.put("name", badgeService.getBadgeName(badgeId));
            replacements.put("remaining", badgeService.formatRemaining(player, badgeId));
            replacements.put("state", enabled ? "active" : "inactive");
            replacements.put("active", Boolean.toString(enabled));
        }
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            value = value.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return value;
    }

    private String paintPlaceholders(String input, Player player, NicknameColorDefinition definition, boolean selected, boolean available) {
        return (input == null ? "" : input)
                .replace("{color_id}", definition.id())
                .replace("{color_name}", definition.name())
                .replace("{preview}", paintService.preview(player, definition))
                .replace("{remaining}", paintService.formatRemaining(player, definition.id()))
                .replace("{selected}", Boolean.toString(selected))
                .replace("{available}", Boolean.toString(available))
                .replace("{state}", selected ? "selected" : available ? "available" : "locked")
                .replace("{player}", player.getName());
    }

    private String pagePlaceholders(String input, MenuPage page) {
        String current = page == MenuPage.BADGES ? "Значки" : "Покраски никнеймов";
        String target = page == MenuPage.BADGES ? "Покраски никнеймов" : "Значки";
        return (input == null ? "" : input).replace("{page}", current).replace("{target_page}", target);
    }

    private String paginationPlaceholders(String input, int page, int pages) {
        return (input == null ? "" : input)
                .replace("{page}", Integer.toString(page + 1))
                .replace("{pages}", Integer.toString(pages))
                .replace("{previous_page}", Integer.toString(Math.max(1, page)))
                .replace("{next_page}", Integer.toString(Math.min(pages, page + 2)));
    }

    private List<Integer> slots(String path, int inventorySize) {
        List<Integer> configured = configManager.gui().getIntegerList(path);
        List<Integer> source = configured.isEmpty() ? DEFAULT_SLOTS : configured;
        LinkedHashSet<Integer> result = new LinkedHashSet<>();
        for (int slot : source) {
            if (slot >= 0 && slot < inventorySize) {
                result.add(slot);
            }
            if (result.size() >= 10) {
                break;
            }
        }
        if (result.size() < 10) {
            for (int slot : DEFAULT_SLOTS) {
                if (slot >= 0 && slot < inventorySize) {
                    result.add(slot);
                }
                if (result.size() >= 10) {
                    break;
                }
            }
        }
        return List.copyOf(result);
    }

    private String firstString(YamlConfiguration gui, List<String> paths, String fallback) {
        for (String path : paths) {
            if (gui.contains(path)) {
                return gui.getString(path, fallback);
            }
        }
        return fallback;
    }

    private int firstInt(YamlConfiguration gui, List<String> paths, int fallback) {
        for (String path : paths) {
            if (gui.contains(path)) {
                return gui.getInt(path, fallback);
            }
        }
        return fallback;
    }

    private Component guiText(String text) {
        return ColorUtil.component(text).decoration(TextDecoration.ITALIC, false);
    }

    private int badgeInventorySize(boolean emptyState) {
        return inventorySize(emptyState ? "empty-state.size" : "menu.size", emptyState ? 45 : 54);
    }

    private int inventorySize(String path, int fallback) {
        int requested = configManager.gui().getInt(path, fallback);
        int clamped = Math.max(9, Math.min(54, requested));
        return Math.min(54, ((clamped + 8) / 9) * 9);
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
