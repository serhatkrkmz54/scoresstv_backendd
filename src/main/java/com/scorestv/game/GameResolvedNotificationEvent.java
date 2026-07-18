package com.scorestv.game;

import java.util.List;

/**
 * Bir oyun yarismasi cozuldugunde (kazananlar belirlenip coin dagitildiktan
 * sonra) yayinlanir. {@link GameResultNotificationService} bunu
 * {@code AFTER_COMMIT} + {@code @Async} dinler ve her kazanan kullaniciya
 * kisisel FCM push gonderir ("3/5 tahminin tuttu, +820 Scores Puani").
 *
 * <p>Yalnizca coin kazanan (en az 1 dogru) kullanicilar listeye eklenir —
 * negatif/spam push olmasin.
 */
public record GameResolvedNotificationEvent(
        Long competitionId,
        String titleTr,
        String titleEn,
        List<UserResult> results) {

    /** Tek kullanicinin bu yarismadaki ozeti. */
    public record UserResult(
            Long userId,
            int correct,
            int graded,
            long coins) {}
}
