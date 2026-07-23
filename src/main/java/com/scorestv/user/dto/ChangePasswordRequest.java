package com.scorestv.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Mevcut sifreyle dogrulanip yeni sifre belirleme istegi. */
public record ChangePasswordRequest(

        @NotBlank
        String currentPassword,

        @NotBlank
        @Size(min = 8, max = 72, message = "Şifre 8-72 karakter olmalı")
        String newPassword
) {}
