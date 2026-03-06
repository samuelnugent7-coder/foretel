package com.coreclub.fulfillment.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public record FulfillmentRequest(
    String token,
    String orderId,
    String player,
    String sku,
    String commands,
    List<String> commandsList
) {

    public FulfillmentRequest {
        token = trim(token);
        orderId = trim(orderId);
        player = trim(player);
        sku = trim(sku);
    }

    public boolean isValid() {
        return token != null && !token.isBlank()
            && orderId != null && !orderId.isBlank()
            && player != null && !player.isBlank()
            && sku != null && !sku.isBlank();
    }

    public List<String> resolvedCommands() {
        List<String> inline = sanitize(commandsList);
        if (!inline.isEmpty()) {
            return inline;
        }
        if (commands != null && !commands.isBlank()) {
            return Arrays.stream(commands.split("\\r?\\n"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private static List<String> sanitize(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return values.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(line -> !line.isBlank())
            .collect(Collectors.toList());
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
