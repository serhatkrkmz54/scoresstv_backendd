package com.scorestv.football.sync;

import com.scorestv.football.ApiFootballClient;
import com.scorestv.football.ApiFootballResponse;
import com.scorestv.football.FootballProperties;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.football.sync.dto.FixtureApiDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Anasayfa fikstür penceresini (bugün ±N gün) API-Football'dan senkronlar.
 *
 * <p>Her tarih için bir {@code /fixtures?date=} çağrısı yapılır — bu çağrı o
 * günün <b>tüm liglerinin</b> maçlarını döner. Sonuç {@link FixtureUpserter}
 * ile DB'ye yazılır. HTTP çağrısı transaction dışında, DB yazımı transaction
 * içinde tutulur.
 *
 * <p>Pencere tarihleri her çağrıda çalışma anındaki "bugün"e göre yeniden
 * hesaplanır; böylece pencere her gün otomatik ileri kayar ve daima ±N gün
 * tam kalır.
 */
@Service
public class FixtureSyncService {

    private static final Logger log = LoggerFactory.getLogger(FixtureSyncService.class);

    private static final ParameterizedTypeReference<ApiFootballResponse<List<FixtureApiDto>>> FIXTURES_TYPE =
            new ParameterizedTypeReference<ApiFootballResponse<List<FixtureApiDto>>>() {
            };

    private final ApiFootballClient client;
    private final FixtureUpserter upserter;
    private final FixtureRepository fixtureRepository;
    private final FootballProperties properties;

    /**
     * Gecmis tarih lazy finalize debounce — ayni tarih icin 30dk'da bir kez
     * tetiklenir. Public API'den cagrilan finalize burayla orchestrate edilir.
     */
    private static final Duration PAST_DATE_FINALIZE_DEBOUNCE =
            Duration.ofMinutes(30);
    private final Map<LocalDate, Instant> _lastPastDateFinalize =
            new ConcurrentHashMap<>();

    /**
     * BUGUN icin daha sik debounce (60sn) — kullanici pull-to-refresh yaparsa
     * stale status'ler hizlica taze cekilir. LiveTickerJob yetisemezse bile
     * elle tetiklenen sync devreye girer.
     */
    private static final Duration TODAY_FINALIZE_DEBOUNCE = Duration.ofSeconds(60);
    private final Map<LocalDate, Instant> _lastTodayFinalize =
            new ConcurrentHashMap<>();

    public FixtureSyncService(ApiFootballClient client,
                              FixtureUpserter upserter,
                              FixtureRepository fixtureRepository,
                              FootballProperties properties) {
        this.client = client;
        this.upserter = upserter;
        this.fixtureRepository = fixtureRepository;
        this.properties = properties;
    }

    /**
     * Gecmis bir tarih ziyaret edildiginde otomatik finalize tetikler.
     *
     * <p>Senaryo: kullanici 29 Mayis'a kaydirdi — DB'de o tarihteki maclar
     * NS/1H/2H gibi takili statusler ile durabilir (orn. cron eski olmadan
     * uzun sure boyunca o tarih ziyaret edilmedi). Bu metod o tarih icin
     * yeni bir {@code /fixtures?date=} cagrisi yaparak final statuleri ceker.
     *
     * <p><b>Debounce:</b> ayni tarih icin 30dk'da bir kez.
     * <b>Filtreleme:</b> sadece bugunden eski tarihler — bugun zaten
     * TodayRefreshJob ile her saat tazeleniyor, gelecek tarihte sync anlamsiz.
     */
    @Async
    public void ensureDateFinalizedAsync(LocalDate date) {
        if (date == null) return;
        LocalDate today = LocalDate.now(ZoneId.of(properties.sync().timezone()));
        if (!date.isBefore(today)) return; // sadece gecmis

        Instant last = _lastPastDateFinalize.get(date);
        if (last != null
                && last.isAfter(Instant.now().minus(PAST_DATE_FINALIZE_DEBOUNCE))) {
            return;
        }
        _lastPastDateFinalize.put(date, Instant.now());

        try {
            int n = syncDate(date);
            log.info("Gecmis tarih lazy finalize: {} → {} mac yeniden upsert", date, n);
        } catch (RuntimeException ex) {
            log.warn("Gecmis tarih lazy finalize hatasi {}: {}", date, ex.getMessage());
        }
    }

    /**
     * BUGUN icin lazy finalize — public /fixtures cagrisi sirasinda
     * 60sn debounce ile tetiklenir. Stuck status'leri (1H/2H hala canli
     * gozuken ama API tarafinda FT olmus) zorla yeniden ceker.
     *
     * <p>LiveTickerJob zaten 15sn'de bir calisir AMA pull-to-refresh ile
     * kullanici instant yenileme yapmak isterse devreye girer.
     */
    @Async
    public void ensureTodayFinalizedAsync(LocalDate date) {
        if (date == null) return;
        Instant last = _lastTodayFinalize.get(date);
        if (last != null
                && last.isAfter(Instant.now().minus(TODAY_FINALIZE_DEBOUNCE))) {
            return;
        }
        _lastTodayFinalize.put(date, Instant.now());

        try {
            int n = syncDate(date);
            log.info("Bugun lazy finalize: {} → {} mac taze upsert", date, n);
        } catch (RuntimeException ex) {
            log.warn("Bugun lazy finalize hatasi {}: {}", date, ex.getMessage());
        }
    }

