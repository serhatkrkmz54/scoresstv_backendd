package com.scorestv.rankings.notify;

/**
 * Bir siralama satirinin gun-bazli sira degisimini tanimlayan duz DTO.
 *
 * <p>Ranking sync servisleri (REPLACE) yazimdan sonra eski/yeni sira'yi
 * kiyaslayip her gercek degisim icin bir {@code RankingChange} uretir;
 * {@link RankingNotificationService} bunlari alarak FCM bildirimi gonderir.
 *
 * <p>Entity DEGIL — async/baska-thread'de guvenle tasinabilsin diye sadece
 * primitif/String alanlar tutar (lazy-init sorunu olmaz).
 *
 * <p>Hedefleme:
 * <ul>
 *   <li>{@code FIFA} / {@code UEFA_COUNTRY} → cihaz ulkesine ({@link #countryCode})
 *       gore.</li>
 *   <li>{@code UEFA_CLUB} → kulup app Team'e isim/kod ile eslenir, takim
 *       takipcilerine gore.</li>
 * </ul>
 */
public record RankingChange(
        Scope scope,
        /** Bildirimde gosterilecek ad (ulke adi ya da kulup adi). */
        String displayName,
        int oldRank,
        int newRank,
        /** FIFA/UEFA_COUNTRY hedefleme; UEFA_CLUB icin eslesme ipucu (ISO-3). */
        String countryCode,
        /** UEFA_CLUB isim eslesmesi icin kisa ad (orn. "Galatasaray"). */
        String clubShortName,
        /** UEFA_CLUB isim eslesmesi icin takim kodu (orn. "GAL"). */
        String teamCode
) {

    public enum Scope { FIFA, UEFA_COUNTRY, UEFA_CLUB }

    /** Sira yukseldi mi? (kucuk sira numarasi = daha iyi). */
    public boolean isUp() {
        return newRank < oldRank;
    }

    public static RankingChange fifa(String name, String countryCode,
                                     int oldRank, int newRank) {
        return new RankingChange(Scope.FIFA, name, oldRank, newRank,
                countryCode, null, null);
    }

    public static RankingChange uefaCountry(String name, String countryCode,
                                            int oldRank, int newRank) {
        return new RankingChange(Scope.UEFA_COUNTRY, name, oldRank, newRank,
                countryCode, null, null);
    }

    public static RankingChange uefaClub(String name, String countryCode,
                                         String clubShortName, String teamCode,
                                         int oldRank, int newRank) {
        return new RankingChange(Scope.UEFA_CLUB, name, oldRank, newRank,
                countryCode, clubShortName, teamCode);
    }
}
