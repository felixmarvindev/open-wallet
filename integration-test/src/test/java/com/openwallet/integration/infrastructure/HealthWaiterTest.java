package com.openwallet.integration.infrastructure;

import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HealthWaiterTest {

    private com.sun.net.httpserver.HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Waiter succeeds when endpoint becomes healthy")
    void waiterSucceeds() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        int port = startServer(exchange -> {
            int n = hits.incrementAndGet();
            int code = n < 2 ? 503 : 200;
            exchange.sendResponseHeaders(code, 0);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write("ok".getBytes());
            }
        });

        HealthWaiter waiter = new HealthWaiter(new OkHttpClient());
        waiter.waitForHealthy("http://localhost:" + port + "/health", Duration.ofSeconds(5), Duration.ofMillis(200));
    }

    @Test
    @DisplayName("Waiter fails when endpoint stays unhealthy")
    void waiterFails() throws Exception {
        int port = startServer(exchange -> {
            exchange.sendResponseHeaders(503, 0);
            exchange.getResponseBody().close();
        });

        HealthWaiter waiter = new HealthWaiter(new OkHttpClient());
        assertThatThrownBy(() ->
                waiter.waitForHealthy("http://localhost:" + port + "/health", Duration.ofSeconds(2), Duration.ofMillis(200)))
                .isInstanceOf(IllegalStateException.class);
    }

    private int startServer(com.sun.net.httpserver.HttpHandler handler) throws IOException {
        server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/health", handler);
        server.start();
        return server.getAddress().getPort();
    }
}


