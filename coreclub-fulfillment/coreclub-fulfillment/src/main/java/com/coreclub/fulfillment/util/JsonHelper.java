package com.coreclub.fulfillment.util;

import com.coreclub.fulfillment.model.EncryptedWebhookPayload;
import com.coreclub.fulfillment.model.FulfillmentRequest;
import com.coreclub.fulfillment.model.PackageSyncRequest;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.util.Map;

public final class JsonHelper {
    private static final Gson GSON = new GsonBuilder()
        .disableHtmlEscaping()
        .create();

    private JsonHelper() {
    }

    public static FulfillmentRequest parseRequest(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("JSON body is empty");
        }
        try {
            return GSON.fromJson(json, FulfillmentRequest.class);
        } catch (JsonSyntaxException exception) {
            throw new IllegalArgumentException("Invalid JSON payload", exception);
        }
    }

    public static PackageSyncRequest parsePackageSyncRequest(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("JSON body is empty");
        }
        try {
            return GSON.fromJson(json, PackageSyncRequest.class);
        } catch (JsonSyntaxException exception) {
            throw new IllegalArgumentException("Invalid JSON payload", exception);
        }
    }

    public static EncryptedWebhookPayload parseEncryptedPayload(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("JSON body is empty");
        }
        try {
            return GSON.fromJson(json, EncryptedWebhookPayload.class);
        } catch (JsonSyntaxException exception) {
            throw new IllegalArgumentException("Invalid JSON payload", exception);
        }
    }

    public static String encodeResponse(boolean success, String message) {
        return encodeResponse(success, message, null);
    }

    public static String encodeResponse(boolean success, String message, Map<String, Object> extras) {
        JsonObject root = new JsonObject();
        root.addProperty("success", success);
        root.addProperty("message", message == null ? "" : message);
        if (extras != null) {
            extras.forEach((key, value) -> root.add(key, GSON.toJsonTree(value)));
        }
        return GSON.toJson(root);
    }
}
