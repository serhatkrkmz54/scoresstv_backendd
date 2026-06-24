package com.scorestv.volleyball.web.dto;

import java.io.Serializable;

/**
 * Hafif voleybol takim ozeti — onboarding "favori takim secme" gibi yerlerde.
 * Basketbol {@code BasketballLeagueTeamView}'in voleybol karsiligi.
 */
public record VolleyballLeagueTeamView(
        Long id,
        String name,
        /** Dile gore Turkce ad (TR locale icin name_tr). Yoksa name ile ayni. */
        String nameTr,
        /** 2-3 harf kisaltma — backend'de yoksa name'den uretilir. */
        String shortCode,
        String logoUrl
) implements Serializable {}
