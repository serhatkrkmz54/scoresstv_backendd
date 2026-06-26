package com.scorestv.football.web;

import com.scorestv.common.SlugUtil;
import com.scorestv.football.FootballCacheNames;
import com.scorestv.football.FootballMessages;
import com.scorestv.football.FootballProperties;
import com.scorestv.football.domain.Country;
import com.scorestv.football.domain.CountryRepository;
import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureEventRepository;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.football.domain.League;
import com.scorestv.football.domain.Team;
import com.scorestv.football.domain.TranslatableName;
import com.scorestv.football.domain.Venue;
import com.scorestv.football.live.LiveFixtureMapper;
import com.scorestv.football.web.dto.FixtureDatesResponse;
import com.scorestv.football.web.dto.FixtureDayResponse;
import com.scorestv.football.web.dto.FixtureSummary;
import com.scorestv.football.web.dto.LeagueGroup;
import com.scorestv.football.web.dto.LiveFixturesResponse;
import com.scorestv.storage.MinioStorageService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Anasayfa fikstür listesini kendi veritabanımızdan okuyup yanıt DTO'suna
 * dönüştürür.
 *
 * <p>API-Football'a hiç gitmez — yalnızca aynalanan veriyi sunar. Sonuç kısa
 * süreli Redis cache'inde tutulur.
 *
 * <p><b>Görseller:</b> logo/bayrak URL'leri <b>daima kendi CDN'imizden</b>
 * (MinIO/nginx) verilir; API-Football'un media adresi asla servis edilmez
 * (hotlink yok). Görsel henüz MinIO'ya aynalanmadıysa ilgili alan {@code null}
 * döner — frontend kendi yer tutucusunu gösterir.
 */
@Service
public class FixtureQueryService {

    /**
     * Maçın o anda oynanıyor olduğunu belirten API-Football durum kodları.
     * INT (kesinti) ve SUSP (askıya alındı) "canlı" sayılmaz; oyun gerçekten
     * devam ediyor mu sorusunun kesin yanıtı için bu küme kullanılır.
     */
    private static final Set<String> LIVE_STATUSES =
            Set.of("1H", "HT", "2H", "ET", "BT", "P", "LIVE");

    /** Henüz başlamamış maçlar — "yaklaşan" filtresi için. */
    private static final Set<String> UPCOMING_STATUSES = Set.of("NS", "TBD");

    private final FixtureRepository fixtureRepository;
    private final FixtureEventRepository fixtureEventRepository;
    private final CountryRepository countryRepository;
    private final FootballProperties footballProperties;
    private final MinioStorageService storage;
    private final FootballMessages messages;
    private final LiveFixtureMapper liveFixtureMapper;

    public FixtureQueryService(FixtureRepository fixtureRepository,
                               FixtureEventRepository fixtureEventRepository,
                               CountryRepository countryRepository,
                               FootballProperties footballProperties,
                               MinioStorageService storage,
                               FootballMessages messages,
                               LiveFixtureMapper liveFixtureMapper) {
        this.fixtureRepository = fixtureRepository;
        this.fixtureEventRepository = fixtureEventRepository;
        this.countryRepository = countryRepository;
        this.footballProperties = footballProperties;
        this.storage = storage;
        this.messages = messages;
        this.liveFixtureMapper = liveFixtureMapper;
    }

    /** Site saat dilimine göre bugünün tarihi. */
    public LocalDate today() {
        return LocalDate.now(zone());
    }

