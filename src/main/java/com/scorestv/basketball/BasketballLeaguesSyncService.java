package com.scorestv.basketball;

import com.scorestv.basketball.domain.BasketballLeague;
import com.scorestv.basketball.domain.BasketballLeagueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * API-Basketball {@code /leagues} endpoint senkronu — lig info + sezonlar
 * + ulke + coverage flag'leri.
 *
 * <p>Bu servis "covered" basketbol ligler icin daily refresh job tarafindan
 * cagrilir. Ayrica:
 * <ul>
 *   <li>Lig detay sayfasi acilisinda {@code lastInfoSyncedAt}'a gore lazy
 *       refresh (24 saatlik freshness).
 *   <li>Cold-start: yeni bir lig URL'inden geldiyse on-demand cagri.
 * </ul>
 *
 * <p>Futboldaki {@code LeaguesSyncService} patterninin basketbol esi.
 * Kotaya etkisi minimal: covered basketbol lig sayisi ~10-20, gunluk bir
 * cagri = 20 istek/gun.
 */
@Service
public class BasketballLeaguesSyncService {

    private static final Logger log = LoggerFactory.getLogger(BasketballLeaguesSyncService.class);

    private final BasketballApiClient apiClient;
    private final BasketballLeagueRepository leagueRepo;
    private final BasketballLeagueUpserter upserter;

    public BasketballLeaguesSyncService(BasketballApiClient apiClient,
                                        BasketballLeagueRepository leagueRepo,
                                        BasketballLeagueUpserter upserter) {
        this.apiClient = apiClient;
        this.leagueRepo = leagueRepo;
        this.upserter = upserter;
    }

    /**
     * Tek lig icin {@code /leagues?id=X} cagrisi + upsert. Yeni veya guncelleme.
     *
     * @param leagueId API-Basketball lig id
     * @return upsert edilmis entity, API bos cevap dondurduyse null
     */
    @Transactional
    public BasketballLeague syncLeagueInfo(long leagueId) {
        try {
            List<BkLeagueDto> resp = apiClient.fetchLeagueById(leagueId);
            if (resp == null || resp.isEmpty()) {
                log.warn("Basketbol lig info bulunamadi: id={}", leagueId);
                return null;
            }
            BkLeagueDto dto = resp.get(0);
            BasketballLeague saved = upserter.upsertFromApi(dto);
            log.info("Basketbol lig info tazelendi id={} name={} seasons={}",
                    leagueId,
                    saved != null ? saved.getName() : "?",
                    dto.seasons() != null ? dto.seasons().size() : 0);
            return saved;
        } catch (Exception e) {
            log.warn("Basketbol lig info sync hata id={}: {}", leagueId, e.toString());
            return null;
        }
    }

    /**
     * Covered tum basketbol ligler icin info tazeleme.
     * Daily refresh job tarafindan cagrilir; her lig icin /leagues?id=X.
     *
     * @return basariyla tazelenen lig sayisi
     */
    public int syncAllCovered() {
        List<BasketballLeague> covered = leagueRepo.findByCoveredTrue();
        if (covered.isEmpty()) {
            log.debug("Basketbol lig info sync: covered lig yok");
            return 0;
        }
        int ok = 0;
        for (BasketballLeague l : covered) {
            try {
                if (syncLeagueInfo(l.getId()) != null) ok++;
            } catch (Exception e) {
                log.warn("Basketbol lig info sync hata id={}: {}",
                        l.getId(), e.toString());
            }
        }
        log.info("Basketbol lig info sync tamam: {}/{} basarili", ok, covered.size());
        return ok;
    }
}
