package com.scorestv.game;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Davet / referans DTO'lari. */
public final class ReferralDtos {

    private ReferralDtos() {
    }

    /** Kullanicinin davet bilgisi — kodu + kac kisi davet etti + kazanilan puan. */
    public record ReferralInfo(String code, long invitedCount, long pointsEarned,
                               int rewardEach) {
    }

    /** Davet kodu kullanma istegi. */
    public record RedeemRequest(
            @NotBlank(message = "Davet kodu bos olamaz")
            @Size(max = 12, message = "Gecersiz kod")
            String code) {
    }

    /** Kod kullanma sonucu — her iki tarafa verilen puan. */
    public record RedeemResult(int awarded) {
    }
}
