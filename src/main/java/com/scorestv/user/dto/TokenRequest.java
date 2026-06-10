package com.scorestv.user.dto;

import jakarta.validation.constraints.NotBlank;

/** /refresh ve /logout endpoint'leri icin ortak govde. */
public record TokenRequest(

        @NotBlank
        String refreshToken
) {}
