package com.scorestv.user.dto;

import com.scorestv.user.Role;
import com.scorestv.user.User;

import java.time.LocalDate;
import java.time.Period;

public record UserResponse(
        Long id,
        String email,
        String displayName,
        Role role,
        LocalDate birthDate,
        Integer age,
        String country,
        // Yerel sifresi var mi? Google ile olusturulan hesaplarda password null
        // olur; istemci "Sifre Degistir" bolumunu buna gore gosterir/gizler.
        boolean hasPassword,
        // Profil resmi (avatar) herkese acik URL'i; yoksa null (istemci ad bas
        // harflerini gosterir). URL, avatar_key'den runtime'da turetilir.
        String avatarUrl
) {
    /** Avatar URL'i bilinmeden — avatarUrl null gecer (or. admin listeleri). */
    public static UserResponse from(User user) {
        return from(user, null);
    }

    /** Avatar URL'i cagiran tarafindan (storage.publicUrl(key)) hesaplanir. */
    public static UserResponse from(User user, String avatarUrl) {
        Integer age = user.getBirthDate() != null
                ? Period.between(user.getBirthDate(), LocalDate.now()).getYears()
                : null;
        boolean hasPassword = user.getPassword() != null && !user.getPassword().isBlank();
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole(),
                user.getBirthDate(),
                age,
                user.getCountry(),
                hasPassword,
                avatarUrl
        );
    }
}
