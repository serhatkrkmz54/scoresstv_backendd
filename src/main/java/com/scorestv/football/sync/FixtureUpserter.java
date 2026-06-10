package com.scorestv.football.sync;

import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.football.domain.League;
import com.scorestv.football.domain.LeagueRepository;
import com.scorestv.football.domain.Team;
import com.scorestv.football.domain.TeamRepository;
import com.scorestv.football.domain.Venue;
import com.scorestv.football.domain.VenueRepository;
import com.scorestv.football.sync.dto.FixtureApiDto;
import com.scorestv.search.events.EntityIndexedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code /fixtures} verisini veritabanına upsert eden transactional bileşen.
 *
 * <p>HTTP çağrısından kasıtlı olarak ayrı tutulur: yavaş ağ işlemi DB
 * transaction'ını açık tutmamalı. {@link FixtureSyncService} önce veriyi çeker,
 * sonra bu sınıfı çağırır.
 *
 * <p>Aynalanan varlıklar API-Football ID'sini PK kullandığı için upsert
 * "bul ya da oluştur → alanları doldur → kaydet" desenidir.
 */
@Service
public class FixtureUpserter {

    private static final Logger log = LoggerFactory.getLogger(FixtureUpserter.class);

    private final FixtureRepository fixtureRepository;
    private final TeamRepository teamRepository;
    private final LeagueRepository leagueRepository;
    private final VenueRepository venueRepository;
    private final ApplicationEventPublisher events;

    public FixtureUpserter(FixtureRepository fixtureRepository,
                           TeamRepository teamRepository,
                           LeagueRepository leagueRepository,
                           VenueRepository venueRepository,
                           ApplicationEventPublisher events) {
        this.fixtureRepository = fixtureRepository;
        this.teamRepository = teamRepository;
        this.leagueRepository = leagueRepository;
        this.venueRepository = venueRepository;
        this.events = events;
    }

    /**
     * Bir partideki maçları (ve gömülü lig/takım/stadyum verisini) tek
     * transaction'da upsert eder. Zorunlu alanı eksik öğeler atlanır.
     *
     * @return başarıyla upsert edilen maç sayısı
     */
    @Transactional
    public int upsert(List<FixtureApiDto> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }
        // Aynı parti içinde tekrar eden lig/takım/stadyumu defalarca DB'den
        // çekmemek için parti-içi önbellek.
        Map<Long, League> leagues = new HashMap<>();
        Map<Long, Team> teams = new HashMap<>();
        Map<Long, Venue> venues = new HashMap<>();

