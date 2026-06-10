package com.scorestv.football.sync.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API-Football {@code /players?team=X&season=Y} yaniti elementi.
 *
 * <p>Her response item iki ust kismi icerir:
 * <ul>
 *   <li>{@code player} — oyuncu kimligi (id, name, age, photo, ...) — Player
 *       master tabloya da yazilir (PlayerUpserter).</li>
 *   <li>{@code statistics[]} — ayni sezonda oynadigi her turnuva icin ayri
 *       obje (team, league, games, goals, shots, passes, tackles, duels,
 *       dribbles, fouls, cards, penalty, substitutes). Bu, takim+lig+sezon
 *       bazinda PlayerSeasonStat satirina yazilir.</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlayerSeasonApiDto(
        Player player,
        List<StatisticsEntry> statistics
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Player(
            Long id,
            String name,
            String firstname,
            String lastname,
            Integer age,
            Birth birth,
            String nationality,
            String height,
            String weight,
            Boolean injured,
            String photo
    ) {}

    /** Oyuncu dogum bilgisi — players?id endpoint'i yaniti icinde. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Birth(
            String date,
            String place,
            String country
    ) {}

    /**
     * Tek bir turnuvaya ait sezonluk istatistik. Tam icerik (games/goals/
     * passes/...) JSONB passthrough icin {@link #fields()} ile saklanir.
     * {@code team}, {@code league} struct-level cikariliyor (FK kurmak ve
     * sezon yilini cozmek icin).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StatisticsEntry {
        private TeamRef team;
        private LeagueRef league;
        private final Map<String, Object> fields = new HashMap<>();

        public TeamRef team() { return team; }
        public LeagueRef league() { return league; }
        /** Ham fields — JSONB'ye yazilacak passthrough. */
        public Map<String, Object> fields() { return fields; }

        // team + league ayri set edilir; geri kalanlari any-setter yutar.
        public void setTeam(TeamRef team) { this.team = team; }
        public void setLeague(LeagueRef league) { this.league = league; }

        @JsonAnySetter
        public void putField(String key, Object value) {
            fields.put(key, value);
        }

        @JsonAnyGetter
        public Map<String, Object> getFields() {
            // Cikti icin team + league'i de geri ekle (JSONB tam icerik olsun).
            Map<String, Object> all = new HashMap<>(fields);
            if (team != null) all.put("team", team);
            if (league != null) all.put("league", league);
            return all;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TeamRef(Long id, String name, String logo) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LeagueRef(
            Long id,
            String name,
            String country,
            String logo,
            String flag,
            Integer season
    ) {}
}
