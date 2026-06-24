package com.scorestv.volleyball.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Junction: bir voleybol takiminin hangi lig+sezonda yer aldigi.
 *
 * <p>Kaynak: API-Volleyball {@code /teams?league=X&season=Y} cagrisi sonucunda
 * upsert edilir. Boylece "lig X sezon Y'deki tum takimlar" sorgusu games'e
 * bagimli olmadan KESIN cevap verir.
 *
 * <p>Bilesik primary key: (team_id, league_id, season).
 */
@Entity
@Table(name = "volleyball_team_league_seasons")
@Getter
@Setter
@NoArgsConstructor
public class VolleyballTeamLeagueSeason {

    @EmbeddedId
    private Pk id;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    public VolleyballTeamLeagueSeason(Long teamId, Long leagueId, String season) {
        this.id = new Pk(teamId, leagueId, season);
        this.syncedAt = Instant.now();
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Pk implements Serializable {

        @Column(name = "team_id", nullable = false)
        private Long teamId;

        @Column(name = "league_id", nullable = false)
        private Long leagueId;

        @Column(name = "season", nullable = false, length = 20)
        private String season;

        public Pk(Long teamId, Long leagueId, String season) {
            this.teamId = teamId;
            this.leagueId = leagueId;
            this.season = season;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk pk)) return false;
            return Objects.equals(teamId, pk.teamId)
                    && Objects.equals(leagueId, pk.leagueId)
                    && Objects.equals(season, pk.season);
        }

        @Override
        public int hashCode() {
            return Objects.hash(teamId, leagueId, season);
        }
    }
}