    /**
     * Verilen günün maçlarını lige göre gruplu döner.
     *
     * <p><b>Canlı sarkma:</b> önceki günden hâlâ canlı oynanan maçlar (kickoff
     * geçmiş ama bitmemiş) misafir olarak bu günün listesine de eklenir; bitince
     * (status artık LIVE değil) otomatik düşer.
     *
     * <p><b>Filtre:</b> {@code filter} ile listeyi tümü/canlı/yaklaşan/bitmiş
     * olarak daraltır. {@code liveCount} alanı filtreden bağımsızdır — gün için
     * <i>gerçek</i> canlı sayısını verir (banner göstergesi için).
     *
     * <p><b>Dil:</b> {@code turkish} true ise takım/lig/ülke/stadyum adları
     * Türkçe karşılığı (name_tr) girilmiş olanlar için Türkçe verilir; girilmemişse
     * İngilizce kaynak ada düşülür. Cache her (tarih, filtre, dil) üçlüsü için
     * ayrı tutulur.
     */
    @Cacheable(value = FootballCacheNames.LIVE,
            key = "#date.toString() + '-' + #filter + '-' + (#turkish ? 'tr' : 'en')")
    @Transactional(readOnly = true)
    public FixtureDayResponse getFixturesByDate(LocalDate date,
                                                FixtureStatusFilter filter,
                                                boolean turkish) {
        ZoneId zone = zone();
        Instant start = date.atStartOfDay(zone).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(zone).toInstant();

        // Spillover (önceki günden gece sarkan canlı maçlar) SADECE BUGÜN
        // için geçerli. Gelecek günlerde "bugünün canlı maçları yarın
        // gözüküyor" hatasına yol açıyordu (bugün canlı olan tüm maçlar
        // yarın için spillover sayılıyordu).
        //
        // Geçmiş günlerde de spillover yok — biten maç zaten finished
        // statüsünde, canlı görünmez.
        LocalDate today = LocalDate.now(zone);
        Instant priorStart;
        if (date.isEqual(today)) {
            // Bugün: dün başlayıp hâlâ canlı olan maçlar (gece sarkan)
            priorStart = date.minusDays(1).atStartOfDay(zone).toInstant();
        } else {
            // Yarın veya geçmiş: spillover penceresi yok (boş range)
            priorStart = start;
        }

        // Bugünün maçları + (yalnız bugün için) önceki günden sarkan canlılar.
        List<Fixture> fixtures = fixtureRepository.findDayWithLiveSpillover(
                priorStart, start, end, LIVE_STATUSES);

        // Lig ülke bayraklarını CDN'den çözebilmek için ad -> ülke haritası (~170 satır).
        Map<String, Country> countriesByName = new HashMap<>();
        for (Country country : countryRepository.findAll()) {
            countriesByName.put(country.getName(), country);
        }

        // liveCount FİLTRE'den BAĞIMSIZ — gün için gerçek canlı sayısı (banner).
        int liveCount = 0;
        for (Fixture fixture : fixtures) {
            if (LIVE_STATUSES.contains(fixture.getStatusShort())) {
                liveCount++;
            }
        }

        // Filtre uygula. ALL durumunda kopya çıkarmadan listeyi olduğu gibi kullanırız.
        List<Fixture> filtered = (filter == FixtureStatusFilter.ALL)
                ? fixtures
                : applyFilter(fixtures, filter);

        // Filtre sonrası lige göre grupla.
        Map<Long, List<Fixture>> byLeague = new LinkedHashMap<>();
        for (Fixture fixture : filtered) {
            byLeague.computeIfAbsent(fixture.getLeague().getId(), key -> new ArrayList<>())
                    .add(fixture);
        }

        // Lig içindeki maçlar: **stabil kronolojik sıra** — başlama saati,
        // tiebreaker olarak fixture id. Status'a göre yeniden sıralama YOK
        // (eskiden canlı-önce vardı, bir maç canlı olunca lig içinde yukarı
        // ciksin de bitince geri dussun istemiyoruz — kullanici "takimlarin
        // yeri degisiyor" diye algilar). Canlilik vurgusu rozetle yapilir,
        // siralama kararlı kalir. SofaScore/FlashScore de bunu boyle yapar.
        Comparator<Fixture> byKickoff = Comparator
                .comparing(Fixture::getKickoffAt,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Fixture::getId);

        // Kırmızı kart sayıları — tek toplu sorgu (canlı tab rozeti için).
        Map<Long, Map<Long, Integer>> redCards = redCardCounts(filtered);

        List<LeagueGroup> groups = new ArrayList<>();
        for (List<Fixture> leagueFixtures : byLeague.values()) {
            leagueFixtures.sort(byKickoff);
            League league = leagueFixtures.get(0).getLeague();
            List<FixtureSummary> summaries = new ArrayList<>();
            for (Fixture fixture : leagueFixtures) {
                summaries.add(toSummary(fixture, turkish, redCards));
            }
            groups.add(new LeagueGroup(toLeagueInfo(league, countriesByName, turkish), summaries));
        }

        // Sıralama:
        //  1. Featured ligler (config.serving.featuredLeagueIds) — sıra korunur.
        //  2. Geri kalan: ülke adına göre alfabetik (lokalize).
        //  3. Aynı ülke içinde: lig adına göre alfabetik (kupa/üst-alt lig ayrımı).
        List<Long> featured = footballProperties.serving().featuredLeagueIds();
        Map<Long, Integer> featuredRank = new HashMap<>();
        for (int i = 0; i < featured.size(); i++) {
            featuredRank.put(featured.get(i), i);
        }
        groups.sort(Comparator
                .comparingInt((LeagueGroup group) ->
                        featuredRank.getOrDefault(group.league().id(), Integer.MAX_VALUE))
                .thenComparing(group ->
                        group.league().country() == null ? "" : group.league().country(),
                        String.CASE_INSENSITIVE_ORDER)
                .thenComparing(group -> group.league().name(), String.CASE_INSENSITIVE_ORDER));

        return new FixtureDayResponse(date.toString(), filtered.size(), liveCount, groups);
    }

