package com.scorestv.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(

        @NotBlank
        String token,

        @NotBlank
        @Size(min = 8, max = 72, message = "Şifre 8-72 karakter olmalı")
        String newPassword
) {}
