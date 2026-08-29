package com.slyph.cloverbadges;

import com.slyph.cloverbadges.api.BadgeApi;
import com.slyph.cloverbadges.badge.BadgeRegistry;
import com.slyph.cloverbadges.command.BadgeCommand;
import com.slyph.cloverbadges.command.BadgeTabCompleter;
import com.slyph.cloverbadges.config.ConfigManager;
import com.slyph.cloverbadges.gui.BadgeMenuListener;
import com.slyph.cloverbadges.gui.BadgeMenuManager;
import com.slyph.cloverbadges.gui.action.BadgeActionExecutor;
import com.slyph.cloverbadges.listener.PlayerListener;
import com.slyph.cloverbadges.message.MessageService;
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
    private CloverBadgesExpansion expansion;
    private BukkitTask cleanupTask;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        badgeRegistry = new BadgeRegistry(configManager);
        messageService = new MessageService(configManager);
        PlayerDataStore dataStore = new PlayerDataStore(this);
        badgeService = new PlayerBadgeService(this, badgeRegistry, dataStore);
        BadgeActionExecutor actionExecutor = new BadgeActionExecutor(this, badgeService, messageService);
        BadgeMenuManager menuManager = new BadgeMenuManager(configManager, badgeService, actionExecutor);

        PluginCommand badgeCommand = Objects.requireNonNull(getCommand("badge"));
        badgeCommand.setExecutor(new BadgeCommand(this, badgeService, messageService, menuManager));
        badgeCommand.setTabCompleter(new BadgeTabCompleter(badgeService));

        getServer().getPluginManager().registerEvents(new PlayerListener(badgeService), this);
        getServer().getPluginManager().registerEvents(new BadgeMenuListener(menuManager), this);
        getServer().getServicesManager().register(BadgeApi.class, badgeService, this, ServicePriority.Normal);

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            expansion = new CloverBadgesExpansion(this, badgeService);
            expansion.register();
        }

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
        if (badgeService != null) {
            badgeService.saveAll();
        }
        getServer().getServicesManager().unregisterAll(this);
    }

    public void reloadPlugin() {
        configManager.reload();
        badgeRegistry.reload();
        scheduleCleanup();
    }

    private void scheduleCleanup() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        long interval = Math.max(20L, getConfig().getLong("storage.cleanup-interval-ticks", 6000L));
        cleanupTask = getServer().getScheduler().runTaskTimer(this, badgeService::cleanupExpired, interval, interval);
    }

    public BadgeApi getBadgeApi() {
        return badgeService;
    }
}