    /** Filtre setine uyan maçları döner. */
    private static List<Fixture> applyFilter(List<Fixture> all, FixtureStatusFilter filter) {
        List<Fixture> result = new ArrayList<>();
        for (Fixture fixture : all) {
            if (matchesFilter(fixture.getStatusShort(), filter)) {
                result.add(fixture);
            }
        }
        return result;
    }

    /** Tek bir durum kodunun filtreye uyup uymadığı. */
    private static boolean matchesFilter(String statusShort, FixtureStatusFilter filter) {
        return switch (filter) {
            case ALL -> true;
            case LIVE -> LIVE_STATUSES.contains(statusShort);
            case UPCOMING -> UPCOMING_STATUSES.contains(statusShort);
            case FINISHED -> !LIVE_STATUSES.contains(statusShort)
                    && !UPCOMING_STATUSES.contains(statusShort);
        };
    }

    private ZoneId zone() {
        return ZoneId.of(footballProperties.sync().timezone());
    }

    private FixtureSummary toSummary(Fixture fixture, boolean turkish,
                                     Map<Long, Map<Long, Integer>> redCardsByFixture) {
        Team home = fixture.getHomeTeam();
        Team away = fixture.getAwayTeam();
        Map<Long, Integer> rc = (redCardsByFixture == null)
                ? null : redCardsByFixture.get(fixture.getId());
        int homeRed = (rc == null) ? 0 : rc.getOrDefault(home.getId(), 0);
        int awayRed = (rc == null) ? 0 : rc.getOrDefault(away.getId(), 0);
        return new FixtureSummary(
                fixture.getId(),
                // Slug dile göre lokalize: TR'de name_tr (varsa), yoksa orijinal ad —
                // ekranda görünen takım adıyla birebir aynı. id ile çözüldüğü için güvenli.
                SlugUtil.fixtureSlug(displayName(home, turkish), displayName(away, turkish), fixture.getId()),
                toLeagueRef(fixture.getLeague(), turkish),
                messages.roundText(fixture.getRound(), turkish),
                fixture.getKickoffAt(),
                fixture.getLastSyncedAt(),
                FixtureSummary.Status.of(
                        fixture.getStatusShort(),
                        // Durum kodu evrenseldir; uzun metin dile göre çevrilir.
                        messages.statusText(
                                fixture.getStatusShort(), fixture.getStatusLong(), turkish),
                        fixture.getElapsed(), fixture.getStatusExtra()),
                new FixtureSummary.Team(
                        home.getId(), displayName(home, turkish), teamLogo(home),
                        SlugUtil.teamSlug(displayName(home, turkish), home.getId())),
                new FixtureSummary.Team(
                        away.getId(), displayName(away, turkish), teamLogo(away),
                        SlugUtil.teamSlug(displayName(away, turkish), away.getId())),
                new FixtureSummary.Score(
                        fixture.getHomeGoals(), fixture.getAwayGoals(),
                        period(fixture.getScoreHtHome(), fixture.getScoreHtAway()),
                        period(fixture.getScoreEtHome(), fixture.getScoreEtAway()),
                        period(fixture.getScorePenHome(), fixture.getScorePenAway())),
                toVenue(fixture.getVenue(), turkish),
                homeRed,
                awayRed);
    }

