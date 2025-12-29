package com.integrixs.shared.util;

import java.util.UUID;

public class SecurityContextHelper {

    // Fallback methods for shared module - backend will override with Spring Security implementation

    public static String getCurrentUsername() {
        // This will be overridden in backend with actual Spring Security implementation
        return "Administrator";
    }

    public static UUID getCurrentUserId() {
        // This will be overridden in backend with actual Spring Security implementation
        return UUID.fromString("00000000-0000-0000-0000-000000000001");
    }

    public static String getCurrentUserIdAsString() {
        return getCurrentUserId().toString();
    }

    public static boolean isAuthenticated() {
        // This will be overridden in backend with actual Spring Security implementation
        return true;
    }

    public static boolean hasRole(String role) {
        // This will be overridden in backend with actual Spring Security implementation
        return true;
    }

    public static boolean hasAnyRole(String... roles) {
        // This will be overridden in backend with actual Spring Security implementation
        return true;
    }
}