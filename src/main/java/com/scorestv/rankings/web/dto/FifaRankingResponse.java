package com.scorestv.rankings.web.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * FIFA Erkek Milli Takim Siralamasi yaniti.
 *
 * <p>{@code totalTeams} ana liste boyutu, {@code lastUpdated} en son ne zaman
 * tazelendi (en yeni satirin {@code lastSyncedAt}'i).
 *
 * <p>Filtrelenebilir: {@code ?confederation=UEFA} veya {@code ?search=france}.
 */
public record FifaRankingResponse(
        Integer totalTeams,
        Instant lastUpdated,
        List<Row> teams
) implements Serializable {

    public record Row(
            Integer rank,
            Integer prevRank,
            /** Pozitif: yukseldi, negatif: dustu, 0: stabil. */
            Integer movement,
            String teamId,
            String teamName,
            /** Ulke 3-harf ISO. */
            String countryCode,
            /** UEFA / CONMEBOL / CAF / AFC / CONCACAF / OFC. */
            String confederation,
            String confederationId,
            /** Kesirli puan. */
            BigDecimal totalPoints,
            BigDecimal prevPoints,
            Integer ratedMatches,
            /** Country tablosundan teamName ile join sonucu — null olabilir. */
            String flagUrl,
            /** DB Country eslesmesi varsa /ulke linki icin slug; yoksa null
             *  (frontend bu durumda link vermez). */
            String countrySlug
    ) implements Serializable {}
}
