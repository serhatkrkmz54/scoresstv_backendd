package com.scorestv.mobile.fcm;

import java.util.List;
import java.util.stream.Collectors;

/**
 * FCM topic adlandırma SÖZLEŞMESİ — backend (gönderim) ve mobil (abonelik)
 * BİREBİR aynı isimleri kullanmalı, yoksa bildirim teslim edilmez.
 *
 * <p><b>Topic'ler:</b>
 * <ul>
 *   <li>Takım olayı: {@code t{teamId}_{suffix}} (örn. {@code t549_gol})</li>
 *   <li>Favori maç (tüm olaylar): {@code fix{fixtureId}} (örn. {@code fix12345})</li>
 * </ul>
 *
 * <p><b>suffix</b> mobil pref anahtarlarıyla aynıdır: gol, kirmizi, penalti,
 * basladi, bitti, kadro, ilkYari, ikinciYari. Outbox {@code notifType} alanı
 * "ht"/"2yari" kullandığından {@link #suffixFor} ile çevrilir.
 *
 * <p>FCM topic adı {@code [a-zA-Z0-9-_.~%]+} olmalı — bu isimler uyumlu.
 */
public final class FcmTopics {

    private FcmTopics() {}

    public static String teamEvent(long teamId, String suffix) {
        return "t" + teamId + "_" + suffix;
    }

    public static String favoriteFixture(long fixtureId) {
        return "fix" + fixtureId;
    }

    /** Basketbol takım olayı: {@code bt{teamId}_{suffix}} (suffix: basladi/ceyrek/bitti). */
    public static String basketballTeamEvent(long teamId, String suffix) {
        return "bt" + teamId + "_" + suffix;
    }

    /** Basketbol favori maç (tüm olaylar): {@code bg{gameId}}. */
    public static String basketballGame(long gameId) {
        return "bg" + gameId;
    }

    /** Voleybol takım olayı: {@code vt{teamId}_{suffix}} (suffix: basladi/set/bitti). */
    public static String volleyballTeamEvent(long teamId, String suffix) {
        return "vt" + teamId + "_" + suffix;
    }

    /** Voleybol favori maç (tüm olaylar): {@code vg{gameId}}. */
    public static String volleyballGame(long gameId) {
        return "vg" + gameId;
    }

    /** Outbox notifType → topic suffix (mobil pref anahtarı). */
    public static String suffixFor(String notifType) {
        if (notifType == null) return "";
        return switch (notifType) {
            case "ht" -> "ilkYari";
            case "2yari" -> "ikinciYari";
            default -> notifType; // gol/kirmizi/penalti/basladi/bitti/kadro
        };
    }

    /**
     * ≤5 topic'i FCM condition string'ine cevirir:
     * {@code 't549_gol' in topics || 'fix12345' in topics}.
     */
    public static String orCondition(List<String> topics) {
        return topics.stream()
                .map(t -> "'" + t + "' in topics")
                .collect(Collectors.joining(" || "));
    }
}
