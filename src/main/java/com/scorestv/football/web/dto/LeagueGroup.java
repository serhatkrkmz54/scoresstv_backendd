package com.scorestv.football.web.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Bir günün maçlarının tek bir lig altında gruplanmış hali.
 * Anasayfa maçları lig lig gösterdiği için yanıt bu şekilde gruplanır.
 */
public record LeagueGroup(
        LeagueInfo league,
        List<FixtureSummary> fixtures
) implements Serializable {

    /** Lig özeti. */
    public record LeagueInfo(
            Long id,
            String name,
            /** Türü ("Lig" / "Kupa"); dile göre çevrilmiş. */
            String type,
            String logo,
            String country,
            String countryFlag,
            /** Bu lig öne çıkarılan (senkron kapsamındaki) liglerden mi? */
            boolean covered,
            /** Lig sayfası URL slug'ı; dile göre ("super-lig-203" / "super-league-203"). */
            String slug
    ) implements Serializable {
    }
}
