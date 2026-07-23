package com.scorestv.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Kod tabanli (OTP) sifre sifirlama — mobil akis. Kullanici e-postasina gelen
 * 6 haneli kodu ve yeni sifresini gonderir.
 */
public record ResetPasswordCodeRequest(

        @NotBlank
        @Email
        String email,

        @NotBlank
        @Pattern(regexp = "\\d{6}", message = "Kod 6 haneli olmalı")
        String code,

        @NotBlank
        @Size(min = 8, max = 72, message = "Şifre 8-72 karakter olmalı")
        String newPassword
) {}
