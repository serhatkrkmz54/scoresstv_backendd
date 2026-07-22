package com.scorestv.football.sync;

import com.scorestv.football.domain.Team;
import com.scorestv.football.domain.TeamLeagueSeason;
import com.scorestv.football.domain.TeamLeagueSeasonRepository;
import com.scorestv.football.domain.TeamRepository;
import com.scorestv.football.domain.Venue;
import com.scorestv.football.domain.VenueRepository;
import com.scorestv.football.sync.dto.TeamApiDto;
import com.scorestv.search.events.EntityIndexedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * {@code /teams} verisini veritabanına upsert eden transactional bileşen.
 *
 * <p>Mevcut takımları zenginleştirir (code, country, founded, national, stadyum
 * bağı) ve stadyumları tam detayıyla doldurur (adres, kapasite, zemin, görsel).
 * {@code logo_key} alanına <b>dokunmaz</b> — orası görsel aynalamanın alanıdır.
 */
@Service
public class TeamUpserter {

    private final TeamRepository teamRepository;
    private final VenueRepository venueRepository;
    private final TeamLeagueSeasonRepository membershipRepository;
    private final ApplicationEventPublisher events;

    public TeamUpserter(TeamRepository teamRepository,
                        VenueRepository venueRepository,
                        TeamLeagueSeasonRepository membershipRepository,
                        ApplicationEventPublisher events) {
        this.teamRepository = teamRepository;
        this.venueRepository = venueRepository;
        this.membershipRepository = membershipRepository;
        this.events = events;
    }

    /**
     * Bir ligin takımlarını (ve stadyumlarını) tek transaction'da upsert eder.
     * Zorunlu alanı eksik öğeler atlanır.
     *
     * @return upsert edilen takım sayısı
     */
    @Transactional
    public int upsert(List<TeamApiDto> items) {
        return upsertForLeagueSeason(items, null, null);
    }

    /**
     * {@link #upsert(List)}'in lig+sezon context'i barindiran varyanti.
     * Lig+sezon verildiginde her takim icin junction tablosuna da (upsert)
     * kayit duser; boylece "lig X sezon Y'deki takimlar" sorgusu fixtures'a
     * bagimli olmadan KESIN cevap verir.
     *
     * <p>Lig veya sezon {@code null} ise davranis eski {@link #upsert(List)}
     * ile aynidir — sadece teams + venues yazilir, junction'a dokunulmaz.
     *
     * @return upsert edilen takim sayisi
     */
    @Transactional
    public int upsertForLeagueSeason(List<TeamApiDto> items, Long leagueId, Integer season) {
        int upserted = 0;
        Instant now = Instant.now();
        for (TeamApiDto item : items) {
            TeamApiDto.Team incoming = item.team();
            if (incoming == null || incoming.id() == null || incoming.name() == null) {
                continue;
            }
            Venue venue = upsertVenue(item.venue());

            Team team = teamRepository.findById(incoming.id()).orElseGet(Team::new);
            team.setId(incoming.id());
            team.setName(incoming.name());
            team.setCode(incoming.code());
            team.setCountry(incoming.country());
            team.setFounded(incoming.founded());
            team.setNational(Boolean.TRUE.equals(incoming.national()));
            team.setLogoUrl(incoming.logo());
            team.setVenue(venue);
            // logoKey ve nameTr'ye DOKUNULMAZ: ilki görsel aynalamanın, ikincisi
            // (elle girilen Türkçe ad) çeviri akışının alanıdır. Mevcut takım
            // DB'den yüklendiği için bu alanlar değişmeden korunur.
            teamRepository.save(team);
            // ES'e arama indeksini guncellemek icin event yayinla — AFTER_COMMIT
            // + @Async ile SearchIndexEventListener tetiklenecek.
            events.publishEvent(new EntityIndexedEvent.TeamIndexed(team));

            // Lig+sezon context'i varsa junction'a uyelik yaz.
            if (leagueId != null && season != null) {
                TeamLeagueSeason.Pk pk = new TeamLeagueSeason.Pk(
                        incoming.id(), leagueId, season);
                TeamLeagueSeason link = membershipRepository.findById(pk)
                        .orElseGet(() -> {
                            TeamLeagueSeason fresh = new TeamLeagueSeason();
                            fresh.setId(pk);
                            return fresh;
                        });
                link.setSyncedAt(now);
                membershipRepository.save(link);
            }
            upserted++;
        }
        return upserted;
    }

    /** Stadyumu tam detayıyla upsert eder; id veya ad eksikse null döner. */
    private Venue upsertVenue(TeamApiDto.Venue incoming) {
        // API-Football alt lig stadyumlarına venue.id=0 (bazen ≤0) verir — GEÇERLİ
        // bir kimlik DEĞİL. id=0'ı saklarsak dünyadaki TÜM "id'siz" stadyumlar tek
        // Venue(0) satırına çöker ve birbirini ezer (FixtureUpserter'daki aynı hata).
        // Burada daha da kötü: tam detay (adres/kapasite/zemin/görsel) tek satıra
        // yazılıp o satırı kirletir. Bu durumda takıma stadyum BAĞLAMAYIZ.
        if (incoming == null || incoming.id() == null || incoming.id() <= 0
                || incoming.name() == null) {
            return null;
        }
        Venue venue = venueRepository.findById(incoming.id()).orElseGet(Venue::new);
        venue.setId(incoming.id());
        venue.setName(incoming.name());
        venue.setAddress(incoming.address());
        venue.setCity(incoming.city());
        venue.setCapacity(incoming.capacity());
        venue.setSurface(incoming.surface());
        venue.setImageUrl(incoming.image());
        // nameTr'ye dokunulmaz — elle girilen Türkçe ad korunur.
        return venueRepository.save(venue);
    }
}
