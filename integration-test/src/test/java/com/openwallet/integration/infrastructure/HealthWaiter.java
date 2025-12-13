package com.openwallet.integration.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.time.Duration;

/**
 * Simple utility to poll a health endpoint (e.g., /actuator/health) until
 * it returns HTTP 200 or a timeout occurs.
 */
@Slf4j
@RequiredArgsConstructor
public class HealthWaiter {

    private final OkHttpClient client;

    /**
     * Polls the given URL until it returns 200 OK or the timeout elapses.
     *
     * @param url            health endpoint URL
     * @param timeout        overall timeout
     * @param retryInterval  delay between retries
     * @throws IllegalStateException if the endpoint does not become healthy
     */
    public void waitForHealthy(String url, Duration timeout, Duration retryInterval) {
        long deadline = System.nanoTime() + timeout.toNanos();
        int attempt = 0;

        while (System.nanoTime() < deadline) {
            attempt++;
            try (Response response = client.newCall(new Request.Builder().url(url).build()).execute()) {
                int code = response.code();
                if (code == 200) {
                    log.info("Health check passed for {} on attempt {}", url, attempt);
                    return;
                }
                log.warn("Health check attempt {} for {} returned {}", attempt, url, code);
            } catch (IOException e) {
                log.warn("Health check attempt {} for {} failed: {}", attempt, url, e.getMessage());
            }

            try {
                Thread.sleep(retryInterval.toMillis());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Health wait interrupted", ie);
            }
        }

        throw new IllegalStateException("Health check did not pass within " + timeout.toSeconds() + "s for " + url);
    }
}


