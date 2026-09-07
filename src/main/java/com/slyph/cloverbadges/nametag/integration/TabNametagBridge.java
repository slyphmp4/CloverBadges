package com.slyph.cloverbadges.nametag.integration;

import com.slyph.cloverbadges.util.ColorUtil;
import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.nametag.NameTagManager;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TabNametagBridge {
    private final Map<UUID, Boolean> originalHiddenState = new ConcurrentHashMap<>();

    public boolean isReady(Player player) {
        return resolve(player) != null;
    }

    public boolean hideVanilla(Player player) {
        Context context = resolve(player);
        if (context == null) {
            return false;
        }

        UUID uuid = player.getUniqueId();
        originalHiddenState.computeIfAbsent(uuid, ignored -> context.manager().hasHiddenNameTag(context.player()));
        if (!context.manager().hasHiddenNameTag(context.player())) {
            context.manager().hideNameTag(context.player());
        }
        return true;
    }

    public void restore(Player player) {
        UUID uuid = player.getUniqueId();
        Boolean wasHidden = originalHiddenState.get(uuid);
        if (wasHidden == null) {
            return;
        }

        Context context = resolve(player);
        if (context == null) {
            return;
        }

        if (!wasHidden && context.manager().hasHiddenNameTag(context.player())) {
            context.manager().showNameTag(context.player());
        }
        originalHiddenState.remove(uuid);
    }

    public void forget(Player player) {
        originalHiddenState.remove(player.getUniqueId());
    }

    public Component prefix(Player player) {
        Context context = resolve(player);
        if (context == null) {
            return Component.empty();
        }
        return parse(readPrefix(context));
    }

    public Component suffix(Player player) {
        Context context = resolve(player);
        if (context == null) {
            return Component.empty();
        }
        return parse(readSuffix(context));
    }

    private String readPrefix(Context context) {
        try {
            return context.manager().getOriginalReplacedPrefix(context.player());
        } catch (NoSuchMethodError | AbstractMethodError ignored) {
            return context.manager().getOriginalPrefix(context.player());
        }
    }

    private String readSuffix(Context context) {
        try {
            return context.manager().getOriginalReplacedSuffix(context.player());
        } catch (NoSuchMethodError | AbstractMethodError ignored) {
            return context.manager().getOriginalSuffix(context.player());
        }
    }

    private Component parse(String text) {
        return ColorUtil.component(ColorUtil.toAmpersand(text == null ? "" : text));
    }

    private Context resolve(Player player) {
        try {
            TabAPI api = TabAPI.getInstance();
            NameTagManager manager = api.getNameTagManager();
            if (manager == null) {
                return null;
            }
            TabPlayer tabPlayer = api.getPlayer(player.getUniqueId());
            if (tabPlayer == null) {
                return null;
            }
            return new Context(manager, tabPlayer);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private record Context(NameTagManager manager, TabPlayer player) {
    }
}
