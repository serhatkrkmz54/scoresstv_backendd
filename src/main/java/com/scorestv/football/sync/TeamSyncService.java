package com.scorestv.football.sync;

import com.scorestv.football.ApiFootballClient;
import com.scorestv.football.ApiFootballResponse;
import com.scorestv.football.domain.Season;
import com.scorestv.football.domain.SeasonRepository;
import com.scorestv.football.domain.TeamRepository;
import com.scorestv.football.sync.dto.TeamApiDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Takımları ve stadyumlarını API-Football'dan senkronlar — <b>tam tarihsel
 * arşiv</b>.
 *
 * <p>{@code /teams} endpoint'i lig+sezon bazlı çalışır. Senkron her
 * <b>(lig, sezon)</b> çifti için bir çağrı yapar ({@code /teams?league=X&season=Y});
 * böylece yalnızca güncel kadrolar değil, geçmiş sezonların (küme düşmüş,
 * kapanmış) takımları da gelir. Dönen takımlar — tam stadyum nesneleriyle —
 * {@link TeamUpserter} ile DB'ye yazılır.
 *
 * <p>Dakikalık hız sınırlaması {@code ApiFootballClient}'ta merkezî olarak
 * yapılır; bu servis ayrıca throttle uygulamaz.
 */
@Service
public class TeamSyncService {

    private static final Logger log = LoggerFactory.getLogger(TeamSyncService.class);

    private static final ParameterizedTypeReference<ApiFootballResponse<List<TeamApiDto>>> TEAMS_TYPE =
            new ParameterizedTypeReference<ApiFootballResponse<List<TeamApiDto>>>() {
            };

    private final ApiFootballClient client;
    private final TeamUpserter upserter;
    private final SeasonRepository seasonRepository;
    private final TeamRepository teamRepository;

    public TeamSyncService(ApiFootballClient client,
                           TeamUpserter upserter,
                           SeasonRepository seasonRepository,
                           TeamRepository teamRepository) {
        this.client = client;
        this.upserter = upserter;
        this.seasonRepository = seasonRepository;
        this.teamRepository = teamRepository;
    }

    /**
     * Tüm liglerin TÜM sezonlarındaki takımları ve stadyumları senkronlar
     * (tam tarihsel arşiv). Her (lig, sezon) çifti için bir {@code /teams}
     * çağrısı; bir çift başarısız olsa bile devam edilir.
     */
    public TeamSyncResult syncAll() {
        List<Season> seasons = seasonRepository.findAllWithLeague();
        log.info("Takım senkronu başladı: {} (lig, sezon) çifti taranacak", seasons.size());

        int processed = 0;
        int failed = 0;
        int teamsUpserted = 0;
        for (Season season : seasons) {
            try {
                teamsUpserted += syncLeague(season.getLeague().getId(), season.getYear());
                processed++;
            } catch (RuntimeException ex) {
                failed++;
                log.warn("(lig,sezon) takımları senkronlanamadı: leagueId={} season={} hata={}",
                        season.getLeague().getId(), season.getYear(), ex.getMessage());
            }
        }

        TeamSyncResult result = new TeamSyncResult(processed, failed, teamsUpserted);
        log.info("Takım senkronu bitti: {}", result);
        return result;
    }

    /**
     * Tek bir (lig, sezon) için {@code /teams} çağrısı + upsert.
     *
     * <p>Upserter junction tablosuna da yazar — sonradan
     * "lig X sezon Y'deki takimlar" sorgusu fixtures'a bagimli olmadan KESIN
     * cevap verir (favori takim secimi gibi UI akislari icin kritik).
     *
     * @return upsert edilen takım sayısı
     */
    public int syncLeague(Long leagueId, Integer season) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("league", leagueId);
        params.put("season", season);

        ApiFootballResponse<List<TeamApiDto>> response =
                client.get("/teams", params, TEAMS_TYPE);
        List<TeamApiDto> items = response.response();
        if (items == null || items.isEmpty()) {
            return 0;
        }
        int upserted = upserter.upsertForLeagueSeason(items, leagueId, season);
        log.info("Takım senkronu: leagueId={} season={} — {} takım upsert edildi",
                leagueId, season, upserted);
        return upserted;
    }

    /**
     * Tek bir takimi {@code /teams?id=X} ile ceker ve upsert eder. Takim
     * detay sayfasinin lazy sync'i bunu cagirir.
     *
     * @return upsert edilen takim sayisi (0 veya 1)
     */
    public int syncOne(Long teamId) {
        ApiFootballResponse<List<TeamApiDto>> response = client.get(
                "/teams", Map.of("id", teamId), TEAMS_TYPE);
        List<TeamApiDto> items = response.response();
        if (items == null || items.isEmpty()) {
            log.info("Tek-takim senkronu: API bos dondu teamId={}", teamId);
            return 0;
        }
        int upserted = upserter.upsert(items);
        log.info("Tek-takim senkronu: teamId={} — {} takim upsert", teamId, upserted);
        return upserted;
    }

    /** Tek bir ligin TÜM sezonlarını senkronlar (id'sinden). */
    public int syncOneLeague(Long leagueId) {
        int total = 0;
        for (Season season : seasonRepository.findByLeagueIdOrderByYearDesc(leagueId)) {
            try {
                total += syncLeague(leagueId, season.getYear());
            } catch (RuntimeException ex) {
                log.warn("Lig sezonu senkronlanamadı: leagueId={} season={} hata={}",
                        leagueId, season.getYear(), ex.getMessage());
            }
        }
        return total;
    }

    /**
     * Başlangıç senkronu: hiçbir takıma stadyum bağlı değilse (= takım senkronu
     * hiç çalışmamış) tam arşiv senkronu yapar, aksi halde atlar.
     */
    public TeamSyncResult syncIfNeeded() {
        if (teamRepository.existsByVenueIsNotNull()) {
            log.info("Takımlar zaten detaylı (stadyum bağlı); başlangıç takım senkronu atlandı.");
            return new TeamSyncResult(0, 0, 0);
        }
        return syncAll();
    }
}
