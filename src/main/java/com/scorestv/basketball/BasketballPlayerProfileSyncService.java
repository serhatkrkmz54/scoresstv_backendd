package com.scorestv.basketball;

import com.scorestv.basketball.domain.BasketballLeague;
import com.scorestv.basketball.domain.BasketballLeagueRepository;
import com.scorestv.basketball.domain.BasketballPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * API-Basketball {@code /players?id=X&season=Y} sync — tek oyuncu profil
 * + sezonluk istatistikleri. Master tabloya foto/dogum/ulke/boy/kilo +
 * BasketballPlayerSeasonStat'a sezon ortalamalari yazilir.
 *
 * <p>Cagri yollari:
 * <ul>
 *   <li>Lazy on-demand: kullanici player detay sayfasi acinca freshness
 *       gate (lastProfileSyncedAt) eskiyse trigger.
 *   <li>Daily refresh: covered basketbol ligler icin gunluk + top 50
 *       oyuncu icin periyodik tazeleme.
 * </ul>
 *
 * <p>Toplu sync (bir lig+sezondaki TUM oyuncular) icin ayri olarak
 * {@code BasketballTopPlayersSyncService.syncPlayersForLeagueSeason}
 * kullanilir — orada {@code /players?league=X&season=Y} sayfali cagri
 * yapilir ve master tablo + sezon stat ayni cagriden beslenir (kotaya
 * dostu).
 */
@Service
public class BasketballPlayerProfileSyncService {

    private static final Logger log = LoggerFactory.getLogger(BasketballPlayerProfileSyncService.class);

    private final BasketballApiClient apiClient;
    private final BasketballPlayerUpserter playerUpserter;
    private final BasketballLeagueRepository leagueRepo;

    public BasketballPlayerProfileSyncService(BasketballApiClient apiClient,
                                              BasketballPlayerUpserter playerUpserter,
                                              BasketballLeagueRepository leagueRepo) {
        this.apiClient = apiClient;
        this.playerUpserter = playerUpserter;
        this.leagueRepo = leagueRepo;
    }

    /**
     * Tek oyuncu icin {@code /players?id=X&season=Y} cagrisi + full upsert.
     *
     * <p>{@code leagueId} parametre olarak verilirse master tabloda team +
     * jersey + position o ligin context'i ile dolar; verilmezse oyuncunun
     * dondurdugu ilk lig'in stat'i kullanilir.
     *
     * @param playerId API-Basketball oyuncu id
     * @param leagueId opsiyonel — hangi lig context'inde (null ise auto-pick)
     * @param season   "YYYY-YYYY" formatinda sezon
     * @return upsert edilmis player, API bos cevap dondurduyse null
     */
    public BasketballPlayer syncProfile(long playerId, Long leagueId, String season) {
        if (season == null || season.isBlank()) {
            log.warn("Player profile sync: sezon bos playerId={}", playerId);
            return null;
        }
        try {
            List<BkPlayerDto> resp = apiClient.fetchPlayerById(playerId, season);
            if (resp == null || resp.isEmpty()) {
                log.debug("Player profile bulunamadi id={} season={}", playerId, season);
                return null;
            }
            BkPlayerDto dto = resp.get(0);
            BasketballLeague league = leagueId != null
                    ? leagueRepo.findById(leagueId).orElse(null)
                    : null;
            BasketballPlayer saved = playerUpserter.upsertFromProfile(dto, league, season);
            if (saved != null) {
                log.info("Basketbol player profil tazelendi id={} name={} season={}",
                        saved.getId(), saved.getName(), season);
            }
            return saved;
        } catch (Exception e) {
            log.warn("Basketbol player profil sync hata id={} season={}: {}",
                    playerId, season, e.toString());
            return null;
        }
    }
}
