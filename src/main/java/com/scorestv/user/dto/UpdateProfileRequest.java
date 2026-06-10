package com.scorestv.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Kullanicinin kendi profilini guncellemesi icin govde. */
public record UpdateProfileRequest(

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
