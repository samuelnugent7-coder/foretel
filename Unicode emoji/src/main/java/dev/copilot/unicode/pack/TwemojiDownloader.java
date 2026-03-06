package dev.copilot.unicode.pack;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

final class TwemojiDownloader {

    private static final URI BASE_URI = URI.create("https://raw.githubusercontent.com/twitter/twemoji/master/assets/72x72/");

    private final HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build();
    private final Path cacheDirectory;
    private final Logger logger;
    private volatile ResourcePackConfig.DownloadSettings downloadSettings = ResourcePackConfig.DownloadSettings.defaults();

    TwemojiDownloader(final Path cacheDirectory, final Logger logger) {
        this.cacheDirectory = cacheDirectory;
        this.logger = logger;
    }

    void updateSettings(final ResourcePackConfig.DownloadSettings settings) {
        if (settings != null) {
            this.downloadSettings = settings;
        }
    }

    Path ensureEmojiImage(final String emoji) throws IOException, InterruptedException {
        final String codepoints = toCodepointFileName(emoji);
        final Path cachedFile = this.cacheDirectory.resolve(codepoints + ".png");
        if (Files.exists(cachedFile)) {
            return cachedFile;
        }
        Files.createDirectories(this.cacheDirectory);
        final ResourcePackConfig.DownloadSettings settings = this.downloadSettings;
        final int attempts = Math.max(0, settings.maxRetries()) + 1;
        IOException lastError = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                final Path downloaded = downloadOnce(codepoints, cachedFile, settings);
                if (downloaded != null) {
                    return downloaded;
                }
                return null;
            } catch (final IOException ex) {
                lastError = ex;
                this.logger.log(Level.WARNING, "Attempt " + attempt + " to download Twemoji asset " + emoji + " failed", ex);
                if (attempt >= attempts) {
                    throw ex;
                }
                sleep(settings.retryDelaySeconds());
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        return null;
    }

    private Path downloadOnce(
        final String codepoints,
        final Path cachedFile,
        final ResourcePackConfig.DownloadSettings settings
    ) throws IOException, InterruptedException {
        final URI source = BASE_URI.resolve(codepoints + ".png");
        final HttpRequest request = HttpRequest.newBuilder(source)
            .timeout(Duration.ofSeconds(settings.timeoutSeconds()))
            .header("User-Agent", "UnicodeShortcodes/1.0 (+https://github.com/copilot)")
            .build();
        this.logger.info(() -> "Downloading Twemoji asset " + source);
        final HttpResponse<InputStream> response = this.client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() == 200) {
                final Path tempFile = Files.createTempFile("twemoji", ".png");
                Files.copy(body, tempFile, StandardCopyOption.REPLACE_EXISTING);
                Files.createDirectories(cachedFile.getParent());
                Files.move(tempFile, cachedFile, StandardCopyOption.REPLACE_EXISTING);
                return cachedFile;
            }
            if (response.statusCode() == 404) {
                this.logger.warning(() -> "Twemoji does not provide an asset for " + source);
                return null;
            }
        }
        throw new IOException("Unexpected HTTP status " + response.statusCode() + " for " + source);
    }

    private void sleep(final int seconds) throws InterruptedException {
        if (seconds <= 0) {
            return;
        }
        Thread.sleep(Duration.ofSeconds(seconds).toMillis());
    }

    private static String toCodepointFileName(final String emoji) {
        final StringBuilder builder = new StringBuilder();
        emoji.codePoints().forEach(codePoint -> {
            if (!Character.isWhitespace(codePoint)) {
                if (builder.length() > 0) {
                    builder.append('-');
                }
                builder.append(String.format(Locale.ROOT, "%04x", codePoint));
            }
        });
        return builder.toString();
    }
}
