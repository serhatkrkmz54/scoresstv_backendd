package com.scorestv.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Apple ile giriş/kayıt isteği (Google ile aynı desen).
 *
 * <p>{@code identityToken} — Apple'ın imzaladığı JWT; backend doğrular.
 * {@code name} — Apple adı YALNIZCA ilk girişte bir kez döner (token'da YOK);
 * client onu yakalayıp ilk kayıtta gönderir. E-posta gizli relay olabilir.
 * {@code birthDate} ve {@code country} yalnızca İLK kayıtta zorunludur.
 */
public record AppleLoginRequest(

        @NotBlank
        String identityToken,

        /** Apple'ın ilk girişte verdiği görünen ad (varsa). */
        @Size(max = 100)
        String name,

        @Past(message = "Doğum tarihi geçmişte olmalı")
        LocalDate birthDate,

        @Size(max = 100)
        String country
) {}
