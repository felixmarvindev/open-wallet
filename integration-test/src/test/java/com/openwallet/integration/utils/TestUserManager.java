package com.openwallet.integration.utils;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages test users that are pre-created before tests run.
 * This eliminates the need to register users during test execution,
 * making tests faster and more reliable.
 * 
 * Usage:
 * <pre>
 * {@code
 * // In @BeforeAll or @BeforeEach
 * TestUserManager userManager = new TestUserManager(authClient);
 * TestUser user = userManager.createUser("testuser", "test@example.com");
 * 
 * // In tests
 * String token = userManager.getToken("testuser");
 * }
 * </pre>
 */
@Slf4j
@Getter
public class TestUserManager {
    
    private final TestHttpClient authClient;
    private final Map<String, TestUser> users = new ConcurrentHashMap<>();
    
    public TestUserManager(TestHttpClient authClient) {
        this.authClient = authClient;
    }
    
    /**
     * Creates a test user and logs them in.
     * 
     * @param username Username for the test user
     * @param email Email for the test user
     * @return TestUser with credentials and token
     * @throws RuntimeException if user creation or login fails
     */
    public TestUser createUser(String username, String email) {
        return createUser(username, email, "Test123!@#");
    }
    
    /**
     * Creates a test user with custom password and logs them in.
     * 
     * @param username Username for the test user
     * @param email Email for the test user
     * @param password Password for the test user
     * @return TestUser with credentials and token
     * @throws RuntimeException if user creation or login fails
     */
    public TestUser createUser(String username, String email, String password) {
        if (users.containsKey(username)) {
            log.debug("User {} already exists, returning existing user", username);
            return users.get(username);
        }
        
        log.info("Creating test user: username={}, email={}", username, email);
        
        try {
            // Register user
            Map<String, String> registerRequest = new HashMap<>();
            registerRequest.put("username", username);
            registerRequest.put("email", email);
            registerRequest.put("password", password);
            
            TestHttpClient.HttpResponse registerResponse = authClient.post("/api/v1/auth/register", registerRequest);
            
            if (registerResponse.getStatusCode() != 201) {
                throw new RuntimeException(
                    String.format("Failed to register user %s: %d - %s", 
                        username, registerResponse.getStatusCode(), registerResponse.getBody()));
            }
            
            Map<String, Object> registerBody = authClient.parseJson(registerResponse.getBody());
            String userId = (String) registerBody.get("userId");
            
            // Login to get token
            Map<String, String> loginRequest = new HashMap<>();
            loginRequest.put("username", username);
            loginRequest.put("password", password);
            
            TestHttpClient.HttpResponse loginResponse = authClient.post("/api/v1/auth/login", loginRequest);
            
            if (loginResponse.getStatusCode() != 200) {
                throw new RuntimeException(
                    String.format("Failed to login user %s: %d - %s", 
                        username, loginResponse.getStatusCode(), loginResponse.getBody()));
            }
            
            Map<String, Object> loginBody = authClient.parseJson(loginResponse.getBody());
            String accessToken = (String) loginBody.get("accessToken");
            
            if (accessToken == null) {
                throw new RuntimeException("Access token not found in login response for user: " + username);
            }
            
            TestUser user = new TestUser(username, email, password, userId, accessToken);
            users.put(username, user);
            
            log.info("✓ Test user created and logged in: username={}, userId={}", username, userId);
            return user;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test user: " + username, e);
        }
    }
    
    /**
     * Gets a test user by username.
     * 
     * @param username Username
     * @return TestUser, or null if not found
     */
    public TestUser getUser(String username) {
        return users.get(username);
    }
    
    /**
     * Gets the access token for a test user.
     * 
     * @param username Username
     * @return Access token, or null if user not found
     */
    public String getToken(String username) {
        TestUser user = users.get(username);
        return user != null ? user.getAccessToken() : null;
    }
    
    /**
     * Checks if a user exists.
     * 
     * @param username Username
     * @return true if user exists
     */
    public boolean hasUser(String username) {
        return users.containsKey(username);
    }
    
    /**
     * Clears all test users (does not delete from Keycloak).
     */
    public void clear() {
        users.clear();
    }
    
    /**
     * Represents a test user with credentials and token.
     */
    @Getter
    public static class TestUser {
        private final String username;
        private final String email;
        private final String password;
        private final String userId;
        private final String accessToken;
        
        public TestUser(String username, String email, String password, String userId, String accessToken) {
            this.username = username;
            this.email = email;
            this.password = password;
            this.userId = userId;
            this.accessToken = accessToken;
        }
    }
}

