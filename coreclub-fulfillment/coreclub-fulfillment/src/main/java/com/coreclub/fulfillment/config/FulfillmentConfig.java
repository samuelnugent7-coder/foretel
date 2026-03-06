package com.coreclub.fulfillment.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public record FulfillmentConfig(
    String listenHost,
    int listenPort,
    String sharedToken,
    List<String> allowedIpAddresses,
    int requestTimeoutSeconds,
    int commandTimeoutSeconds,
    boolean logIncomingRequests,
    byte[] encryptionKey
) {

    public static FulfillmentConfig fromConfig(FileConfiguration config) {
        String host = config.getString("listenHost", "0.0.0.0");
        int port = config.getInt("listenPort", 3032);
        String token = config.getString("sharedToken", "change-me");
        List<String> allowed = config.getStringList("allowedIps");
        if (allowed == null) {
            allowed = Collections.emptyList();
        } else {
            allowed = allowed.stream()
                .filter(ip -> ip != null && !ip.isBlank())
                .map(ip -> ip.toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());
        }
        int requestTimeout = Math.max(3, config.getInt("requestTimeoutSeconds", 10));
        int commandTimeout = Math.max(1, config.getInt("commandTimeoutSeconds", 5));
        boolean logRequests = config.getBoolean("logIncomingRequests", true);
        String encodedKey = config.getString("encryptionKey", "").trim();
        if (encodedKey.isBlank()) {
            throw new IllegalStateException("encryptionKey must be configured for webhook encryption");
        }
        byte[] encryptionKey = decodeEncryptionKey(encodedKey);
        return new FulfillmentConfig(host, port, token, allowed, requestTimeout, commandTimeout, logRequests, encryptionKey);
    }

    public boolean requiresIpCheck() {
        return allowedIpAddresses != null && !allowedIpAddresses.isEmpty();
    }

    public byte[] encryptionKey() {
        return encryptionKey == null ? null : encryptionKey.clone();
    }

    private static byte[] decodeEncryptionKey(String base64) {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64);
            if (decoded.length != 32) {
                throw new IllegalStateException("Encryption key must decode to 32 bytes for AES-256-GCM");
            }
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid Base64 encryption key", exception);
        }
    }
}
