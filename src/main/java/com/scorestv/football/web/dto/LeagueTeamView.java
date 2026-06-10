package com.scorestv.football.web.dto;

import java.io.Serializable;

/**
 * Hafif takim ozeti — onboarding "favori takim secme" gibi yerlerde kullanilir.
 *
 * <p>Tam {@code TeamDetailResponse} gibi agir veri tasimaz — sadece liste
 * uretmek icin gerekli alanlar. Mobile uygulama bunu monogram arma + isim
 * cizmek icin kullanir.
 */
public record LeagueTeamView(
        Long id,
        String name,
        /** Dile gore Turkce ad (TR locale icin name_tr). Yoksa name ile ayni. */
        String nameTr,
        /** 2-3 harf kisaltma (monogram icin). Backend'de yoksa name'den uretir. */
        String shortCode,
        String slug,
        String logoUrl,
        /** Hex renk (#RRGGBB) — monogram arka plani icin. Yoksa null. */
        String primaryColor,
        String country,
        String countryCode
) implements Serializable {}
