package com.coreclub.fulfillment.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record ManagedPackage(
    String sku,
    String name,
    String description,
    String imageUrl,
    List<String> commands,
    List<String> galleryImages
) {

    public ManagedPackage {
        sku = sanitizeSku(sku);
        name = trim(name);
        description = trim(description);
        imageUrl = trim(imageUrl);
        commands = sanitizeList(commands);
        galleryImages = sanitizeList(galleryImages);
    }

    public boolean isValid() {
        return sku != null && !sku.isBlank() && !commands.isEmpty();
    }

    public List<String> commandList() {
        return commands == null ? Collections.emptyList() : commands;
    }

    public List<String> galleryList() {
        return galleryImages == null ? Collections.emptyList() : galleryImages;
    }

    private static String sanitizeSku(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.toLowerCase(Locale.ROOT);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static List<String> sanitizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> sanitized = new ArrayList<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (!trimmed.isEmpty() && !sanitized.contains(trimmed)) {
                sanitized.add(trimmed);
            }
        }
        return Collections.unmodifiableList(sanitized);
    }
}
