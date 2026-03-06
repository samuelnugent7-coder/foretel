package dev.copilot.unicode.pack;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class EmojiResourcePackBuilder {

    private static final String PACK_DESCRIPTION = "Unicode Shortcodes Emoji Pack (Twemoji CC-BY 4.0)";
    private static final String LICENSE_TEXT = "Emoji artwork provided by Twitter Twemoji (CC-BY 4.0).";

    private EmojiResourcePackBuilder() {
    }

    static PackBuildResult build(
        final Path dataFolder,
        final ResourcePackConfig config,
        final Map<String, String> shortcodeMap,
        final TwemojiDownloader downloader,
        final Logger logger
    ) throws IOException, InterruptedException {
        final Path workingDir = dataFolder.resolve("generated-pack");
        cleanDirectory(workingDir);
        final Path assetsDir = workingDir.resolve("assets");
        final Path namespaceFontDir = assetsDir.resolve("unicode_shortcodes/font/emojis");
        final Path minecraftFontDir = assetsDir.resolve("minecraft/font");
        Files.createDirectories(namespaceFontDir);
        Files.createDirectories(minecraftFontDir);

        final Set<String> uniqueEmojis = new LinkedHashSet<>(shortcodeMap.values());
        final List<GlyphEntry> glyphEntries = new ArrayList<>(uniqueEmojis.size());
        for (final String emoji : uniqueEmojis) {
            final Path emojiFile = downloader.ensureEmojiImage(emoji);
            if (emojiFile == null) {
                continue;
            }
            final String fileName = emojiFile.getFileName().toString();
            final Path destination = namespaceFontDir.resolve(fileName);
            Files.copy(emojiFile, destination, StandardCopyOption.REPLACE_EXISTING);
            glyphEntries.add(new GlyphEntry(emoji, "unicode_shortcodes:font/emojis/" + fileName));
        }

        writeFontJson(minecraftFontDir.resolve("default.json"), glyphEntries, config.glyphHeight(), config.glyphAscent());
        writePackMeta(workingDir, config);
        writeLicense(workingDir);

        final Path zipPath = dataFolder.resolve("unicode-shortcodes-pack.zip");
        zipDirectory(workingDir, zipPath);
        final byte[] sha1 = sha1(zipPath);
        return new PackBuildResult(zipPath, sha1);
    }

    private static void cleanDirectory(final Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (var walk = Files.walk(directory)) {
            walk.sorted(Comparator.<Path>comparingInt(Path::getNameCount).reversed())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (final IOException ex) {
                        throw new RuntimeException(ex);
                    }
                });
        }
    }

    private static void writeFontJson(final Path output, final List<GlyphEntry> glyphs, final int height, final int ascent) throws IOException {
        Files.createDirectories(output.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            writer.write("{\"providers\":[");
            writer.write("{\"type\":\"reference\",\"file\":\"minecraft:default\"}");
            for (final GlyphEntry glyph : glyphs) {
                writer.write(",");
                writer.write("{\"type\":\"bitmap\",\"file\":\"");
                writer.write(escapeJson(glyph.assetLocation()));
                writer.write("\",\"ascent\":");
                writer.write(Integer.toString(ascent));
                writer.write(",\"height\":");
                writer.write(Integer.toString(height));
                writer.write(",\"chars\":[\"");
                writer.write(escapeJson(glyph.emoji()));
                writer.write("\"]}");
            }
            writer.write("]}");
        }
    }

    private static void writePackMeta(final Path workingDir, final ResourcePackConfig config) throws IOException {
        final Path packMeta = workingDir.resolve("pack.mcmeta");
        Files.createDirectories(packMeta.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(packMeta, StandardCharsets.UTF_8)) {
            writer.write("{\"pack\":{\"pack_format\":");
            writer.write(Integer.toString(config.packFormat()));
            if (config.supportedFormatMin() != config.supportedFormatMax()) {
                writer.write(",\"supported_formats\":{\"min_inclusive\":");
                writer.write(Integer.toString(config.supportedFormatMin()));
                writer.write(",\"max_inclusive\":");
                writer.write(Integer.toString(config.supportedFormatMax()));
                writer.write("}");
            }
            writer.write(",\"description\":\"");
            writer.write(escapeJson(PACK_DESCRIPTION));
            writer.write("\"}}\n");
        }
    }

    private static void writeLicense(final Path workingDir) throws IOException {
        final Path license = workingDir.resolve("credits/twemoji.txt");
        Files.createDirectories(license.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(license, StandardCharsets.UTF_8)) {
            writer.write(LICENSE_TEXT);
            writer.newLine();
            writer.write("https://twemoji.twitter.com/ (CC-BY 4.0)");
        }
    }

    private static void zipDirectory(final Path sourceDir, final Path zipFile) throws IOException {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipFile));
             var walk = Files.walk(sourceDir, FileVisitOption.FOLLOW_LINKS)) {
            walk.filter(path -> !Files.isDirectory(path)).forEach(path -> {
                final Path relative = sourceDir.relativize(path);
                final ZipEntry entry = new ZipEntry(relative.toString().replace('\\', '/'));
                try {
                    zipOutputStream.putNextEntry(entry);
                    Files.copy(path, zipOutputStream);
                    zipOutputStream.closeEntry();
                } catch (final IOException ex) {
                    throw new RuntimeException(ex);
                }
            });
        }
    }

    private static byte[] sha1(final Path file) throws IOException {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (var input = Files.newInputStream(file)) {
                final byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return digest.digest();
        } catch (final NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-1 not supported", ex);
        }
    }

    private static String escapeJson(final String value) {
        final StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            if (c == '"' || c == '\\') {
                builder.append('\\');
            }
            builder.append(c);
        }
        return builder.toString();
    }

    private record GlyphEntry(String emoji, String assetLocation) {
    }
}
