package com.coreclub.fulfillment.http;

import com.coreclub.fulfillment.commands.CommandExecutorService;
import com.coreclub.fulfillment.config.FulfillmentConfig;
import com.coreclub.fulfillment.model.EncryptedWebhookPayload;
import com.coreclub.fulfillment.model.FulfillmentRequest;
import com.coreclub.fulfillment.model.FulfillmentResult;
import com.coreclub.fulfillment.model.PackageSyncRequest;
import com.coreclub.fulfillment.store.ProcessedOrderStore;
import com.coreclub.fulfillment.store.PackageRegistry;
import com.coreclub.fulfillment.security.WebhookDecryptor;
import com.coreclub.fulfillment.util.JsonHelper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Minimal embedded HTTP server responsible for receiving fulfillment requests
 * from the Node backend.
 */
public final class FulfillmentHttpServer {
    private static final int MAX_PAYLOAD_PREVIEW_CHARS = 2048;
    private static final String BEARER_PREFIX = "Bearer ";
    private final JavaPlugin plugin;
    private final FulfillmentConfig config;
    private final CommandExecutorService commandExecutorService;
    private final ProcessedOrderStore processedOrderStore;
    private final PackageRegistry packageRegistry;
    private final Runnable commandReloadCallback;
    private final WebhookDecryptor webhookDecryptor;
    private final ExecutorService httpExecutor = Executors.newCachedThreadPool();
    private volatile Map<String, List<String>> commandMap = Collections.emptyMap();
    private HttpServer server;
    private Instant startedAt;

    public FulfillmentHttpServer(
        JavaPlugin plugin,
        FulfillmentConfig config,
        CommandExecutorService commandExecutorService,
        ProcessedOrderStore processedOrderStore,
        PackageRegistry packageRegistry,
        WebhookDecryptor webhookDecryptor,
        Runnable commandReloadCallback
    ) {
        this.plugin = plugin;
        this.config = config;
        this.commandExecutorService = commandExecutorService;
        this.processedOrderStore = processedOrderStore;
        this.packageRegistry = packageRegistry;
        this.webhookDecryptor = webhookDecryptor;
        this.commandReloadCallback = commandReloadCallback;
    }

    public void setCommandMap(Map<String, List<String>> commandMap) {
        this.commandMap = commandMap == null ? Collections.emptyMap() : commandMap;
    }

    public void start() {
        if (config.sharedToken() == null || config.sharedToken().isBlank()) {
            plugin.getLogger().warning("Shared token is not configured; HTTP server disabled");
            return;
        }
        try {
            server = HttpServer.create(new InetSocketAddress(config.listenHost(), config.listenPort()), 0);
            server.createContext("/fulfill", this::handleFulfill);
            server.createContext("/health", this::handleHealth);
            server.createContext("/commands", this::handleCommandSync);
            server.setExecutor(httpExecutor);
            server.start();
            startedAt = Instant.now();
            plugin.getLogger().info(MessageFormat.format(
                "Fulfillment HTTP server listening on {0}:{1}",
                config.listenHost(),
                config.listenPort()
            ));
            int allowedIpCount = config.allowedIpAddresses() == null ? 0 : config.allowedIpAddresses().size();
            plugin.getLogger().info(MessageFormat.format(
                "Command catalog contains {0} entries; allowedIpCount={1}",
                commandMap.size(),
                allowedIpCount
            ));
        } catch (IOException exception) {
            plugin.getLogger().severe("Failed to start HTTP server: " + exception.getMessage());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(1);
            server = null;
            plugin.getLogger().info("Fulfillment HTTP server stopped");
        }
        startedAt = null;
        httpExecutor.shutdown();
    }

    private void handleFulfill(HttpExchange exchange) throws IOException {
        String requestId = nextRequestId();
        long startedNanos = System.nanoTime();
        exchange.getResponseHeaders().set("X-Request-Id", requestId);

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            plugin.getLogger().warning(logPrefix(requestId) + "Rejected non-POST /fulfill request");
            sendJson(exchange, 405, false, "Only POST is supported");
            return;
        }

        if (!isAuthorized(exchange)) {
            plugin.getLogger().warning(logPrefix(requestId) + "Unauthorized /fulfill request");
            sendJson(exchange, 401, false, "Unauthorized");
            return;
        }

        String remoteAddress = resolveRemoteAddress(exchange);
        plugin.getLogger().info(logPrefix(requestId) + "Received /fulfill from " + remoteAddress);

        if (config.requiresIpCheck() && !config.allowedIpAddresses().contains(remoteAddress)) {
            plugin.getLogger().warning(logPrefix(requestId) + "Rejected request from disallowed IP " + remoteAddress);
            sendJson(exchange, 403, false, "IP not allowed");
            return;
        }

