package com.scorestv.user.dto;

import com.scorestv.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * ADMIN tarafindan yeni kullanici olusturma istegi.
 * role: ADMIN / EDITOR / USER. birthDate ve country opsiyoneldir.
 */
public record CreateUserRequest(

        @NotBlank
        @Email(message = "Geçerli bir e-posta adresi girin")
        String email,

        @NotBlank
        @Size(min = 3, max = 72, message = "Şifre 3-72 karakter olmalı")
        String password,

        @NotBlank
        @Size(max = 100)
        String displayName,

        @NotNull(message = "Rol zorunlu")
        Role role,

        @Past(message = "Doğum tarihi geçmişte olmalı")
        LocalDate birthDate,

        @Size(max = 100)
        String country
) {}
