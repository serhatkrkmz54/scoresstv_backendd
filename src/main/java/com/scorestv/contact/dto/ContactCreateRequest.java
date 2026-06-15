package com.scorestv.contact.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * İletişim formu gönderim isteği (public).
 */
public record ContactCreateRequest(
        @NotBlank(message = "Ad zorunlu.")
        @Size(max = 120, message = "Ad 120 karakteri aşamaz.")
        String name,

        @NotBlank(message = "E-posta zorunlu.")
        @Email(message = "Geçerli bir e-posta girin.")
        @Size(max = 180, message = "E-posta 180 karakteri aşamaz.")
        String email,

        @Size(max = 160, message = "Konu 160 karakteri aşamaz.")
        String subject,

        @NotBlank(message = "Mesaj boş olamaz.")
        @Size(max = 4000, message = "Mesaj 4000 karakteri aşamaz.")
        String message
) {}