        int upserted = 0;
        int skipped = 0;
        for (FixtureApiDto item : items) {
            if (isInvalid(item)) {
                skipped++;
                continue;
            }
            upsertOne(item, leagues, teams, venues);
            upserted++;
        }
        if (skipped > 0) {
            log.debug("Upsert: {} öğe zorunlu alan eksikliğinden atlandı", skipped);
        }
        return upserted;
    }

    /**
     * Zorunlu (NOT NULL) alanları eksik öğeleri saptar. Bu kontrolden geçen
     * öğelerin upsert'i veritabanı kısıtlarına takılmamalıdır.
     */
    private boolean isInvalid(FixtureApiDto item) {
        if (item == null || item.fixture() == null || item.league() == null
                || item.teams() == null) {
            return true;
        }
        FixtureApiDto.Fixture f = item.fixture();
        if (f.id() == null || f.date() == null || f.status() == null
                || f.status().shortCode() == null) {
            return true;
        }
        FixtureApiDto.League l = item.league();
        if (l.id() == null || l.name() == null || l.season() == null) {
            return true;
        }
        return isInvalidTeam(item.teams().home()) || isInvalidTeam(item.teams().away());
    }

    private boolean isInvalidTeam(FixtureApiDto.Team t) {
        return t == null || t.id() == null || t.name() == null;
    }

    private void upsertOne(FixtureApiDto item,
                           Map<Long, League> leagues,
                           Map<Long, Team> teams,
                           Map<Long, Venue> venues) {
        Venue venue = upsertVenue(item.fixture().venue(), venues);
        League league = upsertLeague(item.league(), leagues);
        Team home = upsertTeam(item.teams().home(), teams);
        Team away = upsertTeam(item.teams().away(), teams);

        FixtureApiDto.Fixture f = item.fixture();
        Fixture fixture = fixtureRepository.findById(f.id()).orElseGet(Fixture::new);
        fixture.setId(f.id());
        fixture.setLeague(league);
        fixture.setSeason(item.league().season());
        fixture.setRound(item.league().round());
        fixture.setHomeTeam(home);
        fixture.setAwayTeam(away);
        fixture.setVenue(venue);
        // Inline fallback: API venue.id null gonderse bile ad+sehir varsa
        // dogrudan fikstur'de sakla. UI'da Venue entity yoksa bunu kullanir.
        FixtureApiDto.Venue rawVenue = item.fixture().venue();
        fixture.setVenueName(rawVenue != null ? rawVenue.name() : null);
        fixture.setVenueCity(rawVenue != null ? rawVenue.city() : null);
        fixture.setReferee(f.referee());
        fixture.setKickoffAt(OffsetDateTime.parse(f.date()).toInstant());
        fixture.setStatusShort(f.status().shortCode());
        fixture.setStatusLong(f.status().longText());
        fixture.setElapsed(f.status().elapsed());
        fixture.setStatusExtra(f.status().extra());

        FixtureApiDto.Goals g = item.goals();
        fixture.setHomeGoals(g == null ? null : g.home());
        fixture.setAwayGoals(g == null ? null : g.away());

        FixtureApiDto.Score s = item.score();
        FixtureApiDto.GoalPair ht = s == null ? null : s.halftime();
        FixtureApiDto.GoalPair ft = s == null ? null : s.fulltime();
        FixtureApiDto.GoalPair et = s == null ? null : s.extratime();
        FixtureApiDto.GoalPair pen = s == null ? null : s.penalty();
        fixture.setScoreHtHome(homeOf(ht));
        fixture.setScoreHtAway(awayOf(ht));
        fixture.setScoreFtHome(homeOf(ft));
        fixture.setScoreFtAway(awayOf(ft));
        fixture.setScoreEtHome(homeOf(et));
        fixture.setScoreEtAway(awayOf(et));
        fixture.setScorePenHome(homeOf(pen));
        fixture.setScorePenAway(awayOf(pen));

        fixture.setLastSyncedAt(Instant.now());
        fixtureRepository.save(fixture);
        // ES arama indeksini guncellemek icin event yayinla.
        events.publishEvent(new EntityIndexedEvent.FixtureIndexed(fixture));
    }

    /** Stadyumu upsert eder. id veya ad eksikse null döner (venue opsiyoneldir). */
    private Venue upsertVenue(FixtureApiDto.Venue v, Map<Long, Venue> cache) {
        if (v == null || v.id() == null || v.name() == null) {
            return null;
        }
        return cache.computeIfAbsent(v.id(), id -> {
            Venue e = venueRepository.findById(id).orElseGet(Venue::new);
            e.setId(id);
            e.setName(v.name());
            e.setCity(v.city());
            return venueRepository.save(e);
        });
    }

    /**
     * Ligi upsert eder. {@code type} ve {@code covered} bu çağrıda DOKUNULMAZ:
     * type fikstür yanıtında gelmez (özel /leagues senkronundan gelir),
     * covered ise ADMIN'in belirlediği görünürlük bayrağıdır.
     *
     * <p><b>currentSeason kuralı:</b> Sadece YENİ lig için veya henüz set
     * edilmemişse atanır. Mevcut ligin {@code currentSeason}'ı override
     * EDİLMEZ — aksi halde H2H sync'i geçmiş sezon (örn. 2019) maçı
     * çektiğinde Premier League'in current_season'ı 2025 → 2019 olarak
     * yanlışlıkla geri sarardı, puan durumu/standings ekranını bozardı.
     * Doğru current_season {@code LeaguesSyncService} (özel /leagues
     * senkronu) tarafından yönetilir.
     */
    private League upsertLeague(FixtureApiDto.League l, Map<Long, League> cache) {
        return cache.computeIfAbsent(l.id(), id -> {
            Optional<League> existing = leagueRepository.findById(id);
            League e = existing.orElseGet(League::new);
            boolean isNew = existing.isEmpty();
            e.setId(id);
            e.setName(l.name());
            e.setLogoUrl(l.logo());
            e.setCountryName(l.country());
            e.setCountryFlagUrl(l.flag());
            if (isNew || e.getCurrentSeason() == null) {
                e.setCurrentSeason(l.season());
            }
            League saved = leagueRepository.save(e);
            events.publishEvent(new EntityIndexedEvent.LeagueIndexed(saved));
            return saved;
        });
    }

    private Team upsertTeam(FixtureApiDto.Team t, Map<Long, Team> cache) {
        return cache.computeIfAbsent(t.id(), id -> {
            Team e = teamRepository.findById(id).orElseGet(Team::new);
            e.setId(id);
            e.setName(t.name());
            e.setLogoUrl(t.logo());
            Team saved = teamRepository.save(e);
            events.publishEvent(new EntityIndexedEvent.TeamIndexed(saved));
            return saved;
        });
    }

    private static Integer homeOf(FixtureApiDto.GoalPair p) {
        return p == null ? null : p.home();
    }

    private static Integer awayOf(FixtureApiDto.GoalPair p) {
        return p == null ? null : p.away();
    }
}
