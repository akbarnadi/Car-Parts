package com.carparts.platform.auth;

import java.util.Set;
import java.util.UUID;

public record AuthPrincipal(
        UUID id,
        String username,
        String email,
        String phone,
        String displayName,
        Set<String> roles,
        Integer managerLevel,
        boolean root
) {
    public boolean hasRole(String role) { return roles.contains(role); }
}
