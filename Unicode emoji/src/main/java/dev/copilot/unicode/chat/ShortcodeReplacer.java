package dev.copilot.unicode.chat;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Immutable service that converts colon shortcodes into their emoji equivalents.
 */
public final class ShortcodeReplacer {

    private final Map<String, String> shortcodeToEmoji;
    private final Map<String, Component> replacements;
    private final TextReplacementConfig replacementConfig;

    private ShortcodeReplacer(
        final Map<String, String> shortcodeToEmoji,
        final Map<String, Component> replacements,
        final TextReplacementConfig replacementConfig
    ) {
        this.shortcodeToEmoji = shortcodeToEmoji;
        this.replacements = replacements;
        this.replacementConfig = replacementConfig;
    }

    public static ShortcodeReplacer fromConfig(final FileConfiguration config) {
        final ConfigurationSection section = config.getConfigurationSection("shortcodes");
        final Map<String, String> ordered = new LinkedHashMap<>();
        if (section != null) {
            for (final String key : section.getKeys(false)) {
                final String normalizedKey = normalizeKey(key);
                final String emoji = section.getString(key);
                if (normalizedKey == null || emoji == null || emoji.isEmpty()) {
                    continue;
                }
                ordered.put(normalizedKey, emoji);
            }
        }

        if (ordered.isEmpty()) {
            return new ShortcodeReplacer(Map.of(), Map.of(), null);
        }

        final int configuredLimit = config.getInt("max-replacements-per-message", 64);
        final int maxReplacements = Math.max(1, configuredLimit);

        final String patternSource = ordered.keySet()
            .stream()
            .sorted(Comparator.comparingInt(String::length).reversed())
            .map(Pattern::quote)
            .collect(Collectors.joining("|"));

        final Pattern pattern = Pattern.compile(patternSource);

        final Map<String, Component> replacements = ordered.entrySet()
            .stream()
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> Component.text(entry.getValue())));

        final TextReplacementConfig configBuilder = TextReplacementConfig.builder()
            .match(pattern)
            .times(maxReplacements)
            .replacement((matchResult, builder) -> replacements.getOrDefault(matchResult.group(), Component.text(matchResult.group())))
            .build();

        return new ShortcodeReplacer(Map.copyOf(ordered), replacements, configBuilder);
    }

    public Component replace(final Component input) {
        if (this.replacementConfig == null) {
            return input;
        }
        return input.replaceText(this.replacementConfig);
    }

    public int shortcodeCount() {
        return this.replacements.size();
    }

    public Map<String, String> shortcodeMap() {
        return this.shortcodeToEmoji;
    }

    private static String normalizeKey(final String rawKey) {
        if (rawKey == null) {
            return null;
        }
        final String trimmed = rawKey.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
