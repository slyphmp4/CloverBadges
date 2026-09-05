package com.slyph.cloverbadges.nametag;

import com.slyph.cloverbadges.CloverBadges;
import com.slyph.cloverbadges.nametag.integration.TabNametagBridge;
import com.slyph.cloverbadges.nametag.render.ArmorStandNametagRenderer;
import com.slyph.cloverbadges.nametag.render.NametagRenderer;
import com.slyph.cloverbadges.nametag.render.TextDisplayNametagRenderer;
import com.slyph.cloverbadges.nicknamecolor.NicknameColorDefinition;
import com.slyph.cloverbadges.nicknamecolor.PlayerNicknameColorService;
import com.slyph.cloverbadges.player.PlayerBadgeService;
import com.slyph.cloverbadges.util.ColorUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class NametagService {
    private final CloverBadges plugin;
    private final PlayerBadgeService badgeService;
    private final PlayerNicknameColorService nicknameColorService;
    private final TabNametagBridge tabBridge;
    private final Map<UUID, NametagRenderer> renderers = new HashMap<>();
    private final Map<UUID, Component> lastText = new HashMap<>();

    private BukkitTask task;
    private long tickCounter;
    private boolean enabled;
    private boolean onlyForGradients;
    private boolean hideVanillaWithTab;
    private boolean useTabPrefixSuffix;
    private boolean armorStandFallback;
    private long refreshIntervalTicks;
    private RendererMode rendererMode;
    private boolean textDisplayShadowed;
    private boolean textDisplaySeeThrough;
    private boolean textDisplayDefaultBackground;
    private int textDisplayLineWidth;
    private float textDisplayViewRange;
    private double armorStandYOffset;
    private String fallbackPrefix;
    private String fallbackSuffix;
    private String fallbackBadgeSeparator;
    private boolean textDisplayBroken;
    private boolean textDisplayWarningShown;
    private boolean armorStandWarningShown;
    private boolean tabWarningShown;

    public NametagService(
            CloverBadges plugin,
            PlayerBadgeService badgeService,
            PlayerNicknameColorService nicknameColorService,
            TabNametagBridge tabBridge
    ) {
        this.plugin = plugin;
        this.badgeService = badgeService;
        this.nicknameColorService = nicknameColorService;
        this.tabBridge = tabBridge;
    }

    public void start() {
        loadSettings();
        schedule();
    }

    public void reload() {
        stopTask();
        removeAll(true);
        textDisplayBroken = false;
        textDisplayWarningShown = false;
        armorStandWarningShown = false;
        tabWarningShown = false;
        loadSettings();
        schedule();
    }

    public void shutdown() {
        stopTask();
        removeAll(true);
    }

    public void handleJoin(Player player) {
        if (tabBridge != null) {
            tabBridge.forget(player);
        }
    }

    public void handleQuit(Player player) {
        removeRenderer(player.getUniqueId());
        if (tabBridge != null) {
            tabBridge.forget(player);
        }
    }

    private void loadSettings() {
        enabled = plugin.getConfig().getBoolean("nametag.enabled", true);
        onlyForGradients = plugin.getConfig().getBoolean("nametag.only-for-gradients", true);
        hideVanillaWithTab = plugin.getConfig().getBoolean("nametag.hide-vanilla-with-tab", true);
        useTabPrefixSuffix = plugin.getConfig().getBoolean("nametag.use-tab-prefix-suffix", true);
        armorStandFallback = plugin.getConfig().getBoolean("nametag.armor-stand-fallback", true);
        refreshIntervalTicks = Math.max(1L, plugin.getConfig().getLong("nametag.refresh-interval-ticks", 10L));
        rendererMode = RendererMode.parse(plugin.getConfig().getString("nametag.renderer", "AUTO"));
        textDisplayShadowed = plugin.getConfig().getBoolean("nametag.text-display.shadowed", true);
        textDisplaySeeThrough = plugin.getConfig().getBoolean("nametag.text-display.see-through", false);
        textDisplayDefaultBackground = plugin.getConfig().getBoolean("nametag.text-display.default-background", false);
        textDisplayLineWidth = Math.max(1, plugin.getConfig().getInt("nametag.text-display.line-width", 1024));
        textDisplayViewRange = (float) Math.max(0.1D, plugin.getConfig().getDouble("nametag.text-display.view-range", 1.0D));
        armorStandYOffset = plugin.getConfig().getDouble("nametag.armor-stand.y-offset", 1.0D);
        fallbackPrefix = plugin.getConfig().getString("nametag.fallback.prefix", "");
        fallbackSuffix = plugin.getConfig().getString("nametag.fallback.suffix", "");
        fallbackBadgeSeparator = plugin.getConfig().getString("nametag.fallback.badge-separator", " ");
    }

    private void schedule() {
        if (!enabled) {
            return;
        }
        tickCounter = 0L;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        tickCounter++;
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        Set<UUID> onlineIds = new HashSet<>();
        for (Player player : onlinePlayers) {
            onlineIds.add(player.getUniqueId());
        }

        Set<UUID> stale = new HashSet<>(renderers.keySet());
        stale.removeAll(onlineIds);
        for (UUID uuid : stale) {
            removeRenderer(uuid);
        }

        boolean refresh = tickCounter == 1L || tickCounter % refreshIntervalTicks == 0L;
        for (Player player : onlinePlayers) {
            processPlayer(player, onlinePlayers, onlineIds, refresh);
        }
    }

    private void processPlayer(Player player, List<Player> onlinePlayers, Set<UUID> onlineIds, boolean refresh) {
        if (!shouldRender(player)) {
            removeRenderer(player.getUniqueId());
            restoreVanilla(player);
            return;
        }

        if (hideVanillaWithTab) {
            if (tabBridge == null || !tabBridge.hideVanilla(player)) {
                removeRenderer(player.getUniqueId());
                if (!tabWarningShown) {
                    plugin.getLogger().warning("Gradient nametag is waiting for TAB NameTagManager to become available");
                    tabWarningShown = true;
                }
                return;
            }
            tabWarningShown = false;
        }

        UUID uuid = player.getUniqueId();
        NametagRenderer renderer = renderers.get(uuid);
        boolean created = false;

        if (renderer != null && !renderer.sync(player)) {
            removeRenderer(uuid);
            renderer = null;
        }

        if (renderer == null) {
            renderer = createRenderer(player);
            if (renderer == null) {
                return;
            }
            renderers.put(uuid, renderer);
            created = true;
        }

        if (created || refresh) {
            Component text = buildText(player);
            Component previous = lastText.get(uuid);
            if (created || previous == null || !previous.equals(text)) {
                renderer = updateTextWithFallback(player, renderer, text);
                if (renderer == null) {
                    return;
                }
                lastText.put(uuid, text);
            }
            syncVisibility(player, renderer, onlinePlayers, onlineIds);
        }
    }

    private boolean shouldRender(Player player) {
        Optional<String> selected = nicknameColorService.selectedId(player);
        if (selected.isEmpty()) {
            return false;
        }
        Optional<NicknameColorDefinition> definition = nicknameColorService.getDefinition(selected.get());
        if (definition.isEmpty()) {
            return false;
        }
        return !onlyForGradients || definition.get().hasGradient();
    }

    private Component buildText(Player player) {
        Component nickname = ColorUtil.component(ColorUtil.toAmpersand(nicknameColorService.coloredNicknameLegacy(player)));

        if (useTabPrefixSuffix && tabBridge != null && tabBridge.isReady(player)) {
            return tabBridge.prefix(player)
                    .append(nickname)
                    .append(tabBridge.suffix(player));
        }

        Component result = ColorUtil.component(fallbackPrefix);
        if (!badgeService.getActiveBadgeIds(player).isEmpty()) {
            result = result
                    .append(badgeService.getActiveBadgeComponent(player))
                    .append(ColorUtil.component(fallbackBadgeSeparator));
        }
        return result
                .append(nickname)
                .append(ColorUtil.component(fallbackSuffix));
    }

    private NametagRenderer createRenderer(Player player) {
        if ((rendererMode == RendererMode.AUTO || rendererMode == RendererMode.TEXT_DISPLAY) && !textDisplayBroken) {
            try {
                return new TextDisplayNametagRenderer(
                        plugin,
                        player,
                        textDisplayShadowed,
                        textDisplaySeeThrough,
                        textDisplayDefaultBackground,
                        textDisplayLineWidth,
                        textDisplayViewRange
                );
            } catch (Throwable throwable) {
                textDisplayBroken = true;
                if (!textDisplayWarningShown) {
                    plugin.getLogger().warning("TextDisplay nametag renderer is unavailable: " + describe(throwable));
                    if (armorStandFallback) {
                        plugin.getLogger().warning("CloverBadges will use the ArmorStand nametag fallback");
                    }
                    textDisplayWarningShown = true;
                }
                if (rendererMode == RendererMode.TEXT_DISPLAY && !armorStandFallback) {
                    return null;
                }
            }
        }

        if (rendererMode == RendererMode.ARMOR_STAND || rendererMode == RendererMode.AUTO || armorStandFallback) {
            try {
                return new ArmorStandNametagRenderer(plugin, player, armorStandYOffset);
            } catch (Throwable throwable) {
                if (!armorStandWarningShown) {
                    plugin.getLogger().warning("ArmorStand nametag renderer is unavailable: " + describe(throwable));
                    armorStandWarningShown = true;
                }
            }
        }
        return null;
    }

    private NametagRenderer updateTextWithFallback(Player player, NametagRenderer renderer, Component text) {
        try {
            renderer.setText(text);
            return renderer;
        } catch (Throwable throwable) {
            if (!"TEXT_DISPLAY".equals(renderer.type()) || !armorStandFallback) {
                plugin.getLogger().warning("Failed to update " + renderer.type() + " nametag: " + describe(throwable));
                removeRenderer(player.getUniqueId());
                return null;
            }

            textDisplayBroken = true;
            if (!textDisplayWarningShown) {
                plugin.getLogger().warning("TextDisplay nametag update failed: " + describe(throwable));
                plugin.getLogger().warning("CloverBadges will use the ArmorStand nametag fallback");
                textDisplayWarningShown = true;
            }

            removeRenderer(player.getUniqueId());
            try {
                NametagRenderer fallback = new ArmorStandNametagRenderer(plugin, player, armorStandYOffset);
                fallback.setText(text);
                renderers.put(player.getUniqueId(), fallback);
                return fallback;
            } catch (Throwable fallbackFailure) {
                if (!armorStandWarningShown) {
                    plugin.getLogger().warning("ArmorStand nametag fallback failed: " + describe(fallbackFailure));
                    armorStandWarningShown = true;
                }
                return null;
            }
        }
    }

    private void syncVisibility(
            Player owner,
            NametagRenderer renderer,
            List<Player> onlinePlayers,
            Set<UUID> onlineIds
    ) {
        renderer.retainViewers(onlineIds);
        for (Player viewer : onlinePlayers) {
            boolean visible = viewer != owner
                    && viewer.getWorld().equals(owner.getWorld())
                    && viewer.canSee(owner);
            renderer.setVisible(viewer, visible);
        }
    }

    private void restoreVanilla(Player player) {
        if (tabBridge != null) {
            tabBridge.restore(player);
        }
    }

    private void removeRenderer(UUID uuid) {
        NametagRenderer renderer = renderers.remove(uuid);
        if (renderer != null) {
            renderer.remove();
        }
        lastText.remove(uuid);
    }

    private void removeAll(boolean restoreVanilla) {
        for (NametagRenderer renderer : renderers.values()) {
            renderer.remove();
        }
        renderers.clear();
        lastText.clear();

        if (restoreVanilla && tabBridge != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                tabBridge.restore(player);
            }
        }
    }

    private String describe(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }
        return throwable.getClass().getSimpleName() + ": " + message;
    }

    private enum RendererMode {
        AUTO,
        TEXT_DISPLAY,
        ARMOR_STAND;

        private static RendererMode parse(String value) {
            if (value == null || value.isBlank()) {
                return AUTO;
            }
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException ignored) {
                return AUTO;
            }
        }
    }
}
