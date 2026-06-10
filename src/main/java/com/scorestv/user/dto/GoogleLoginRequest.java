package com.scorestv.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Google ID token ile giris/kayit istegi.
 * birthDate ve country yalnizca ILK kayitta zorunludur; mevcut bir kullanici
 * Google ile giris yaparken bu alanlar bos birakilabilir.
 */
public record GoogleLoginRequest(

        @NotBlank
        String idToken,

        @Past(message = "Doğum tarihi geçmişte olmalı")
        LocalDate birthDate,

        @Size(max = 100)
        String country
) {}
