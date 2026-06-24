package com.ledgermind.ledgermindbackend.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class SecurityUtils {

    private SecurityUtils() {}

    /**
     * Returns the authenticated user's UUID from the current request's
     * security context. Throws if called from an unauthenticated context
     * (should never happen on secured endpoints).
     */
    public static UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user in security context");
        }

        return (UUID) auth.getPrincipal();
    }
}