package com.scorestv.football.live;

import com.scorestv.common.SlugUtil;
import com.scorestv.football.FootballMessages;
import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.League;
import com.scorestv.football.domain.Team;
import com.scorestv.football.domain.TranslatableName;
import com.scorestv.football.web.dto.FixtureSummary;
import com.scorestv.football.web.dto.LiveFixturesResponse;
import com.scorestv.storage.MinioStorageService;
import org.springframework.stereotype.Component;

/**
 * Bir {@link Fixture} entity'sini canlı yayın için
 * {@link LiveFixturesResponse.LiveFixture} DTO'suna çevirir.
 *
 * <p>İki kanal aynı mapper'ı kullanır:
 * <ul>
 *   <li>HTTP polling — {@code GET /api/v1/fixtures/live}</li>
 *   <li>WebSocket yayın — {@link LiveBroadcaster}</li>
 * </ul>
 * Böylece iki yol arasında alan/biçim farkı olmaz.
 *
 * <p>Görseller daima MinIO key üzerinden kendi CDN'imizden verilir;
 * key boşsa {@code null} döner — frontend yer tutucusunu gösterir.
 */
@Component
public class LiveFixtureMapper {

    private final MinioStorageService storage;
    private final FootballMessages messages;

    public LiveFixtureMapper(MinioStorageService storage, FootballMessages messages) {
        this.storage = storage;
        this.messages = messages;
    }

    /**
     * Canlı maç özetini üretir. Fixture'ın {@code league}, {@code homeTeam} ve
     * {@code awayTeam} ilişkileri çağrı anında erişilebilir olmalıdır
     * (JOIN FETCH ile çekilmiş entity verin).
     */
    public LiveFixturesResponse.LiveFixture toLiveFixture(Fixture fixture, boolean turkish) {
        League league = fixture.getLeague();
        Team home = fixture.getHomeTeam();
        Team away = fixture.getAwayTeam();
        return new LiveFixturesResponse.LiveFixture(
                fixture.getId(),
                // Slug dile göre lokalize (TR'de name_tr, yoksa orijinal). id ile çözülür.
                SlugUtil.fixtureSlug(displayName(home, turkish), displayName(away, turkish), fixture.getId()),
                new LiveFixturesResponse.LeagueRef(
                        league.getId(),
                        displayName(league, turkish),
                        messages.leagueType(league.getType(), turkish),
                        logoUrl(league.getLogoKey())),
                messages.roundText(fixture.getRound(), turkish),
                fixture.getKickoffAt(),
                fixture.getLastSyncedAt(),
                FixtureSummary.Status.of(
                        fixture.getStatusShort(),
                        messages.statusText(
                                fixture.getStatusShort(), fixture.getStatusLong(), turkish),
                        fixture.getElapsed(), fixture.getStatusExtra()),
                new FixtureSummary.Team(
                        home.getId(), displayName(home, turkish), logoUrl(home.getLogoKey()),
                        SlugUtil.teamSlug(displayName(home, turkish), home.getId())),
                new FixtureSummary.Team(
                        away.getId(), displayName(away, turkish), logoUrl(away.getLogoKey()),
                        SlugUtil.teamSlug(displayName(away, turkish), away.getId())),
                new FixtureSummary.Score(
                        fixture.getHomeGoals(), fixture.getAwayGoals(),
                        period(fixture.getScoreHtHome(), fixture.getScoreHtAway()),
                        period(fixture.getScoreEtHome(), fixture.getScoreEtAway()),
                        period(fixture.getScorePenHome(), fixture.getScorePenAway())));
    }

    /** MinIO key varsa CDN URL'i; aksi halde null (hotlink yok). */
    private String logoUrl(String key) {
        return key != null ? storage.publicUrl(key) : null;
    }

    /** Dil "tr" ise ve Türkçe karşılığı girilmişse Türkçe ad; aksi halde İngilizce. */
    /** İY/UZ/PEN periyot skoru; iki değer de null ise null döner. */
    private static FixtureSummary.Score.Period period(Integer home, Integer away) {
        return (home == null && away == null)
                ? null
                : new FixtureSummary.Score.Period(home, away);
    }

    private static String displayName(TranslatableName entity, boolean turkish) {
        if (turkish) {
            String tr = entity.getNameTr();
            if (tr != null && !tr.isBlank()) {
                return tr;
            }
        }
        return entity.getName();
    }
}
