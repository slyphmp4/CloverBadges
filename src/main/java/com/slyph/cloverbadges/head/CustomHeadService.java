package com.slyph.cloverbadges.head;

import com.destroystokyo.paper.profile.ProfileProperty;
import com.slyph.cloverbadges.CloverBadges;
import com.slyph.cloverbadges.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerTextures;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CustomHeadService {
    private static final String BASE_API = "https://minecraft-heads.com/api/heads";
    private static final String PROFILE_NAME = "CloverBadge";
    private static final Pattern TEXTURE_URL_PATTERN = Pattern.compile("\\\"url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern TEXTURE_HASH_PATTERN = Pattern.compile("^[0-9a-fA-F]{32,128}$");

    private final CloverBadges plugin;
    private final ConfigManager configManager;
    private final ExecutorService executor;
    private final Map<String, TextureData> resolvedTextures = new ConcurrentHashMap<>();
    private final Set<String> warnedTextures = ConcurrentHashMap.newKeySet();
    private final AtomicLong generation = new AtomicLong();

    public CustomHeadService(CloverBadges plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "CloverBadges-MinecraftHeads");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void reload() {
        long currentGeneration = generation.incrementAndGet();
        Set<String> configuredNames = collectConfiguredNames();
        Set<String> normalizedNames = new LinkedHashSet<>();
        for (String name : configuredNames) {
            normalizedNames.add(normalizeName(name));
        }
        resolvedTextures.keySet().retainAll(normalizedNames);

        if (configuredNames.isEmpty()) {
            return;
        }
        if (!plugin.getConfig().getBoolean("minecraft-heads.api.enabled", true)) {
            return;
        }

        String appUuid = plugin.getConfig().getString("minecraft-heads.api.app-uuid", "").trim();
        if (appUuid.isEmpty()) {
            plugin.getLogger().warning("Minecraft-Heads name lookup requires minecraft-heads.api.app-uuid. Falling back to head.value where configured.");
            return;
        }

        String apiKey = plugin.getConfig().getString("minecraft-heads.api.api-key", "").trim();
        boolean demo = plugin.getConfig().getBoolean("minecraft-heads.api.demo", false);
        int connectTimeout = Math.max(1000, plugin.getConfig().getInt("minecraft-heads.api.connect-timeout-ms", 5000));
        int readTimeout = Math.max(1000, plugin.getConfig().getInt("minecraft-heads.api.read-timeout-ms", 10000));
        ApiSettings settings = new ApiSettings(appUuid, apiKey, demo, connectTimeout, readTimeout);
        LinkedHashSet<String> names = new LinkedHashSet<>(configuredNames);
        executor.submit(() -> sync(currentGeneration, names, settings));
    }

    public void shutdown() {
        generation.incrementAndGet();
        executor.shutdownNow();
    }

    public void apply(ItemMeta meta, String minecraftHeadsName, String fallbackValue) {
        if (!(meta instanceof SkullMeta skullMeta)) {
            return;
        }

        Optional<TextureData> apiTexture = resolveTexture(minecraftHeadsName);
        Optional<TextureData> fallbackTexture = textureFromValue(fallbackValue);
        TextureData texture = apiTexture.orElseGet(() -> fallbackTexture.orElse(null));
        if (texture == null) {
            return;
        }

        LinkedHashMap<String, Throwable> failures = new LinkedHashMap<>();

        Throwable paperFailure = applyPaperTexture(skullMeta, texture);
        if (paperFailure == null) {
            return;
        }
        failures.put("paper", paperFailure);

        Throwable bukkitFailure = applyBukkitTexture(skullMeta, texture);
        if (bukkitFailure == null) {
            return;
        }
        failures.put("bukkit", bukkitFailure);

        if (warnedTextures.add(texture.url())) {
            plugin.getLogger().warning("Failed to apply custom head texture through standard profile APIs on " + Bukkit.getName() + " " + Bukkit.getVersion() + ". " + failureSummary(failures));
        }
    }

    public Optional<String> resolveTextureUrl(String minecraftHeadsName) {
        return resolveTexture(minecraftHeadsName).map(TextureData::url);
    }

    private Optional<TextureData> resolveTexture(String minecraftHeadsName) {
        if (minecraftHeadsName == null || minecraftHeadsName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(resolvedTextures.get(normalizeName(minecraftHeadsName)));
    }

    private Throwable applyPaperTexture(SkullMeta skullMeta, TextureData texture) {
        try {
            com.destroystokyo.paper.profile.PlayerProfile profile = Bukkit.createProfile(profileId(texture), PROFILE_NAME);
            profile.clearProperties();
            profile.setProperty(new ProfileProperty("textures", texture.value()));
            skullMeta.setPlayerProfile(profile);
            return null;
        } catch (Throwable throwable) {
            return throwable;
        }
    }

    private Throwable applyBukkitTexture(SkullMeta skullMeta, TextureData texture) {
        try {
            org.bukkit.profile.PlayerProfile profile = Bukkit.createPlayerProfile(profileId(texture), PROFILE_NAME);
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(URI.create(texture.url()).toURL());
            profile.setTextures(textures);
            skullMeta.setOwnerProfile(profile);
            return null;
        } catch (Throwable throwable) {
            return throwable;
        }
    }

    private UUID profileId(TextureData texture) {
        return UUID.nameUUIDFromBytes(("CloverBadges:" + texture.url()).getBytes(StandardCharsets.UTF_8));
    }

    private String failureSummary(Map<String, Throwable> failures) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Throwable> entry : failures.entrySet()) {
            Throwable throwable = entry.getValue();
            String message = throwable.getMessage();
            String detail = throwable.getClass().getSimpleName();
            if (message != null && !message.isBlank()) {
                detail += ": " + message;
            }
            parts.add(entry.getKey() + "=" + detail);
        }
        return String.join("; ", parts);
    }

    private void sync(long syncGeneration, Set<String> configuredNames, ApiSettings settings) {
        LinkedHashMap<String, String> pending = new LinkedHashMap<>();
        for (String name : configuredNames) {
            String normalized = normalizeName(name);
            if (!resolvedTextures.containsKey(normalized)) {
                pending.put(normalized, name);
            }
        }
        if (pending.isEmpty() || syncGeneration != generation.get()) {
            return;
        }

        try {
            List<Integer> categoryIds = fetchCategoryIds(settings);
            if (categoryIds.isEmpty()) {
                scanHeads(syncGeneration, null, pending, settings);
            } else {
                for (Integer categoryId : categoryIds) {
                    if (pending.isEmpty() || syncGeneration != generation.get()) {
                        break;
                    }
                    scanHeads(syncGeneration, categoryId, pending, settings);
                }
            }

            if (!pending.isEmpty() && syncGeneration == generation.get()) {
                plugin.getLogger().warning("Minecraft-Heads could not resolve: " + String.join(", ", pending.values()) + ". Falling back to head.value where configured.");
            }
        } catch (IOException exception) {
            if (syncGeneration == generation.get()) {
                plugin.getLogger().warning("Minecraft-Heads API sync failed: " + exception.getMessage());
            }
        }
    }

    private List<Integer> fetchCategoryIds(ApiSettings settings) throws IOException {
        String url = BASE_API + "/categories?app_uuid=" + encode(settings.appUuid());
        if (settings.demo()) {
            url += "&demo=true";
        }
        YamlConfiguration response = fetch(url, settings);
        List<Integer> result = new ArrayList<>();
        for (Map<?, ?> entry : response.getMapList("data")) {
            Object id = entry.get("id");
            if (id instanceof Number number) {
                result.add(number.intValue());
            } else if (id != null) {
                try {
                    result.add(Integer.parseInt(id.toString()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return result;
    }

    private void scanHeads(long syncGeneration, Integer categoryId, Map<String, String> pending, ApiSettings settings) throws IOException {
        int page = 1;
        while (page <= 50 && !pending.isEmpty() && syncGeneration == generation.get()) {
            StringBuilder url = new StringBuilder(BASE_API)
                    .append("/custom-heads?app_uuid=")
                    .append(encode(settings.appUuid()))
                    .append("&page=")
                    .append(page);
            if (categoryId != null) {
                url.append("&category_id=").append(categoryId);
            }
            if (!settings.apiKey().isEmpty()) {
                url.append("&value=true");
            }
            if (settings.demo()) {
                url.append("&demo=true");
            }

            YamlConfiguration response = fetch(url.toString(), settings);
            List<Map<?, ?>> data = response.getMapList("data");
            if (data.isEmpty()) {
                break;
            }

            for (Map<?, ?> entry : data) {
                String name = firstString(entry, "n", "name");
                if (name == null) {
                    continue;
                }
                String normalized = normalizeName(name);
                if (!pending.containsKey(normalized)) {
                    continue;
                }
                Optional<TextureData> texture = textureFromEntry(entry);
                if (texture.isEmpty()) {
                    continue;
                }
                if (syncGeneration == generation.get()) {
                    resolvedTextures.putIfAbsent(normalized, texture.get());
                    pending.remove(normalized);
                }
            }

            if (response.getBoolean("meta.data_limited", false) || data.size() < 10000) {
                break;
            }
            page++;
        }
    }

    private YamlConfiguration fetch(String url, ApiSettings settings) throws IOException {
        URLConnection connection = URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(settings.connectTimeoutMs());
        connection.setReadTimeout(settings.readTimeoutMs());
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "CloverBadges/" + plugin.getPluginMeta().getVersion());
        if (!settings.apiKey().isEmpty()) {
            connection.setRequestProperty("api-key", settings.apiKey());
        }

        StringBuilder body = new StringBuilder();
        try (InputStreamReader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                body.append(buffer, 0, read);
            }
        }

        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.loadFromString(body.toString());
        } catch (InvalidConfigurationException exception) {
            throw new IOException("invalid API response", exception);
        }
        return configuration;
    }

    private Set<String> collectConfiguredNames() {
        YamlConfiguration gui = configManager.gui();
        LinkedHashSet<String> result = new LinkedHashSet<>();
        addConfiguredName(result, gui.getString("badge.head.minecraft-heads"));
        addConfiguredName(result, gui.getString("badge.head-active.minecraft-heads"));
        addConfiguredName(result, gui.getString("badge.head-inactive.minecraft-heads"));
        addConfiguredName(result, gui.getString("page-switcher.head.minecraft-heads"));
        addConfiguredName(result, gui.getString("page-switcher.badges-page.head.minecraft-heads"));
        addConfiguredName(result, gui.getString("page-switcher.nickname-colors-page.head.minecraft-heads"));

        ConfigurationSection badges = gui.getConfigurationSection("badges");
        if (badges == null) {
            return result;
        }
        for (String badgeId : badges.getKeys(false)) {
            String base = "badges." + badgeId + ".";
            addConfiguredName(result, gui.getString(base + "head.minecraft-heads"));
            addConfiguredName(result, gui.getString(base + "head-active.minecraft-heads"));
            addConfiguredName(result, gui.getString(base + "head-inactive.minecraft-heads"));
        }
        return result;
    }

    private void addConfiguredName(Collection<String> target, String value) {
        if (value != null && !value.isBlank()) {
            target.add(value.trim());
        }
    }

    private Optional<TextureData> textureFromEntry(Map<?, ?> entry) {
        String value = firstString(entry, "v", "value");
        Optional<TextureData> fromValue = textureFromValue(value);
        if (fromValue.isPresent()) {
            return fromValue;
        }

        String url = firstString(entry, "u", "url");
        return textureFromUrl(url);
    }

    private Optional<TextureData> textureFromValue(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        Optional<TextureData> direct = textureFromUrl(value.trim());
        if (direct.isPresent()) {
            return direct;
        }

        String compact = value.replaceAll("\\s+", "");
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(compact);
        } catch (IllegalArgumentException exception) {
            try {
                decoded = Base64.getUrlDecoder().decode(compact);
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        }

        String json = new String(decoded, StandardCharsets.UTF_8).replace("\\/", "/");
        Matcher matcher = TEXTURE_URL_PATTERN.matcher(json);
        if (!matcher.find()) {
            return Optional.empty();
        }

        Optional<String> normalizedUrl = normalizeTextureUrl(matcher.group(1));
        if (normalizedUrl.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(textureData(normalizedUrl.get()));
    }

    private Optional<TextureData> textureFromUrl(String value) {
        Optional<String> normalizedUrl = normalizeTextureUrl(value);
        if (normalizedUrl.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(textureData(normalizedUrl.get()));
    }

    private TextureData textureData(String normalizedUrl) {
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + normalizedUrl + "\"}}}";
        String encoded = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        return new TextureData(normalizedUrl, encoded);
    }

    private Optional<String> normalizeTextureUrl(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String candidate = value.trim().replace("\\/", "/");
        if (TEXTURE_HASH_PATTERN.matcher(candidate).matches()) {
            return Optional.of("https://textures.minecraft.net/texture/" + candidate);
        }
        if (candidate.startsWith("textures.minecraft.net/texture/")) {
            candidate = "https://" + candidate;
        }

        try {
            URI uri = URI.create(candidate);
            String host = uri.getHost();
            String path = uri.getPath();
            if (host == null || !host.equalsIgnoreCase("textures.minecraft.net") || path == null || !path.startsWith("/texture/")) {
                return Optional.empty();
            }
            return Optional.of("https://textures.minecraft.net" + path);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private String firstString(Map<?, ?> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return null;
    }

    private String normalizeName(String value) {
        return value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record TextureData(String url, String value) {
    }

    private record ApiSettings(String appUuid, String apiKey, boolean demo, int connectTimeoutMs, int readTimeoutMs) {
    }
}
