package com.scorestv.football.image;

/**
 * Görsel aynalama işinin özeti.
 *
 * @param teamLogos     MinIO'ya aynalanan takım logosu sayısı
 * @param leagueLogos   aynalanan lig logosu sayısı
 * @param countryFlags  aynalanan ülke bayrağı sayısı
 * @param playerPhotos  aynalanan oyuncu fotoğrafı sayısı
 * @param coachPhotos   aynalanan teknik direktör fotoğrafı sayısı
 * @param venueImages   aynalanan stadyum görseli sayısı
 */
public record ImageMirrorResult(
        int teamLogos,
        int leagueLogos,
        int countryFlags,
        int playerPhotos,
        int coachPhotos,
        int venueImages
) {
}
