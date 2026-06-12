package com.scorestv.basketball.domain;

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
 * Junction: bir basketbol takımının hangi lig+sezonda yer aldığı.
 * Futbol {@link com.scorestv.football.domain.TeamLeagueSeason}'in basketbol
 * karşılığı.
 *
 * <p>Kaynak: API-Basketball {@code /teams?league=X&season=Y} çağrısı sonucunda
 * upsert edilir (bkz. {@code BasketballTeamSyncService.syncTeamsForLeague}).
 * Bu sayede "lig X sezon Y'deki tüm takımlar" sorgusu, games'e bağımlı olmadan
 * KESIN ve TAM cevap verir — sezon başında hiç maç yokken bile resmi kadro
 * listesi alınabilir.
 *
 * <p>Bileşik primary key: (team_id, league_id, season). JPA tarafında
 * {@link Pk} embedded id ile temsil edilir. {@code season} VARCHAR çünkü
 * basketbol "2024-2025" formatında iki yıl span (futbol tek integer year).
 */
@Entity
@Table(name = "basketball_team_league_seasons")
@Getter
@Setter
@NoArgsConstructor
public class BasketballTeamLeagueSeason {

    @EmbeddedId
    private Pk id;

    @Column(name = "synced_at", nullable = false)
    private Instant syncedAt;

    public BasketballTeamLeagueSeason(Long teamId, Long leagueId, String season) {
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