        byte[] body = exchange.getRequestBody().readAllBytes();
        String payload = new String(body, StandardCharsets.UTF_8);
        if (config.logIncomingRequests()) {
            plugin.getLogger().info(logPrefix(requestId) + "Payload preview: " + previewPayload(payload));
        }

        String decryptedPayload = payload;
        if (webhookDecryptor != null) {
            EncryptedWebhookPayload encryptedPayload;
            try {
                encryptedPayload = JsonHelper.parseEncryptedPayload(payload);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning(logPrefix(requestId) + "Invalid encrypted payload: " + exception.getMessage());
                sendJson(exchange, 400, false, "Invalid encrypted payload: " + exception.getMessage());
                return;
            }

            if (encryptedPayload == null || !encryptedPayload.isValid()) {
                plugin.getLogger().warning(logPrefix(requestId) + "Encrypted payload missing required fields");
                sendJson(exchange, 400, false, "Encrypted payload missing required fields");
                return;
            }

            try {
                decryptedPayload = webhookDecryptor.decrypt(
                    encryptedPayload.iv(),
                    encryptedPayload.ciphertext(),
                    encryptedPayload.authTag()
                );
                plugin.getLogger().info(logPrefix(requestId) + "Successfully decrypted webhook payload");
            } catch (IllegalStateException exception) {
                plugin.getLogger().warning(logPrefix(requestId) + "Failed to decrypt webhook payload: " + exception.getMessage());
                sendJson(exchange, 400, false, "Unable to decrypt payload");
                return;
            }
        }

        FulfillmentRequest request;
        try {
            request = JsonHelper.parseRequest(decryptedPayload);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning(logPrefix(requestId) + "Invalid JSON payload: " + exception.getMessage());
            sendJson(exchange, 400, false, "Invalid JSON: " + exception.getMessage());
            return;
        }

        if (!request.isValid()) {
            plugin.getLogger().warning(logPrefix(requestId) + "Missing fields in fulfillment payload");
            sendJson(exchange, 400, false, "Missing required fields");
            return;
        }

        if (!Objects.equals(request.token(), config.sharedToken())) {
            plugin.getLogger().warning(logPrefix(requestId) + "Token mismatch for order " + request.orderId());
            sendJson(exchange, 401, false, "Invalid token");
            return;
        }

        String orderId = request.orderId();
        if (processedOrderStore.isProcessed(orderId)) {
            plugin.getLogger().info(logPrefix(requestId) + "Order " + orderId + " already processed; acknowledging");
            logFulfillmentCompletion(requestId, orderId, startedNanos, true, "duplicate");
            sendJson(exchange, 200, true, "Order already fulfilled");
            return;
        }

        List<String> commands = request.resolvedCommands();
        if (commands.isEmpty()) {
            commands = commandMap.get(request.sku().toLowerCase(Locale.ROOT));
        }
        if (commands == null || commands.isEmpty()) {
            plugin.getLogger().warning(logPrefix(requestId) + MessageFormat.format(
                "No commands resolved for order {0} sku {1}",
                orderId,
                request.sku()
            ));
            sendJson(exchange, 404, false, "No commands defined for SKU");
            return;
        }

        plugin.getLogger().info(logPrefix(requestId) + MessageFormat.format(
            "Executing {0} commands for order {1} (player={2}, sku={3})",
            commands.size(),
            orderId,
            request.player(),
            request.sku()
        ));

