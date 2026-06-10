package com.scorestv.football.web.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * Şu an oynanan tüm canlı maçların kompakt listesi — canlı banner / mobil
 * widget için. Lige göre gruplanmaz; düz liste, başlama saatine göre sıralı.
 *
 * <p>Kısa polling için uygundur (örn. her 30 sn frontend bunu çağırır).
 * Yanıt redis'te kısa süreli cache'lenir.
 *
 * @param asOf       yanıtın üretildiği an — saat senkronu için
 * @param liveCount  şu an canlı maç sayısı
 * @param fixtures   canlı maçların kompakt özetleri
 */
public record LiveFixturesResponse(
        Instant asOf,
        int liveCount,
        List<LiveFixture> fixtures
) implements Serializable {

    /**
     * Canlı bir maçın kompakt özeti. Stadyum ve bağlam alanları (venue, country)
     * dahil edilmez; canlı banner için gereksizdir.
     */
    public record LiveFixture(
            Long id,
            String slug,
            LeagueRef league,
            String round,
            Instant kickoff,
            Instant lastSyncedAt,
            FixtureSummary.Status status,
            FixtureSummary.Team homeTeam,
            FixtureSummary.Team awayTeam,
            FixtureSummary.Score score
    ) implements Serializable {
    }

    /** Maça gömülü lig referansı — banner üzerinde lig adı/logosu için. */
    public record LeagueRef(
            Long id,
            String name,
            String type,
            String logo
    ) implements Serializable {
    }
}