    /**
     * Yapılandırılmış saat dilimine göre pencere tarihlerini hesaplar:
     * bugün-öncesi ... bugün ... bugün+sonrası. Çalışma anındaki "bugün"e
     * göre hesaplandığı için pencere her gün kendiliğinden ileri kayar.
     */
    public List<LocalDate> windowDates() {
        FootballProperties.Sync sync = properties.sync();
        LocalDate today = LocalDate.now(ZoneId.of(sync.timezone()));
        List<LocalDate> dates = new ArrayList<>();
        for (int offset = -sync.windowDaysBefore(); offset <= sync.windowDaysAfter(); offset++) {
            dates.add(today.plusDays(offset));
        }
        return dates;
    }

    /**
     * Tüm pencereyi yeniden senkronlar (tam tarama). Günlük zamanlanmış işin
     * kullandığı yöntem budur: her gün tüm pencere tazelenir, böylece kayan
     * pencere daima güncel kalır. Bir tarih başarısız olsa bile (örn. kota
     * hatası) diğerlerine devam edilir.
     */
    public FixtureSyncResult syncWindow() {
        List<LocalDate> dates = windowDates();
        log.info("Tam fikstür penceresi senkronu başladı: {} tarih ({} .. {})",
                dates.size(), dates.getFirst(), dates.getLast());

        int datesSucceeded = 0;
        int datesFailed = 0;
        int fixturesUpserted = 0;
        for (LocalDate date : dates) {
            try {
                fixturesUpserted += syncDate(date);
                datesSucceeded++;
            } catch (RuntimeException ex) {
                datesFailed++;
                log.error("Tarih senkronu başarısız: {} — {}", date, ex.getMessage());
            }
        }

        FixtureSyncResult result =
                new FixtureSyncResult(datesSucceeded, datesFailed, fixturesUpserted);
        log.info("Tam fikstür penceresi senkronu bitti: {}", result);
        return result;
    }

    /**
     * Yalnızca DB'de hiç maçı bulunmayan pencere tarihlerini senkronlar.
     *
     * <p>Başlangıç senkronunda kullanılır: taze bir sistemde tüm pencereyi
     * çeker, yeniden başlatmalarda ise zaten dolu tarihleri atlayarak API
     * kotasını korur. Pencereye yeni giren uç tarih de boş olacağı için
     * otomatik çekilir.
     */
    public FixtureSyncResult syncMissingDates() {
        List<LocalDate> dates = windowDates();
        ZoneId zone = ZoneId.of(properties.sync().timezone());
        log.info("Eksik tarih senkronu başladı: {} tarih taranıyor", dates.size());

        int datesSucceeded = 0;
        int datesFailed = 0;
        int datesSkipped = 0;
        int fixturesUpserted = 0;
        for (LocalDate date : dates) {
            Instant start = date.atStartOfDay(zone).toInstant();
            Instant end = date.plusDays(1).atStartOfDay(zone).toInstant();
            if (fixtureRepository.existsByKickoffAtGreaterThanEqualAndKickoffAtLessThan(start, end)) {
                datesSkipped++;
                continue;
            }
            try {
                fixturesUpserted += syncDate(date);
                datesSucceeded++;
            } catch (RuntimeException ex) {
                datesFailed++;
                log.error("Eksik tarih senkronu başarısız: {} — {}", date, ex.getMessage());
            }
        }

        log.info("Eksik tarih senkronu bitti: {} senkronlandı, {} zaten doluydu, {} başarısız",
                datesSucceeded, datesSkipped, datesFailed);
        return new FixtureSyncResult(datesSucceeded, datesFailed, fixturesUpserted);
    }

    /**
     * Belirli bir lig+sezon icin TUM fikstürleri ceker ve upsert eder.
     * {@code GET /fixtures?league=X&season=Y} tek istekte sezonun tamamini
     * doner (PL icin ~380 mac). Lig detay sayfasinin lazy sync'i bunu cagirir.
     *
     * @return upsert edilen mac sayisi
     */
    public int syncLeagueSeason(Long leagueId, Integer season) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("league", leagueId);
        params.put("season", season);
        params.put("timezone", properties.sync().timezone());

        ApiFootballResponse<List<FixtureApiDto>> response =
                client.get("/fixtures", params, FIXTURES_TYPE);

        List<FixtureApiDto> items = response.response();
        if (items == null || items.isEmpty()) {
            log.info("Lig+sezon fikstür sync: leagueId={} season={} — mac yok",
                    leagueId, season);
            return 0;
        }
        int upserted = upserter.upsert(items);
        log.info("Lig+sezon fikstür sync: leagueId={} season={} — {} mac upsert",
                leagueId, season, upserted);
        return upserted;
    }

    /**
     * Tek bir tarihi senkronlar: {@code /fixtures?date=} çağrısı + upsert.
     *
     * @return o tarih için upsert edilen maç sayısı
     */
    public int syncDate(LocalDate date) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("date", date.toString());
        params.put("timezone", properties.sync().timezone());

        ApiFootballResponse<List<FixtureApiDto>> response =
                client.get("/fixtures", params, FIXTURES_TYPE);

        List<FixtureApiDto> items = response.response();
        if (items == null || items.isEmpty()) {
            log.info("Fikstür senkronu: {} — maç yok", date);
            return 0;
        }
        int upserted = upserter.upsert(items);
        log.info("Fikstür senkronu: {} — {} maç upsert edildi", date, upserted);
        return upserted;
    }
}
