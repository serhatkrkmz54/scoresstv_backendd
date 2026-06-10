package com.scorestv.football.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Junction: bir takimin hangi lig+sezonda yer aldigi.
 *
 * <p>Kaynak: API-Football {@code /teams?league=X&season=Y} cagrisi sonucunda
 * upsert edilir (bkz. {@code TeamUpserter.upsertForLeagueSeason}). Bu sayede
 * "lig X sezon Y'deki tum takimlar" sorgusu, fixtures'a bagimli olmadan
 * KESIN ve TAM cevap verir — sezon basinda hic fikstur yokken bile resmi
 * kadro listesi alinabilir.
 *
 * <p>Bilesik primary key: (team_id, league_id, season). JPA tarafinda
 * {@link Pk} embedded id ile temsil edilir.
 */
@Entity
@Table(name = "team_league_seasons")
@Getter
@Setter
@NoArgsConstructor
public class TeamLeagueSeason {

    @EmbeddedId
    private Pk id;

    /**
     * Bu kaydin /teams API'sinden yazildigi/refresh edildigi an. Periyodik
     * yeniden senkron joblar buna bakip debounce yapabilir.
     */
    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    public TeamLeagueSeason(Long teamId, Long leagueId, Integer season) {
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

        @Column(nullable = false)
        private Integer season;

        public Pk(Long teamId, Long leagueId, Integer season) {
            this.teamId = teamId;
            this.leagueId = leagueId;
            this.season = season;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk other)) return false;
            return Objects.equals(teamId, other.teamId)
                    && Objects.equals(leagueId, other.leagueId)
                    && Objects.equals(season, other.season);
        }

        @Override
        public int hashCode() {
            return Objects.hash(teamId, leagueId, season);
        }
    }
}
