package com.scorestv.user;

import com.scorestv.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String email;

    // Google ile olusturulan hesaplarda null olabilir.
    @Column
    private String password;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(length = 100)
    private String country;

    /** Google 'sub' degeri; Google ile giris yapabilen kullanicilarda dolu. */
    @Column(name = "google_id", length = 64, unique = true)
    private String googleId;

    /** Apple 'sub' degeri; Apple ile giris yapabilen kullanicilarda dolu. */
    @Column(name = "apple_id", length = 255, unique = true)
    private String appleId;

    /**
     * Kullanicinin benzersiz davet (referans) kodu — arkadasini davet etmek
     * icin. Talep edildiginde (ilk goruntulemede) lazily uretilir; o zamana
     * kadar null.
     */
    @Column(name = "referral_code", length = 12, unique = true)
    private String referralCode;

    /**
     * Kullanicinin yukledigi profil resminin (avatar) MinIO nesne anahtari.
     * Herkese acik URL {@code MinioStorageService.publicUrl(avatarKey)} ile
     * turetilir. null ise avatar yok — istemci ad bas harflerini gosterir.
     */
    @Column(name = "avatar_key", length = 255)
    private String avatarKey;
}
