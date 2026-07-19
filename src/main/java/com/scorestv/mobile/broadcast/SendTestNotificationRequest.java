package com.scorestv.mobile.broadcast;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Yalnizca belirli bir HESABIN cihazlarina TEST bildirimi gonderme istegi.
 * E-posta, mobil uygulamada o hesapla giris yapmis cihazlari (app_user_id)
 * hedeflemek icin kullanilir; herkese gitmez. Genel broadcast'ten farkli
 * olarak senkron gonderilir (tek kullanici, birkac cihaz).
 */
public record SendTestNotificationRequest(
        @NotBlank(message = "E-posta bos olamaz")
        @Email(message = "Gecerli bir e-posta girin")
        @Size(max = 320, message = "E-posta en fazla 320 karakter")
        String email,

        @NotBlank(message = "Baslik bos olamaz")
        @Size(max = 200, message = "Baslik en fazla 200 karakter")
        String title,

        @NotBlank(message = "Metin bos olamaz")
        @Size(max = 1000, message = "Metin en fazla 1000 karakter")
        String body,

        @Size(max = 1000, message = "Baglanti en fazla 1000 karakter")
        String link
) {
}
