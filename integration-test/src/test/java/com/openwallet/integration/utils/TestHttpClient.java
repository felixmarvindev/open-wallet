package com.openwallet.integration.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.util.Map;

/**
 * HTTP client utility for integration tests.
 * Provides convenient methods for making API calls and handling JSON.
 */
@Slf4j
public class TestHttpClient {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public TestHttpClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = new OkHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Makes a POST request with JSON body.
     */
    public HttpResponse post(String path, Object body) throws IOException {
        return post(path, body, null);
    }

    /**
     * Makes a POST request with JSON body and optional authorization token.
     */
    public HttpResponse post(String path, Object body, String authToken) throws IOException {
        String jsonBody = objectMapper.writeValueAsString(body);
        
        Request.Builder requestBuilder = new Request.Builder()
                .url(baseUrl + path)
                .post(RequestBody.create(jsonBody, MediaType.get("application/json")))
                .addHeader("Content-Type", "application/json");
        
        if (authToken != null) {
            requestBuilder.addHeader("Authorization", "Bearer " + authToken);
        }
        
        Request request = requestBuilder.build();
        log.debug("POST {} -> {}", path, jsonBody);
        
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : null;
            log.debug("Response: {} {}", response.code(), responseBody);
            
            return new HttpResponse(response.code(), responseBody);
        }
    }

    /**
     * Makes a GET request.
     */
    public HttpResponse get(String path) throws IOException {
        return get(path, null);
    }

    /**
     * Makes a GET request with optional authorization token.
     */
    public HttpResponse get(String path, String authToken) throws IOException {
        Request.Builder requestBuilder = new Request.Builder()
                .url(baseUrl + path)
                .get();
        
        if (authToken != null) {
            requestBuilder.addHeader("Authorization", "Bearer " + authToken);
        }
        
        Request request = requestBuilder.build();
        log.debug("GET {}", path);
        
        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : null;
            log.debug("Response: {} {}", response.code(), responseBody);
            
            return new HttpResponse(response.code(), responseBody);
        }
    }

    /**
     * Parses JSON response to a Map.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> parseJson(String json) throws IOException {
        return objectMapper.readValue(json, Map.class);
    }

    /**
     * Parses JSON response to a List of Maps (for array responses).
     */
    @SuppressWarnings("unchecked")
    public java.util.List<Map<String, Object>> parseJsonArray(String json) throws IOException {
        return objectMapper.readValue(json, java.util.List.class);
    }

    /**
     * HTTP response wrapper.
     */
    public static class HttpResponse {
        private final int statusCode;
        private final String body;

        public HttpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getBody() {
            return body;
        }

        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }
    }
}


