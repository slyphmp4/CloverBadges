package com.slyph.cloverbadges;

import com.slyph.cloverbadges.api.BadgeApi;
import com.slyph.cloverbadges.badge.BadgeRegistry;
import com.slyph.cloverbadges.command.BadgeCommand;
import com.slyph.cloverbadges.command.BadgeTabCompleter;
import com.slyph.cloverbadges.config.ConfigManager;
import com.slyph.cloverbadges.gui.BadgeMenuListener;
import com.slyph.cloverbadges.gui.BadgeMenuManager;
import com.slyph.cloverbadges.gui.action.BadgeActionExecutor;
import com.slyph.cloverbadges.head.CustomHeadService;
import com.slyph.cloverbadges.listener.PlayerListener;
import com.slyph.cloverbadges.message.MessageService;
import com.slyph.cloverbadges.nicknamecolor.NicknameColorRegistry;
import com.slyph.cloverbadges.nicknamecolor.PlayerNicknameColorService;
import com.slyph.cloverbadges.nicknamecolor.preview.NicknamePreviewService;
import com.slyph.cloverbadges.nicknamecolor.storage.NicknameColorStore;
import com.slyph.cloverbadges.placeholder.CloverBadgesExpansion;
import com.slyph.cloverbadges.player.PlayerBadgeService;
import com.slyph.cloverbadges.storage.PlayerDataStore;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;

public final class CloverBadges extends JavaPlugin {
    private ConfigManager configManager;
    private BadgeRegistry badgeRegistry;
    private MessageService messageService;
    private PlayerBadgeService badgeService;
    private PlayerNicknameColorService nicknameColorService;
    private CustomHeadService customHeadService;
    private CloverBadgesExpansion expansion;
    private BukkitTask cleanupTask;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        badgeRegistry = new BadgeRegistry(configManager);
        messageService = new MessageService(configManager);
        PlayerDataStore dataStore = new PlayerDataStore(this);
        badgeService = new PlayerBadgeService(this, badgeRegistry, dataStore);

        NicknameColorRegistry nicknameColorRegistry = new NicknameColorRegistry(configManager);
        NicknameColorStore nicknameColorStore = new NicknameColorStore(this);
        NicknamePreviewService nicknamePreviewService = new NicknamePreviewService(this, badgeService);
        nicknameColorService = new PlayerNicknameColorService(
                this,
                nicknameColorRegistry,
                nicknameColorStore,
                nicknamePreviewService
        );

        customHeadService = new CustomHeadService(this, configManager);
        BadgeActionExecutor actionExecutor = new BadgeActionExecutor(this, badgeService, messageService);
        BadgeMenuManager menuManager = new BadgeMenuManager(
                configManager,
                badgeService,
                nicknameColorService,
                actionExecutor,
                customHeadService
        );

        PluginCommand badgeCommand = Objects.requireNonNull(getCommand("badge"));
        badgeCommand.setExecutor(new BadgeCommand(this, badgeService, nicknameColorService, messageService, menuManager));
        badgeCommand.setTabCompleter(new BadgeTabCompleter(badgeService, nicknameColorService));

        getServer().getPluginManager().registerEvents(new PlayerListener(badgeService, nicknameColorService), this);
        getServer().getPluginManager().registerEvents(new BadgeMenuListener(menuManager), this);
        getServer().getServicesManager().register(BadgeApi.class, badgeService, this, ServicePriority.Normal);

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            expansion = new CloverBadgesExpansion(this, badgeService, nicknameColorService);
            expansion.register();
        }

        customHeadService.reload();
        scheduleCleanup();
        getLogger().info("CloverBadges v" + getPluginMeta().getVersion() + " enabled");
    }

    @Override
    public void onDisable() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        if (expansion != null) {
            expansion.unregister();
        }
        if (customHeadService != null) {
            customHeadService.shutdown();
        }
        if (badgeService != null) {
            badgeService.flushStorage();
        }
        if (nicknameColorService != null) {
            nicknameColorService.flushStorage();
        }
        getServer().getServicesManager().unregisterAll(this);
    }

    public void reloadPlugin() {
        configManager.reload();
        badgeRegistry.reload();
        nicknameColorService.reload();
        customHeadService.reload();
        scheduleCleanup();
    }

    private void scheduleCleanup() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        long interval = Math.max(20L, getConfig().getLong("storage.cleanup-interval-ticks", 6000L));
        cleanupTask = getServer().getScheduler().runTaskTimer(this, () -> {
            badgeService.cleanupExpired();
            nicknameColorService.cleanupExpired();
        }, interval, interval);
    }

    public BadgeApi getBadgeApi() {
        return badgeService;
    }
}
