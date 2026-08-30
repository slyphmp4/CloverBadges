package com.slyph.cloverbadges.nicknamecolor.preview;

import com.slyph.cloverbadges.CloverBadges;
import com.slyph.cloverbadges.player.PlayerBadgeService;
import com.slyph.cloverbadges.util.ColorUtil;
import org.bukkit.entity.Player;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class NicknamePreviewService {
    private final CloverBadges plugin;
    private final PlayerBadgeService badgeService;

    public NicknamePreviewService(CloverBadges plugin, PlayerBadgeService badgeService) {
        this.plugin = plugin;
        this.badgeService = badgeService;
    }

    public String render(Player player, String nickname) {
        List<String> parts = new ArrayList<>();
        addPart(parts, ColorUtil.toAmpersand(resolveUltraPermissionsPrefix(player)));
        addPart(parts, activeBadges(player));
        addPart(parts, nickname);
        return String.join(" ", parts);
    }

    private String activeBadges(Player player) {
        List<String> active = badgeService.getActiveBadgeIds(player);
        if (active.isEmpty()) {
            return "";
        }
        String separator = plugin.getConfig().getString("display.separator", " ");
        return active.stream()
                .map(badgeService::getBadgeText)
                .reduce((left, right) -> left + separator + right)
                .orElse("");
    }

    private String resolveUltraPermissionsPrefix(Player player) {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("UltraPermissions")) {
            return "";
        }
        try {
            Class<?> ultraPermissions = Class.forName("me.TechsCode.UltraPermissions.UltraPermissions");
            Object api = ultraPermissions.getMethod("getAPI").invoke(null);
            Object users = invokeNoArgs(api, "getUsers");
            Object user = invokeOneArg(users, "uuid", UUID.class, player.getUniqueId());

            String direct = invokeString(user, "getPrefix");
            if (!direct.isBlank()) {
                return direct;
            }

            Object groups = invokeNoArgs(user, "getGroups");
            Object ordered = invokeNoArgs(groups, "worstToBest");
            Object value = unwrapValue(ordered);
            Object first = firstValue(value);
            return first == null ? "" : invokeString(first, "getPrefix");
        } catch (ReflectiveOperationException | LinkageError exception) {
            return "";
        }
    }

    private Object invokeNoArgs(Object target, String methodName) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName);
        return method.invoke(target);
    }

    private Object invokeOneArg(Object target, String methodName, Class<?> type, Object value) throws ReflectiveOperationException {
        Method method = target.getClass().getMethod(methodName, type);
        return method.invoke(target, value);
    }

    private String invokeString(Object target, String methodName) {
        if (target == null) {
            return "";
        }
        try {
            Object value = invokeNoArgs(target, methodName);
            return value instanceof String string ? string : "";
        } catch (ReflectiveOperationException exception) {
            return "";
        }
    }

    private Object unwrapValue(Object value) {
        if (value == null || value.getClass().isArray() || value instanceof Collection<?>) {
            return value;
        }
        try {
            Method get = value.getClass().getMethod("get");
            return get.invoke(value);
        } catch (ReflectiveOperationException exception) {
            return value;
        }
    }

    private Object firstValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value.getClass().isArray()) {
            return Array.getLength(value) == 0 ? null : Array.get(value, 0);
        }
        if (value instanceof List<?> list) {
            return list.isEmpty() ? null : list.getFirst();
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().findFirst().orElse(null);
        }
        return value;
    }

    private void addPart(List<String> parts, String value) {
        if (value == null) {
            return;
        }
        String trimmed = value.strip();
        if (!trimmed.isEmpty()) {
            parts.add(trimmed);
        }
    }
}
