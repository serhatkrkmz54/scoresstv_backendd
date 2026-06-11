package com.scorestv.football.web.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * Maç detay sayfasının yanıtı.
 *
 * <p>Phase 1 alanları: maç özeti + stadyum + lig referansı + hakem + SEO paketi.
 * Sonraki fazlarda eklenecekler (events, lineups, statistics, playerStats,
 * headToHead, standings, injuries, predictions) bu record'a yeni alan olarak
 * eklenir; mevcut alan/sıralar değişmez — backward compatible kalır.
 */
public record MatchDetailResponse(
        Long id,
        /** SEO slug — frontend URL'i ile aynı. */
        String slug,
        /** Tur/aşama (örn. "Hafta 17"); dile göre çevrilmiş. */
        String round,
        Instant kickoff,
        Instant lastSyncedAt,
        FixtureSummary.Status status,
        FixtureSummary.Team homeTeam,
        FixtureSummary.Team awayTeam,
        FixtureSummary.Score score,
        Venue venue,
        LeagueRef league,
        /** Hakem adı; bilinmiyorsa null. */
        String referee,
        /**
         * Maç olayları zaman çizelgesi (gol, kart, oyuncu değişikliği, VAR).
         * Dakikaya göre sıralı. Maç başlamadıysa boş liste.
         */
        List<EventSummary> events,
        /**
         * Takım kadroları — ev sahibi önce, deplasman sonra. Henüz açıklanmadıysa
         * boş liste (frontend "Kadro maça 20-40 dk kala açıklanır" göstergesi
         * gösterebilir).
         */
        List<LineupView> lineups,
        /**
         * Takım istatistikleri — her satırda type + home + away karşılaştırması.
         * Standart API sırasında: Shots on Goal, Total Shots, ... Ball Possession,
         * Cards, Total passes, expected_goals. Maç başlamadıysa boş liste.
         */
        List<StatisticView> statistics,
        /**
         * Oyuncu bazlı maç performansı — ev sahibi önce, deplasman sonra.
         * Her takım için ~14-20 oyuncu (ilk 11 + oynayan yedekler).
         * Maç başlamadıysa boş liste.
         */
        List<PlayerStatGroup> playerStats,
        /**
         * İki takım arasındaki geçmiş (oynanmış) karşılaşmalar — yeni → eski,
         * en fazla 10 maç. DB'de yoksa boş döner; admin sync ile pre-fetch
         * edilebilir.
         */
        List<H2hFixtureView> headToHead,
        /**
         * Bu maçın liginin sezonluk puan durumu — gruplara göre. Tek-gruplu
         * liglerde tek eleman; gruplu turnuvalarda (Şampiyonlar Ligi, Copa,
         * EURO vs.) her grup ayrı eleman, içinde sıralı takım satırları.
         */
        List<StandingsGroup> standings,
        /**
         * Sakatlık / cezalı liste — ev sahibi önce, deplasman sonra.
         * Bilinmiyorsa veya yoksa boş liste.
         */
        List<InjuryGroup> injuries,
        /**
         * Maç tahmini (kazanan, yüzde, karşılaştırma). Bulunamazsa null
         * (bazı liglerde API tahmin üretmez).
         */
        PredictionView prediction,
        /**
         * TV yayin bilgisi — bu mac kullanicinin ulkesinde hangi kanal(lar)da
         * yayinlanir. Mac override > lig default > bos. Frontend {@code
         * ?country=} parametresi ile filtre eder.
         */
        List<com.scorestv.broadcasts.BroadcastsService.BroadcastView> broadcasts,
        /**
         * Kupa eleme aşaması bracket'i — bu maç bir kupa ligindeyse ve
         * o sezonun knockout fixture'ları varsa dolu gelir; lig maçlarinda
         * veya knockout fixture'ları yoksa {@code null}.
         */
        BracketView bracket,
        MatchSeoResponse seo,
        /** Bilyoner iddaa oranları; eşleşme yoksa veya özellik kapalıysa null. */
        com.scorestv.bilyoner.MatchOdds odds
) implements Serializable {

    /** Stadyum detayı — özet listesinden daha zengin (kapasite + zemin). */
    public record Venue(
            Long id,
            String name,
            String city,
            Integer capacity,
            /** "Çim" / "Suni Çim" — dile göre çevrilmiş; null olabilir. */
            String surface
    ) implements Serializable {
    }

    /** Detay sayfası için lig referansı — özet kartından daha zengin (ülke + bayrak). */
    public record LeagueRef(
            Long id,
            String name,
            /** "Lig" / "Kupa" — dile göre çevrilmiş. */
            String type,
            String logo,
            /** Dile göre çevrilmiş ülke adı. */
            String country,
            String countryFlag,
            /** Sezon yılı (örn. 2025). */
            Integer season
    ) implements Serializable {
    }
}
