package com.scorestv.security;

import com.scorestv.user.Role;

/**
 * Gecerli istegin kimligi. SecurityContext'te principal olarak tutulur,
 * controller'larda @AuthenticationPrincipal ile enjekte edilir.
 */
public record CurrentUser(
        Long id,
        String email,
        Role role
) {}
