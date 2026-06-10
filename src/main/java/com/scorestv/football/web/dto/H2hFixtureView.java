package com.scorestv.football.web.dto;

import java.io.Serializable;
import java.time.Instant;

/**
 * Maç detayı "Head-to-Head" bölümünde tek bir karşılaşma kartı. Tüm statüleri
 * kapsar (geçmiş, canlı, gelecek); yeni → eski sıralı.
 *
 * @param status     API durum kodu ("FT", "NS", "1H", ...)
 * @param statusText Dile çevrilmiş durum ("Maç Bitti" / "Match Finished" vb.)
 */
public record H2hFixtureView(
        Long id,
        String slug,
        Instant kickoff,
        LeagueRef league,
        Team homeTeam,
        Team awayTeam,
        Integer homeScore,
        Integer awayScore,
        String status,
        String statusText
) implements Serializable {

    public record LeagueRef(Long id, String name, String logo) implements Serializable {}

    /**
     * @param slug Takım detay slug'i; frontend {@code /team/} veya {@code /takim/} ekler.
     */
    public record Team(Long id, String name, String logo, String slug) implements Serializable {}
}
