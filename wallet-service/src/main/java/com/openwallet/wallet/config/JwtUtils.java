package com.openwallet.wallet.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Utility class for extracting information from JWT tokens.
 */
public class JwtUtils {

    /**
     * Extracts the user ID (sub claim) from the current JWT authentication.
     *
     * @return User ID from JWT sub claim, or null if not authenticated
     */
    public static String getUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken) {
            JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) authentication;
            Jwt jwt = jwtAuth.getToken();
            return jwt.getSubject();
        }
        return null;
    }

    /**
     * Extracts the user ID (sub claim) from a JWT token.
     *
     * @param jwt JWT token
     * @return User ID from JWT sub claim
     */
    public static String getUserId(Jwt jwt) {
        return jwt != null ? jwt.getSubject() : null;
    }

    /**
     * Gets the raw JWT token value from the current authentication.
     * Used for passing the token to other services.
     *
     * @return Raw JWT token value, or null if not authenticated
     */
    public static String getTokenValue() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken) {
            JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) authentication;
            Jwt jwt = jwtAuth.getToken();
            return jwt.getTokenValue();
        }
        return null;
    }
}

