package com.slyph.cloverbadges.smoke;

import com.slyph.cloverbadges.CloverBadges;
import com.slyph.cloverbadges.api.BadgeApi;
import com.slyph.cloverbadges.command.BadgeCommand;
import com.slyph.cloverbadges.config.ConfigManager;
import com.slyph.cloverbadges.gui.BadgeMenuHolder;
import com.slyph.cloverbadges.gui.BadgeMenuManager;
import com.slyph.cloverbadges.gui.MenuPage;
import com.slyph.cloverbadges.head.CustomHeadService;
import com.slyph.cloverbadges.messagecolor.PlayerMessageColorService;
import com.slyph.cloverbadges.nametag.NametagService;
import com.slyph.cloverbadges.nametag.render.ArmorStandNametagRenderer;
import com.slyph.cloverbadges.nametag.render.NametagRenderer;
import com.slyph.cloverbadges.nametag.render.TextDisplayNametagRenderer;
import com.slyph.cloverbadges.nicknamecolor.PlayerNicknameColorService;
import com.slyph.cloverbadges.player.PlayerBadgeService;
import com.slyph.cloverbadges.util.ColorUtil;
import com.slyph.cloverbadges.util.DurationParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class CloverBadgesSmokeTest extends JavaPlugin {
    private static final UUID PLAYER_ID = UUID.fromString("d5e8e334-5159-41de-b95e-41ab696f1103");
    private static final UUID EXPIRING_ID = UUID.fromString("e60c9a38-32e1-413c-9daf-e2ddc3e28b03");
    private final List<String> checks = new ArrayList<>();
    private CloverBadges plugin;
    private PlayerBadgeService badges;
    private PlayerNicknameColorService nicknames;
    private PlayerMessageColorService messages;
    private BadgeMenuManager menus;
    private Inventory openedInventory;

    @Override
    public void onEnable() {
        if (!Boolean.getBoolean("cloverbadges.smoke")) {
            getLogger().severe("This test plugin requires a disposable server and -Dcloverbadges.smoke=true");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        Bukkit.getScheduler().runTask(this, this::runChecks);
    }

    private void runChecks() {
        YamlConfiguration report = new YamlConfiguration();
        String phase = System.getProperty("cloverbadges.smoke.phase", "exercise");
        try {
            plugin = (CloverBadges) Bukkit.getPluginManager().getPlugin("CloverBadges");
            require(plugin != null && plugin.isEnabled(), "plugin enabled");
            require(plugin.getDescription().getAuthors().contains("slyph"), "plugin author");
            require("1.16".equals(plugin.getDescription().getAPIVersion()), "legacy api-version preserved");
            for (String name : System.getProperty("cloverbadges.smoke.plugins", "").split(",")) {
                if (!name.isBlank()) {
                    require(Bukkit.getPluginManager().isPluginEnabled(name), name + " enabled");
                }
            }
            badges = (PlayerBadgeService) plugin.getBadgeApi();
            BadgeCommand command = (BadgeCommand) plugin.getCommand("badge").getExecutor();
            nicknames = field(command, "paintService", PlayerNicknameColorService.class);
            messages = field(command, "messageColorService", PlayerMessageColorService.class);
            menus = field(command, "menuManager", BadgeMenuManager.class);
            require(Bukkit.getServicesManager().load(BadgeApi.class) == badges, "Bukkit service registration");

            if (phase.equals("restore")) {
                verifyRestoredState();
            } else if (phase.equals("exercise")) {
                exerciseState();
                testMenus();
                testHeads();
                testRenderers();
                testCompletion();
                testReload();
            } else {
                throw new IllegalArgumentException("Unknown test phase: " + phase);
            }
            testColors();
            testPlaceholders();
            report.set("passed", true);
            getLogger().info("SMOKE PASS " + phase + " " + checks.size() + " checks");
        } catch (Throwable failure) {
            report.set("passed", false);
            report.set("failure", failure.toString());
            getLogger().log(Level.SEVERE, "SMOKE FAIL " + phase, failure);
        } finally {
            report.set("phase", phase);
            report.set("server", Bukkit.getVersion());
            report.set("plugin-version", plugin == null ? "" : plugin.getPluginMeta().getVersion());
            report.set("checks", checks);
            try {
                Files.createDirectories(getDataFolder().toPath());
                report.save(getDataFolder().toPath().resolve(phase + ".yml").toFile());
            } catch (Exception failure) {
                getLogger().log(Level.SEVERE, "Failed to write test report", failure);
            }
            Bukkit.shutdown();
        }
    }

    private void exerciseState() throws Exception {
        Player player = player(PLAYER_ID, Set.of("*"));
        String badge = badges.allBadgeIds().iterator().next();
        String nickname = nicknames.allColorIds().getFirst();
        String message = messages.allColorIds().getFirst();
        DurationParser.ParsedDuration permanent = DurationParser.parse("permanent").orElseThrow();
        require(badges.grant(player, badge, permanent), "grant badge");
        require(badges.select(player, badge), "select badge");
        require(nicknames.grant(player, nickname, permanent), "grant nickname color");
        require(nicknames.select(player, nickname), "select nickname color");
        require(messages.grant(player, message, permanent), "grant message color");
        require(messages.select(player, message), "select message color");

        YamlConfiguration state = new YamlConfiguration();
        state.set("badge", badge);
        state.set("nickname", nickname);
        state.set("message", message);
        Files.createDirectories(getDataFolder().toPath());
        state.save(getDataFolder().toPath().resolve("state.yml").toFile());

        Player expiring = player(EXPIRING_ID, Set.of("*"));
        DurationParser.ParsedDuration shortGrant = new DurationParser.ParsedDuration(false, 1L);
        require(badges.grant(expiring, badge, shortGrant), "grant temporary badge");
        require(nicknames.grant(expiring, nickname, shortGrant), "grant temporary nickname color");
        require(messages.grant(expiring, message, shortGrant), "grant temporary message color");
        require(badges.revoke(player, badge), "revoke badge");
        require(!badges.hasBadge(player, badge), "badge revoke applied");
        require(badges.grant(player, badge, permanent) && badges.select(player, badge), "restore permanent badge");
        require(nicknames.revoke(player, nickname), "revoke nickname color");
        require(nicknames.selectedId(player).isEmpty(), "nickname revoke clears selection");
        require(nicknames.grant(player, nickname, permanent) && nicknames.select(player, nickname), "restore permanent nickname color");
        require(messages.revoke(player, message), "revoke message color");
        require(messages.selectedId(player).isEmpty(), "message revoke clears selection");
        require(messages.grant(player, message, permanent) && messages.select(player, message), "restore permanent message color");
    }

    private void verifyRestoredState() {
        YamlConfiguration state = YamlConfiguration.loadConfiguration(getDataFolder().toPath().resolve("state.yml").toFile());
        Player player = player(PLAYER_ID, Set.of("*"));
        String badge = state.getString("badge");
        String nickname = state.getString("nickname");
        String message = state.getString("message");
        require(badges.hasBadge(player, badge), "badge ownership restored after restart");
        require(badges.getActiveBadgeIds(player).contains(badge), "badge selection restored after restart");
        require(nicknames.hasColor(player, nickname), "nickname ownership restored after restart");
        require(nicknames.selectedId(player).orElseThrow().equals(nickname), "nickname selection restored after restart");
        require(messages.hasColor(player, message), "message ownership restored after restart");
        require(messages.selectedId(player).orElseThrow().equals(message), "message selection restored after restart");
        Player expiring = player(EXPIRING_ID, Set.of("*"));
        badges.cleanupExpired();
        nicknames.cleanupExpired();
        messages.cleanupExpired();
        require(!badges.hasBadge(expiring, badge), "temporary badge expired");
        require(!nicknames.hasColor(expiring, nickname), "temporary nickname color expired");
        require(!messages.hasColor(expiring, message), "temporary message color expired");
    }

    private void testMenus() throws Exception {
        Player player = player(PLAYER_ID, Set.of("*"));
        menus.open(player);
        require(openedInventory != null, "badge inventory created");
        require(((BadgeMenuHolder) openedInventory.getHolder()).page() == MenuPage.BADGES, "badge menu page");
        verifyItems(openedInventory);
        menus.handleClick(player, (BadgeMenuHolder) openedInventory.getHolder(), 3, ClickType.LEFT);
        require(((BadgeMenuHolder) openedInventory.getHolder()).page() == MenuPage.NICKNAME_COLORS, "nickname menu page");
        verifyItems(openedInventory);
        menus.handleClick(player, (BadgeMenuHolder) openedInventory.getHolder(), 5, ClickType.LEFT);
        require(((BadgeMenuHolder) openedInventory.getHolder()).page() == MenuPage.MESSAGE_COLORS, "message menu page");
        verifyItems(openedInventory);
    }

    private void verifyItems(Inventory inventory) {
        int count = 0;
        for (ItemStack item : inventory.getContents()) {
            if (item != null && !item.isEmpty()) {
                ItemStack restored = ItemStack.deserializeBytes(item.serializeAsBytes());
                boolean preserved = item.getType() == restored.getType() && item.getAmount() == restored.getAmount()
                        && Objects.equals(item.getItemMeta().displayName(), restored.getItemMeta().displayName())
                        && Objects.equals(item.getItemMeta().lore(), restored.getItemMeta().lore());
                if (!preserved) {
                    throw new IllegalStateException("Menu metadata changed: " + item.serialize() + " -> " + restored.serialize());
                }
                require(true, "menu item round trip " + count++);
            }
        }
        require(count > 0, "menu contains items");
    }

    private void testHeads() throws Exception {
        CustomHeadService heads = field(menus, "customHeadService", CustomHeadService.class);
        String hash = "a".repeat(64);
        String url = "https://textures.minecraft.net/texture/" + hash;
        String value = Base64.getEncoder().encodeToString(("{\"textures\":{\"SKIN\":{\"url\":\"" + url + "\"}}}").getBytes(StandardCharsets.UTF_8));
        for (String input : List.of(hash, url, value)) {
            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) item.getItemMeta();
            heads.apply(meta, "", input);
            require(meta.getPlayerProfile() != null, "custom head profile created");
            require(url.equals(meta.getPlayerProfile().getTextures().getSkin().toString()), "custom head texture preserved");
            item.setItemMeta(meta);
            ItemStack restored = ItemStack.deserializeBytes(item.serializeAsBytes());
            SkullMeta restoredMeta = (SkullMeta) restored.getItemMeta();
            require(url.equals(restoredMeta.getPlayerProfile().getTextures().getSkin().toString()), "custom head survives item serialization");
        }
    }

    private void testRenderers() throws Exception {
        Player owner = serverPlayer();
        World world = owner.getWorld();
        world.getChunkAt(owner.getLocation());
        Set<UUID> before = new HashSet<>();
        world.getEntities().forEach(entity -> before.add(entity.getUniqueId()));
        if (Boolean.getBoolean("cloverbadges.smoke.allow-renderer-fallback")) {
            NametagService service = new NametagService(plugin, badges, nicknames, null);
            service.start();
            NametagRenderer renderer = null;
            try {
                Method create = NametagService.class.getDeclaredMethod("createRenderer", Player.class);
                create.setAccessible(true);
                renderer = (NametagRenderer) create.invoke(service, owner);
                require(renderer != null, "AUTO nametag renderer created");
                renderer.setText(ColorUtil.component("&FF0088slyph"));
                require(renderer.sync(owner), "AUTO nametag sync: " + renderer.type());
            } finally {
                if (renderer != null) renderer.remove();
                service.shutdown();
            }
        } else {
            TextDisplayNametagRenderer display = new TextDisplayNametagRenderer(plugin, owner, true, false, false, 1024, 1.0F);
            try {
                display.setText(ColorUtil.component("&FF0088slyph"));
                TextDisplay entity = field(display, "display", TextDisplay.class);
                require(display.sync(owner), "TextDisplay attachment and sync: valid=" + entity.isValid() + ", dead=" + entity.isDead()
                        + ", world=" + entity.getWorld().equals(owner.getWorld()) + ", vehicle=" + (entity.getVehicle() == owner));
            } finally {
                display.remove();
            }
        }
        ArmorStandNametagRenderer stand = new ArmorStandNametagRenderer(plugin, owner, 1.0D);
        try {
            stand.setText(ColorUtil.component("&55FF88slyph"));
            require(stand.sync(owner), "ArmorStand teleport and sync");
        } finally {
            stand.remove();
        }
        require(world.getEntities().stream().allMatch(entity -> before.contains(entity.getUniqueId())), "nametag entities removed");
    }

    private Player serverPlayer() throws Exception {
        Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        World world = Bukkit.getWorlds().getFirst();
        Object level = world.getClass().getMethod("getHandle").invoke(world);
        Class<?> profileType = Class.forName("com.mojang.authlib.GameProfile");
        Object profile = profileType.getConstructor(UUID.class, String.class).newInstance(UUID.randomUUID(), "SmokeOwner");
        Class<?> infoType = Class.forName("net.minecraft.server.level.ClientInformation");
        Object info = infoType.getMethod("createDefault").invoke(null);
        Class<?> playerType = Class.forName("net.minecraft.server.level.ServerPlayer");
        Object handle = playerType.getConstructor(Class.forName("net.minecraft.server.MinecraftServer"),
                Class.forName("net.minecraft.server.level.ServerLevel"), profileType, infoType).newInstance(server, level, profile, info);
        org.bukkit.Location location = world.getSpawnLocation();
        playerType.getMethod("setPos", double.class, double.class, double.class)
                .invoke(handle, location.getX(), location.getY(), location.getZ());
        Class<?> connectionType = Class.forName("net.minecraft.network.Connection");
        Class<?> flowType = Class.forName("net.minecraft.network.protocol.PacketFlow");
        Object flow = flowType.getField("SERVERBOUND").get(null);
        Object connection = connectionType.getConstructor(flowType).newInstance(flow);
        Class<?> cookieType = Class.forName("net.minecraft.server.network.CommonListenerCookie");
        Object cookie = cookieType.getMethod("createInitial", profileType, boolean.class).invoke(null, profile, false);
        Class<?> listenerType = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
        Object listener = listenerType.getConstructor(Class.forName("net.minecraft.server.MinecraftServer"), connectionType,
                playerType, cookieType).newInstance(server, connection, handle, cookie);
        playerType.getField("connection").set(handle, listener);
        level.getClass().getMethod("addNewPlayer", playerType).invoke(level, handle);
        return (Player) playerType.getMethod("getBukkitEntity").invoke(handle);
    }

    private void testCompletion() {
        PluginCommand command = plugin.getCommand("badge");
        CommandSender noPermissions = sender(Set.of());
        require(command.getTabCompleter().onTabComplete(noPermissions, command, "badge", new String[]{""}).isEmpty(), "no command suggestions without use permission");
        CommandSender user = sender(Set.of("cloverbadges.use"));
        require(command.getTabCompleter().onTabComplete(user, command, "badge", new String[]{""}).isEmpty(), "admin suggestions hidden from regular players");
        CommandSender reloader = sender(Set.of("cloverbadges.use", "cloverbadges.admin.reload"));
        require(command.getTabCompleter().onTabComplete(reloader, command, "badge", new String[]{""}).equals(List.of("reload")), "suggestions restricted to granted permission");
        require(command.getTabCompleter().onTabComplete(reloader, command, "badge", new String[]{"give", ""}).isEmpty(), "unauthorized give arguments hidden");
        require(command.getExecutor().onCommand(noPermissions, command, "badge", new String[]{"reload"}), "unauthorized command handled");
    }

    private void testReload() throws Exception {
        ConfigManager config = field(menus, "configManager", ConfigManager.class);
        config.gui().set("menu.title", "&FF0088Smoke Test");
        config.gui().save(plugin.getDataFolder().toPath().resolve("gui.yml").toFile());
        require(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "badge reload"), "reload command dispatched");
        require("&FF0088Smoke Test".equals(config.gui().getString("menu.title")), "custom GUI title survives reload");
        require(nicknames.selectedId(player(PLAYER_ID, Set.of("*"))).isPresent(), "nickname selection survives reload");
        require(messages.selectedId(player(PLAYER_ID, Set.of("*"))).isPresent(), "message selection survives reload");
    }

    private void testColors() {
        Component expected = Component.text("slyph", TextColor.color(0xFF0088));
        require(expected.equals(ColorUtil.component("&FF0088slyph")), "simple HEX color via Adventure");
        Player player = player(PLAYER_ID, Set.of("*"));
        String nickname = nicknames.coloredNicknameLegacy(player);
        require(nickname.startsWith("§r") && nickname.endsWith("§r"), "nickname formatting isolation");
        require(PlainTextComponentSerializer.plainText().serialize(ColorUtil.component(ColorUtil.toAmpersand(nickname))).equals("SmokePlayer"), "nickname text preserved");
    }

    private void testPlaceholders() {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            require(plugin.isEnabled(), "PlaceholderAPI remains optional");
            return;
        }
        Player player = player(PLAYER_ID, Set.of("*"));
        require(!me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%cloverbadges_badge_id%").contains("%"), "badge placeholder resolved");
        require(me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%cloverbadges_nickname_color_id%").equals(nicknames.selectedId(player).orElseThrow()), "nickname placeholder resolved");
        require(me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%cloverbadges_message_color_id%").equals(messages.selectedId(player).orElseThrow()), "message placeholder resolved");
        require(me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%cloverbadges_unknown%").equals("%cloverbadges_unknown%"), "unknown placeholder preserved");
    }

    private Player player(UUID id, Set<String> permissions) {
        return proxy(Player.class, id, permissions);
    }

    private CommandSender sender(Set<String> permissions) {
        return proxy(CommandSender.class, PLAYER_ID, permissions);
    }

    private <T> T proxy(Class<T> type, UUID id, Set<String> permissions) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "getUniqueId" -> id;
                case "getName" -> "SmokePlayer";
                case "getFirstPlayed" -> 1L;
                case "hasPlayedBefore" -> true;
                case "hasPermission", "isPermissionSet" -> permissions.contains("*") || permissions.contains(args[0].toString());
                case "getServer" -> Bukkit.getServer();
                case "getWorld" -> Bukkit.getWorlds().getFirst();
                case "getLocation" -> Bukkit.getWorlds().getFirst().getSpawnLocation();
                case "openInventory" -> {
                    openedInventory = (Inventory) args[0];
                    yield null;
                }
                case "equals" -> proxy == args[0];
                case "hashCode" -> id.hashCode();
                case "toString" -> "CloverBadgesSmokePlayer";
                default -> {
                    Class<?> result = method.getReturnType();
                    if (result == boolean.class) yield false;
                    if (result == int.class) yield 0;
                    if (result == long.class) yield 0L;
                    if (result == float.class) yield 0.0F;
                    if (result == double.class) yield 0.0D;
                    yield null;
                }
            };
        }));
    }

    private Object fieldPresent(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private <T> T field(Object target, String name, Class<T> type) {
        return type.cast(fieldPresent(target, name));
    }

    private void require(boolean passed, String label) {
        if (!passed) {
            throw new IllegalStateException(label);
        }
        checks.add(label);
    }
}
