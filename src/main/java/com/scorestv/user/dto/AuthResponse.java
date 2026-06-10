package com.scorestv.user.dto;

/**
 * Login / register / refresh sonrasi donen token paketi.
 * expiresIn: access token'in saniye cinsinden gecerlilik suresi.
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {
    public static AuthResponse of(String accessToken, String refreshToken,
                                  long expiresIn, UserResponse user) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresIn, user);
    }
}
