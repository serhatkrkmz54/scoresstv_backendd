package com.scorestv.football.sync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * API-Football {@code /transfers?team=X} yanitinda bir element.
 *
 * <p>Yapi: her oyuncunun TUM kariyer transferleri ic ice gelir.
 * {@code response[i] = { player: {...}, update: "...", transfers: [{date, type, teams: {in, out}}] }}.
 *
 * <p>{@code TransferUpserter} bu zinciri acip flat olarak {@code transfers}
 * tablosuna yazar. Takim sorgusu, hem in_team_id=X hem out_team_id=X
 * indeksli olarak hizli calisir.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TransferApiDto(
        Player player,
        String update,
        List<TransferEntry> transfers
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Player(Long id, String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TransferEntry(
            String date,    // "2019-07-15"
            String type,    // "Free" / "Loan" / "$X.XM" gibi
            TeamsPair teams
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TeamsPair(TeamRef in, TeamRef out) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TeamRef(Long id, String name, String logo) {}
}