        CompletableFuture<FulfillmentResult> future = commandExecutorService.execute(request.player(), commands);
        FulfillmentResult result;
        try {
            result = future.get(config.commandTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (Exception exception) {
            future.cancel(true);
            plugin.getLogger().severe(logPrefix(requestId) + "Command execution timeout for order " + orderId + ": " + exception.getMessage());
            logFulfillmentCompletion(requestId, orderId, startedNanos, false, "timeout");
            sendJson(exchange, 504, false, "Command execution timeout");
            return;
        }

        if (!result.success()) {
            plugin.getLogger().severe(logPrefix(requestId) + "Command execution failed for order " + orderId + ": " + result.message());
            logFulfillmentCompletion(requestId, orderId, startedNanos, false, result.message());
            sendJson(exchange, 500, false, result.message());
            return;
        }

        processedOrderStore.markProcessed(orderId);
        plugin.getLogger().info(logPrefix(requestId) + "Fulfillment completed for order " + orderId);
        logFulfillmentCompletion(requestId, orderId, startedNanos, true, null);
        sendJson(exchange, 200, true, "Fulfilled " + orderId);
    }

    private void handleCommandSync(HttpExchange exchange) throws IOException {
        String requestId = nextRequestId();
        long startedNanos = System.nanoTime();
        exchange.getResponseHeaders().set("X-Request-Id", requestId);

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            plugin.getLogger().warning(logPrefix(requestId) + "Rejected non-POST /commands request");
            sendJson(exchange, 405, false, "Only POST is supported");
            return;
        }

        if (packageRegistry == null) {
            plugin.getLogger().severe(logPrefix(requestId) + "Package registry unavailable");
            sendJson(exchange, 503, false, "Package registry unavailable");
            return;
        }

        String payload = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        PackageSyncRequest request;
        try {
            request = JsonHelper.parsePackageSyncRequest(payload);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning(logPrefix(requestId) + "Invalid package sync payload: " + exception.getMessage());
            sendJson(exchange, 400, false, exception.getMessage());
            return;
        }

        if (!request.isValid() || !Objects.equals(request.token(), config.sharedToken())) {
            plugin.getLogger().warning(logPrefix(requestId) + "Package sync rejected due to token mismatch");
            sendJson(exchange, 401, false, "Invalid token");
            return;
        }

        try {
            packageRegistry.replacePackages(request.packages());
            if (commandReloadCallback != null) {
                commandReloadCallback.run();
            }
            int packageCount = request.packages() == null ? 0 : request.packages().size();
            plugin.getLogger().info(logPrefix(requestId) + MessageFormat.format(
                "Synchronized {0} packages with bridge", packageCount));
            sendJson(exchange, 200, true, MessageFormat.format("Synced {0} packages", packageCount));
        } catch (IOException exception) {
            plugin.getLogger().severe(logPrefix(requestId) + "Failed to persist package registry: " + exception.getMessage());
            sendJson(exchange, 500, false, "Unable to persist package catalog");
        }
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        String requestId = nextRequestId();
        exchange.getResponseHeaders().set("X-Request-Id", requestId);

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            plugin.getLogger().warning(logPrefix(requestId) + "Rejected non-GET /health request");
            sendJson(exchange, 405, false, "Only GET is supported");
            return;
        }

        if (!isAuthorized(exchange)) {
            plugin.getLogger().warning(logPrefix(requestId) + "Unauthorized /health request");
            sendJson(exchange, 401, false, "Unauthorized");
            return;
        }

        long uptimeSeconds = 0;
        if (startedAt != null) {
            uptimeSeconds = Math.max(0, Duration.between(startedAt, Instant.now()).getSeconds());
        }

        Map<String, Object> extras = new HashMap<>();
        extras.put("uptimeSeconds", uptimeSeconds);
        extras.put("processedOrders", processedOrderStore.snapshot().size());
        extras.put("version", plugin.getDescription().getVersion());

        plugin.getLogger().info(logPrefix(requestId) + "Health probe answered with uptimeSeconds=" + uptimeSeconds);
        sendJson(exchange, 200, true, "OK", extras);
    }

    private boolean isAuthorized(HttpExchange exchange) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader == null || authHeader.isBlank()) {
            return false;
        }
        if (!authHeader.startsWith(BEARER_PREFIX)) {
            return false;
        }
        String providedToken = authHeader.substring(BEARER_PREFIX.length()).trim();
        return Objects.equals(providedToken, config.sharedToken());
    }

    private static String previewPayload(String payload) {
        if (payload == null) {
            return "<empty>";
        }
        if (payload.length() <= MAX_PAYLOAD_PREVIEW_CHARS) {
            return payload;
        }
        return payload.substring(0, MAX_PAYLOAD_PREVIEW_CHARS) + "...";
    }

    private static String resolveRemoteAddress(HttpExchange exchange) {
        if (exchange == null || exchange.getRemoteAddress() == null || exchange.getRemoteAddress().getAddress() == null) {
            return "unknown";
        }
        return exchange.getRemoteAddress().getAddress().getHostAddress().toLowerCase(Locale.ROOT);
    }

    private static String logPrefix(String requestId) {
        return "[req=" + requestId + "] ";
    }

    private static String nextRequestId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static long elapsedMillis(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    private void logFulfillmentCompletion(String requestId, String orderId, long startedNanos, boolean success, String note) {
        plugin.getLogger().info(MessageFormat.format(
            "{0}Completed /fulfill request (orderId={1}, success={2}, durationMs={3}){4}",
            logPrefix(requestId),
            orderId == null ? "<unknown>" : orderId,
            success,
            elapsedMillis(startedNanos),
            note == null ? "" : " - " + note
        ));
    }

    private void sendJson(HttpExchange exchange, int status, boolean success, String message) throws IOException {
        sendJson(exchange, status, success, message, null);
    }

    private void sendJson(HttpExchange exchange, int status, boolean success, String message, Map<String, Object> extras) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        String json = JsonHelper.encodeResponse(success, message, extras);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