    /**
     * Verilen maçlar için kırmızı kart sayılarını TOPLU sorgu ile çözer:
     * {@code fixtureId -> (teamId -> adet)}. Boş liste için boş map.
     */
    private Map<Long, Map<Long, Integer>> redCardCounts(List<Fixture> fixtures) {
        if (fixtures == null || fixtures.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = new ArrayList<>(fixtures.size());
        for (Fixture f : fixtures) {
            ids.add(f.getId());
        }
        Map<Long, Map<Long, Integer>> out = new HashMap<>();
        for (Object[] row : fixtureEventRepository.countRedCardsByFixtureIds(ids)) {
            Long fixtureId = ((Number) row[0]).longValue();
            Long teamId = ((Number) row[1]).longValue();
            int count = ((Number) row[2]).intValue();
            out.computeIfAbsent(fixtureId, k -> new HashMap<>()).put(teamId, count);
        }
        return out;
    }

    /** Tek bir maca ait lig ozeti (FixtureSummary icine). */
    private FixtureSummary.LeagueRef toLeagueRef(League league, boolean turkish) {
        if (league == null) return null;
        String name = displayName(league, turkish);
        return new FixtureSummary.LeagueRef(
                league.getId(),
                name,
                messages.leagueType(league.getType(), turkish),
                leagueLogo(league),
                SlugUtil.leagueSlug(name, league.getId()));
    }

    private FixtureSummary.Venue toVenue(Venue venue, boolean turkish) {
        return venue == null ? null
                : new FixtureSummary.Venue(displayName(venue, turkish), venue.getCity());
    }

    private LeagueGroup.LeagueInfo toLeagueInfo(League league,
                                               Map<String, Country> countriesByName,
                                               boolean turkish) {
        Country country = (league.getCountryName() != null)
                ? countriesByName.get(league.getCountryName())
                : null;
        return new LeagueGroup.LeagueInfo(
                league.getId(),
                displayName(league, turkish),
                messages.leagueType(league.getType(), turkish),
                leagueLogo(league),
                countryName(league, country, turkish),
                countryFlag(country),
                league.isCovered(),
                SlugUtil.leagueSlug(displayName(league, turkish), league.getId()));
    }

    /**
     * Lig ülkesinin görünen adı. Lig kaydındaki İngilizce {@code countryName}
     * yerine, dil "tr" ise ve eşleşen ülkenin Türkçe adı girilmişse Türkçe ad
     * verilir; aksi halde İngilizce ada düşülür.
     */
    private static String countryName(League league, Country country, boolean turkish) {
        if (turkish && country != null
                && country.getNameTr() != null && !country.getNameTr().isBlank()) {
            return country.getNameTr();
        }
        return league.getCountryName();
    }

    /**
     * Bir varlığın görünen adı: dil "tr" ise ve Türkçe karşılığı (name_tr)
     * girilmişse Türkçe ad, aksi halde İngilizce kaynak ad.
     */
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

    /**
     * Takım logosu — DAİMA kendi CDN'imizden. Görsel MinIO'ya aynalanmadıysa
     * null döner; API-Football media adresi asla verilmez (hotlink yok).
     */
    private String teamLogo(Team team) {
        return team.getLogoKey() != null ? storage.publicUrl(team.getLogoKey()) : null;
    }

    /** Lig logosu — daima kendi CDN'imizden; aynalanmadıysa null. */
    private String leagueLogo(League league) {
        return league.getLogoKey() != null ? storage.publicUrl(league.getLogoKey()) : null;
    }

    /** Ülke bayrağı — daima kendi CDN'imizden; aynalanmadıysa null. */
    private String countryFlag(Country country) {
        return (country != null && country.getFlagKey() != null)
                ? storage.publicUrl(country.getFlagKey())
                : null;
    }

    /**
     * Anasayfa tarih şeridi: site saat dilimine göre bugün ± {@code days} gün
     * için (toplam, canlı) maç sayıları. {@code days} 1-15 aralığına kırpılır.
     */
    @Cacheable(value = FootballCacheNames.LIVE,
            key = "'dates-' + #days + '-' + (#turkish ? 'tr' : 'en')")
    @Transactional(readOnly = true)
    public FixtureDatesResponse getDates(int days, boolean turkish) {
        int bounded = Math.max(1, Math.min(days, 15));
        ZoneId zone = zone();
        LocalDate today = LocalDate.now(zone);
        LocalDate startDate = today.minusDays(bounded);
        LocalDate endExclusive = today.plusDays(bounded + 1L);
        Instant start = startDate.atStartOfDay(zone).toInstant();
        Instant end = endExclusive.atStartOfDay(zone).toInstant();

        // Native sorgu: gün gün toplam ve canlı maç sayıları.
        Map<LocalDate, long[]> byDate = new HashMap<>();
        for (Object[] row :
                fixtureRepository.aggregateByDay(zone.getId(), start, end)) {
            // Hibernate 6+ PG DATE → java.time.LocalDate (java.sql.Date değil).
            LocalDate day = (LocalDate) row[0];
            long total = ((Number) row[1]).longValue();
            long live = ((Number) row[2]).longValue();
            byDate.put(day, new long[]{total, live});
        }

        // Pencerenin TÜM günlerini sırayla doldur — boş günler de 0/0 ile gelsin.
        List<FixtureDatesResponse.DateEntry> entries = new ArrayList<>();
        for (LocalDate d = startDate; d.isBefore(endExclusive); d = d.plusDays(1)) {
            long[] counts = byDate.getOrDefault(d, new long[]{0L, 0L});
            entries.add(new FixtureDatesResponse.DateEntry(
                    d.toString(), dayName(d, today, turkish), counts[0], counts[1]));
        }
        return new FixtureDatesResponse(today.toString(), entries);
    }

    /**
     * Şu an oynanan tüm canlı maçlar — kompakt liste, başlama saatine göre.
     * Canlı banner / mobil widget için; 30 sn'lik cache ile polling dostudur.
     */
    @Cacheable(value = FootballCacheNames.LIVE,
            key = "'live-' + (#turkish ? 'tr' : 'en')")
    @Transactional(readOnly = true)
    public LiveFixturesResponse getLive(boolean turkish) {
        List<Fixture> live = fixtureRepository.findLiveWithDetails(LIVE_STATUSES);
        List<LiveFixturesResponse.LiveFixture> items = new ArrayList<>(live.size());
        for (Fixture fixture : live) {
            items.add(liveFixtureMapper.toLiveFixture(fixture, turkish));
        }
        return new LiveFixturesResponse(Instant.now(), items.size(), items);
    }

    /**
     * Belirli ID'lerdeki maclari ozet listesi olarak doner.
     *
     * <p>Mobile <b>Favoriler</b> tabi icin — favori fixture id listesi lokal
     * tutuldugundan, FavoritesScreen acildiginda bu endpoint'ten tek seferde
     * tum favori maclarin FixtureSummary'sini ceker.
     *
     * <p><b>Siralama:</b> kickoffAt ASC (yaklasan/bugun ust, gecmis alt).
     * <p><b>Maks:</b> 50 id — DOS koruma.
     *
     * @param ids     fixture id listesi (1-50)
     * @param turkish dile gore TR/EN takim+lig adlari
     */
    @Transactional(readOnly = true)
    public List<FixtureSummary> byIds(List<Long> ids, boolean turkish) {
        if (ids == null || ids.isEmpty()) return List.of();
        // Maks 50 id ile sinirla — DOS koruma
        List<Long> limited = ids.size() > 50 ? ids.subList(0, 50) : ids;
        List<Fixture> fixtures = fixtureRepository.findAllById(limited);
        // Kickoff'a gore sirala (yaklasanlar ust)
        fixtures.sort(Comparator
                .comparing(Fixture::getKickoffAt,
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Fixture::getId));
        Map<Long, Map<Long, Integer>> redCards = redCardCounts(fixtures);
        List<FixtureSummary> out = new ArrayList<>(fixtures.size());
        for (Fixture f : fixtures) {
            out.add(toSummary(f, turkish, redCards));
        }
        return out;
    }

    /**
     * Tarih şeridi için gün adı: "Bugün" / "Yarın" / "Dün" ya da dile göre
     * kısa gün adı ("Pzt", "Mon"...).
     */
    private static String dayName(LocalDate date, LocalDate today, boolean turkish) {
        if (date.isEqual(today)) {
            return turkish ? "Bugün" : "Today";
        }
        if (date.isEqual(today.plusDays(1))) {
            return turkish ? "Yarın" : "Tomorrow";
        }
        if (date.isEqual(today.minusDays(1))) {
            return turkish ? "Dün" : "Yesterday";
        }
        Locale locale = turkish ? Locale.of("tr") : Locale.ENGLISH;
        return DateTimeFormatter.ofPattern("EEE", locale).format(date);
    }
}
