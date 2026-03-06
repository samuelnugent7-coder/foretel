package dev.copilot.unicode.pack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PackHttpServer implements AutoCloseable {

    private final Logger logger;
    private HttpServer server;
    private Path currentFile;
    private String currentRoute;
    private ExecutorService executor;

    public PackHttpServer(final Logger logger) {
        this.logger = logger;
    }

    public void start(final InetSocketAddress address, final Path file, final String route) throws IOException {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(route, "route");
        stop();
        this.currentFile = file;
        this.currentRoute = route;
        this.server = HttpServer.create(address, 0);
        this.server.createContext(route, new FileHandler());
        this.executor = Executors.newCachedThreadPool();
        this.server.setExecutor(this.executor);
        this.server.start();
        this.logger.info(() -> "Serving emoji resource pack on http://" + address.getHostString() + ":" + address.getPort() + route);
    }

    @Override
    public void close() {
        stop();
    }

    public void stop() {
        if (this.server != null) {
            this.server.stop(0);
            this.server = null;
            this.logger.info("Stopped emoji resource pack HTTP server");
        }
        if (this.executor != null) {
            this.executor.shutdownNow();
            this.executor = null;
        }
    }

    private final class FileHandler implements HttpHandler {
        @Override
        public void handle(final HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            if (currentFile == null || !Files.exists(currentFile)) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            exchange.getResponseHeaders().add("Content-Type", "application/zip");
            final long length = Files.size(currentFile);
            exchange.sendResponseHeaders(200, length);
            try (OutputStream output = exchange.getResponseBody(); InputStream input = Files.newInputStream(currentFile)) {
                input.transferTo(output);
            } catch (final IOException ex) {
                logger.log(Level.WARNING, "Failed to stream resource pack", ex);
            } finally {
                exchange.close();
            }
        }
    }
}
