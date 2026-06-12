package com.scorestv.basketball.web.dto;

import java.io.Serializable;

/**
 * Hafif basketbol takım özeti — onboarding "favori takım seçme" gibi yerlerde
 * kullanılır. Futbol {@code LeagueTeamView}'in basketbol karşılığı.
 *
 * <p>Mobile bunu monogram arma + isim çizmek için kullanır.
 */
public record BasketballLeagueTeamView(
        Long id,
        String name,
        /** Dile göre Türkçe ad (TR locale için name_tr). Yoksa name ile aynı. */
        String nameTr,
        /** 2-3 harf kısaltma — backend'de yoksa name'den üretilir. */
        String shortCode,
        String logoUrl
) implements Serializable {}
