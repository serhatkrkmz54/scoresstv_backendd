package com.scorestv.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record RegisterRequest(

        @NotBlank
        @Email(message = "Geçerli bir e-posta adresi girin")
        String email,

        @NotBlank
        @Size(min = 3, max = 72, message = "Şifre 3-72 karakter olmalı")
        String password,

        @NotBlank
        @Size(max = 100)
        String displayName,

        @NotNull(message = "Doğum tarihi zorunlu")
        @Past(message = "Doğum tarihi geçmişte olmalı")
        LocalDate birthDate,

        @NotBlank(message = "Ülke zorunlu")
        @Size(max = 100)
        String country
) {}
