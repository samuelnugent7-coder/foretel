package dev.copilot.unicode.pack;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class ResourcePackConfig {

    private final boolean enabled;
    private final boolean autoSend;
    private final boolean force;
    private final String prompt;
    private final HostConfig hostConfig;
    private final int glyphHeight;
    private final int glyphAscent;
    private final int packFormat;
    private final int supportedFormatMin;
    private final int supportedFormatMax;
    private final DownloadSettings downloadSettings;

    private ResourcePackConfig(
        final boolean enabled,
        final boolean autoSend,
        final boolean force,
        final String prompt,
        final HostConfig hostConfig,
        final int glyphHeight,
        final int glyphAscent,
        final int packFormat,
        final int supportedFormatMin,
        final int supportedFormatMax,
        final DownloadSettings downloadSettings
    ) {
        this.enabled = enabled;
        this.autoSend = autoSend;
        this.force = force;
        this.prompt = prompt;
        this.hostConfig = hostConfig;
        this.glyphHeight = glyphHeight;
        this.glyphAscent = glyphAscent;
        this.packFormat = packFormat;
        this.supportedFormatMin = supportedFormatMin;
        this.supportedFormatMax = supportedFormatMax;
        this.downloadSettings = downloadSettings;
    }

    public static ResourcePackConfig from(final FileConfiguration config) {
        final ConfigurationSection section = config.getConfigurationSection("resource-pack");
        if (section == null) {
            return disabled();
        }

        final boolean enabled = section.getBoolean("enabled", false);
        final boolean autoSend = section.getBoolean("auto-send-on-join", true);
        final boolean force = section.getBoolean("force", false);
        final String prompt = section.getString("prompt", "This server offers a custom emoji font. Accept to view emojis in color.");

        final ConfigurationSection hostSection = section.getConfigurationSection("host");
        final HostConfig hostConfig = hostSection == null
            ? HostConfig.disabled()
            : new HostConfig(
                hostSection.getBoolean("enabled", false),
                hostSection.getString("bind-address", "0.0.0.0"),
                hostSection.getInt("port", 8137),
                sanitizeRoute(hostSection.getString("route", "unicode-shortcodes-pack.zip")),
                hostSection.getString("public-url", "http://127.0.0.1:8137/unicode-shortcodes-pack.zip")
            );

        final ConfigurationSection glyphSection = section.getConfigurationSection("glyph");
        final int glyphHeight = glyphSection != null ? glyphSection.getInt("height", 72) : 72;
        final int glyphAscent = glyphSection != null ? glyphSection.getInt("ascent", 60) : 60;

        final int packFormat = section.getInt("pack-format", 15);
        final ConfigurationSection supportedSection = section.getConfigurationSection("supported-format-range");
        final int supportedMin = supportedSection != null ? supportedSection.getInt("min", packFormat) : packFormat;
        final int supportedMax = supportedSection != null ? supportedSection.getInt("max", Math.max(packFormat, supportedMin)) : Math.max(packFormat, supportedMin);

        final ConfigurationSection downloadSection = section.getConfigurationSection("download");
        final DownloadSettings downloadSettings = downloadSection == null
            ? DownloadSettings.defaults()
            : new DownloadSettings(
                Math.max(0, downloadSection.getInt("max-retries", 3)),
                Math.max(0, downloadSection.getInt("retry-delay-seconds", 5)),
                Math.max(1, downloadSection.getInt("timeout-seconds", 30))
            );

        return new ResourcePackConfig(
            enabled,
            autoSend,
            force,
            prompt,
            hostConfig,
            glyphHeight,
            glyphAscent,
            packFormat,
            Math.min(supportedMin, packFormat),
            Math.max(Math.max(supportedMax, supportedMin), packFormat),
            downloadSettings
        );
    }

    private static ResourcePackConfig disabled() {
        return new ResourcePackConfig(
            false,
            false,
            false,
            "",
            HostConfig.disabled(),
            72,
            60,
            15,
            15,
            15,
            DownloadSettings.defaults()
        );
    }

    private static String sanitizeRoute(final String route) {
        if (route == null || route.isBlank()) {
            return "/unicode-shortcodes-pack.zip";
        }
        return route.startsWith("/") ? route : "/" + route;
    }

    public boolean enabled() {
        return this.enabled;
    }

    public boolean autoSend() {
        return this.autoSend;
    }

    public boolean force() {
        return this.force;
    }

    public String prompt() {
        return this.prompt;
    }

    public HostConfig hostConfig() {
        return this.hostConfig;
    }

    public int glyphHeight() {
        return this.glyphHeight;
    }

    public int glyphAscent() {
        return this.glyphAscent;
    }

    public int packFormat() {
        return this.packFormat;
    }

    public int supportedFormatMin() {
        return this.supportedFormatMin;
    }

    public int supportedFormatMax() {
        return this.supportedFormatMax;
    }

    public DownloadSettings downloadSettings() {
        return this.downloadSettings;
    }

    public static final class HostConfig {

        private final boolean enabled;
        private final String bindAddress;
        private final int port;
        private final String route;
        private final String publicUrl;

        public HostConfig(final boolean enabled, final String bindAddress, final int port, final String route, final String publicUrl) {
            this.enabled = enabled;
            this.bindAddress = bindAddress;
            this.port = port;
            this.route = route;
            this.publicUrl = publicUrl;
        }

        public static HostConfig disabled() {
            return new HostConfig(false, "0.0.0.0", 8137, "/unicode-shortcodes-pack.zip", "");
        }

        public boolean enabled() {
            return this.enabled;
        }

        public String bindAddress() {
            return this.bindAddress;
        }

        public int port() {
            return this.port;
        }

        public String route() {
            return this.route;
        }

        public String publicUrl() {
            return this.publicUrl;
        }
    }

    public static final class DownloadSettings {

        private final int maxRetries;
        private final int retryDelaySeconds;
        private final int timeoutSeconds;

        public DownloadSettings(final int maxRetries, final int retryDelaySeconds, final int timeoutSeconds) {
            this.maxRetries = maxRetries;
            this.retryDelaySeconds = retryDelaySeconds;
            this.timeoutSeconds = timeoutSeconds;
        }

        public static DownloadSettings defaults() {
            return new DownloadSettings(3, 5, 30);
        }

        public int maxRetries() {
            return this.maxRetries;
        }

        public int retryDelaySeconds() {
            return this.retryDelaySeconds;
        }

        public int timeoutSeconds() {
            return this.timeoutSeconds;
        }
    }
}
