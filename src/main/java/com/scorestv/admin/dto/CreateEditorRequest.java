package com.scorestv.admin.dto;

import com.scorestv.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Yeni editör/yönetici olusturma istegi (ADMIN). */
public record CreateEditorRequest(

        @NotBlank
        @Email(message = "Geçerli bir e-posta adresi girin")
        String email,

        @NotBlank
        @Size(max = 100)
        String displayName,

        @NotBlank
        @Size(min = 6, max = 72, message = "Şifre 6-72 karakter olmalı")
        String password,

        @NotNull(message = "Rol zorunlu")
        Role role
) {}
