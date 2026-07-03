package com.scorestv.football.web;

import com.scorestv.broadcasts.BroadcastsService;
import com.scorestv.common.SlugUtil;
import com.scorestv.football.FootballCacheNames;
import com.scorestv.football.FootballMessages;
import com.scorestv.football.FootballProperties;
import com.scorestv.football.domain.Country;
import com.scorestv.football.domain.CountryRepository;
import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.football.domain.League;
import com.scorestv.football.domain.Team;
import com.scorestv.football.domain.TranslatableName;
import com.scorestv.football.web.dto.TvGuideResponse;
import com.scorestv.storage.MinioStorageService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Canlı Maç Programı / Hangi Kanalda" hub'ı için bir günün futbol maçlarını
 * lige gruplu + her lig için varsayılan TV kanal(lar)ı ile döndürür.
 *
 * <p>{@link FixtureQueryService#getFixturesByDate} deseniyle aynı zone/aralık
 * ve {@code findDayWithLiveSpillover} sorgusunu kullanır; ancak hub'da SARKMA
 * İSTEMEDİĞİMİZ için {@code priorStart = start} geçilir (yalnız o günün maçları).
 *
 * <p><b>Görseller:</b> logo URL'leri DAİMA kendi CDN'imizden (MinIO) verilir;
 * aynalanmadıysa {@code null} döner (hotlink yok) — FixtureQueryService ile aynı.
 *
 * <p><b>Kanal:</b> her lig için varsayılan kanallar {@link BroadcastsService#leagueDefaultChannels}
 * ile GRUP BAŞINA BİR KEZ çözülür (o grubun ilk maçının sezonuyla).
 */
@Service
public class TvGuideService {

    /** Maçın o anda oynanıyor olduğunu belirten API-Football durum kodları
     *  (FixtureQueryService ile aynı küme). Spillover sorgusunun @Param'ı olarak
     *  gerekir; hub'da spillover kapalı olduğu için pratikte eşleşme olmaz. */
    private static final Set<String> LIVE_STATUSES =
            Set.of("1H", "HT", "2H", "ET", "BT", "P", "LIVE");

    private final FixtureRepository fixtureRepository;
    private final CountryRepository countryRepository;
    private final FootballProperties footballProperties;
    private final MinioStorageService storage;
    private final FootballMessages messages;
    private final BroadcastsService broadcastsService;

    public TvGuideService(FixtureRepository fixtureRepository,
                          CountryRepository countryRepository,
                          FootballProperties footballProperties,
                          MinioStorageService storage,
                          FootballMessages messages,
                          BroadcastsService broadcastsService) {
        this.fixtureRepository = fixtureRepository;
        this.countryRepository = countryRepository;
        this.footballProperties = footballProperties;
        this.storage = storage;
        this.messages = messages;
        this.broadcastsService = broadcastsService;
    }

    /** Site saat dilimine göre bugünün tarihi (controller varsayılanı için). */
    public LocalDate today() {
        return LocalDate.now(zone());
    }

    /**
     * Verilen günün futbol TV programı — lige gruplu + lig kanalları.
     *
     * @param date    gün (ISO)
     * @param country kullanıcı ülke kodu (kanal çözümü için; null → "TR")
     * @param turkish true ise takım/lig/ülke/kanal adları Türkçe (girilmişse)
     */
    @Cacheable(value = FootballCacheNames.LIVE,
            key = "'tvguide-' + #date.toString() + '-' + (#country == null ? 'TR' : #country) + '-' + (#turkish ? 'tr' : 'en')")
    @Transactional(readOnly = true)
    public TvGuideResponse getTvGuide(LocalDate date, String country, boolean turkish) {
        ZoneId zone = zone();
        Instant start = date.atStartOfDay(zone).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(zone).toInstant();

        // Hub'da SPILLOVER YOK — yalnız o günün maçları. priorStart = start
        // vererek sarkma penceresini boşaltıyoruz (FixtureQueryService'in
        // gelecek/geçmiş günler için yaptığının aynısı).
        List<Fixture> fixtures = fixtureRepository.findDayWithLiveSpillover(
                start, start, end, LIVE_STATUSES);

        // Lig ülke bayrağı/adı çözümü için ad -> ülke haritası.
        Map<String, Country> countriesByName = new HashMap<>();
        for (Country c : countryRepository.findAll()) {
            countriesByName.put(c.getName(), c);
        }

        // Lige göre grupla (ekleniş sırası korunur; repo zaten kickoff ASC döner).
        Map<Long, List<Fixture>> byLeague = new LinkedHashMap<>();
        for (Fixture fixture : fixtures) {
            byLeague.computeIfAbsent(fixture.getLeague().getId(), k -> new ArrayList<>())
                    .add(fixture);
        }

        // Lig içi kickoff ASC (tiebreaker id) — kararlı kronolojik sıra.
        Comparator<Fixture> byKickoff = Comparator
                .comparing(Fixture::getKickoffAt,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Fixture::getId);

        List<TvGuideResponse.League> leagues = new ArrayList<>();
        for (List<Fixture> leagueFixtures : byLeague.values()) {
            leagueFixtures.sort(byKickoff);
            League league = leagueFixtures.get(0).getLeague();

            // Kanalları GRUP BAŞINA BİR KEZ çöz: ilk maçın sezonu (null ise
            // ligin currentSeason'ı).
            Integer season = leagueFixtures.get(0).getSeason();
            if (season == null) {
                season = league.getCurrentSeason();
            }
            List<String> channels = broadcastsService.leagueDefaultChannels(
                    league.getId(), season, country, turkish);

            List<TvGuideResponse.Match> matches = new ArrayList<>(leagueFixtures.size());
            for (Fixture fixture : leagueFixtures) {
                matches.add(toMatch(fixture, turkish));
            }

            Country leagueCountry = (league.getCountryName() != null)
                    ? countriesByName.get(league.getCountryName())
                    : null;
            leagues.add(new TvGuideResponse.League(
                    displayName(league, turkish),
                    SlugUtil.leagueSlug(displayName(league, turkish), league.getId()),
                    leagueLogo(league),
                    countryName(league, leagueCountry, turkish),
                    channels,
                    matches));
        }

        // Kanalı olan ligler ÜSTTE; ardından ülke adına, sonra lig adına göre.
        leagues.sort(Comparator
                .comparingInt((TvGuideResponse.League l) ->
                        (l.channels() != null && !l.channels().isEmpty()) ? 0 : 1)
                .thenComparing(l -> l.country() == null ? "" : l.country(),
                        String.CASE_INSENSITIVE_ORDER)
                .thenComparing(TvGuideResponse.League::name, String.CASE_INSENSITIVE_ORDER));

        return new TvGuideResponse(date.toString(), leagues);
    }

    private TvGuideResponse.Match toMatch(Fixture fixture, boolean turkish) {
        Team home = fixture.getHomeTeam();
        Team away = fixture.getAwayTeam();
        return new TvGuideResponse.Match(
                // Slug DAİMA İngilizce addan — maç detay/canonical ile birebir aynı.
                SlugUtil.fixtureSlug(home.getName(), away.getName(), fixture.getId()),
                fixture.getKickoffAt() != null ? fixture.getKickoffAt().toString() : null,
                fixture.getStatusShort(),
                messages.statusText(fixture.getStatusShort(), fixture.getStatusLong(), turkish),
                displayName(home, turkish),
                SlugUtil.teamSlug(displayName(home, turkish), home.getId()),
                teamLogo(home),
                fixture.getHomeGoals(),
                displayName(away, turkish),
                SlugUtil.teamSlug(displayName(away, turkish), away.getId()),
                teamLogo(away),
                fixture.getAwayGoals());
    }

    private ZoneId zone() {
        return ZoneId.of(footballProperties.sync().timezone());
    }

    /**
     * Bir varlığın görünen adı: dil "tr" ise ve Türkçe karşılığı (name_tr)
     * girilmişse Türkçe ad, aksi halde İngilizce kaynak ad.
     */
    private static String displayName(TranslatableName entity, boolean turkish) {
        if (turkish) {
            String tr = entity.getNameTr();
            if (tr != null && !tr.isBlank()) {
                return tr;
            }
        }
        return entity.getName();
    }

    /** Lig ülkesinin görünen adı — dil "tr" ise ve ülke name_tr girilmişse Türkçe. */
    private static String countryName(League league, Country country, boolean turkish) {
        if (turkish && country != null
                && country.getNameTr() != null && !country.getNameTr().isBlank()) {
            return country.getNameTr();
        }
        return league.getCountryName();
    }

    /** Takım logosu — daima kendi CDN'imizden; aynalanmadıysa null. */
    private String teamLogo(Team team) {
        return team.getLogoKey() != null ? storage.publicUrl(team.getLogoKey()) : null;
    }

    /** Lig logosu — daima kendi CDN'imizden; aynalanmadıysa null. */
    private String leagueLogo(League league) {
        return league.getLogoKey() != null ? storage.publicUrl(league.getLogoKey()) : null;
    }
}
