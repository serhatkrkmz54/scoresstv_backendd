package com.scorestv.volleyball;

import com.scorestv.volleyball.domain.VolleyballLeague;
import com.scorestv.volleyball.domain.VolleyballLeagueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * API-Volleyball {@code /leagues} endpoint senkronu — lig info + sezonlar +
 * ulke.
 *
 * <p>"covered" voleybol ligler icin daily refresh job tarafindan cagrilir.
 * Ayrica lig detay sayfasi acilisinda lazy refresh.
 */
@Service
public class VolleyballLeaguesSyncService {

    private static final Logger log = LoggerFactory.getLogger(VolleyballLeaguesSyncService.class);

    private final VolleyballApiClient apiClient;
    private final VolleyballLeagueRepository leagueRepo;
    private final VolleyballLeagueUpserter upserter;

    public VolleyballLeaguesSyncService(VolleyballApiClient apiClient,
                                        VolleyballLeagueRepository leagueRepo,
                                        VolleyballLeagueUpserter upserter) {
        this.apiClient = apiClient;
        this.leagueRepo = leagueRepo;
        this.upserter = upserter;
    }

    /**
     * Tek lig icin {@code /leagues?id=X} cagrisi + upsert.
     *
     * @return upsert edilmis entity, API bos cevap dondurduyse null
     */
    @Transactional
    public VolleyballLeague syncLeagueInfo(long leagueId) {
        try {
            List<VbLeagueDto> resp = apiClient.fetchLeagueById(leagueId);
            if (resp == null || resp.isEmpty()) {
                log.warn("Voleybol lig info bulunamadi: id={}", leagueId);
                return null;
            }
            VbLeagueDto dto = resp.get(0);
            VolleyballLeague saved = upserter.upsertFromApi(dto);
            log.info("Voleybol lig info tazelendi id={} name={} seasons={}",
                    leagueId,
                    saved != null ? saved.getName() : "?",
                    dto.seasons() != null ? dto.seasons().size() : 0);
            return saved;
        } catch (Exception e) {
            log.warn("Voleybol lig info sync hata id={}: {}", leagueId, e.toString());
            return null;
        }
    }

    /** Covered tum voleybol ligler icin info tazeleme. */
    public int syncAllCovered() {
        List<VolleyballLeague> covered = leagueRepo.findByCoveredTrue();
        if (covered.isEmpty()) {
            log.debug("Voleybol lig info sync: covered lig yok");
            return 0;
        }
        int ok = 0;
        for (VolleyballLeague l : covered) {
            try {
                if (syncLeagueInfo(l.getId()) != null) ok++;
            } catch (Exception e) {
                log.warn("Voleybol lig info sync hata id={}: {}", l.getId(), e.toString());
            }
        }
        log.info("Voleybol lig info sync tamam: {}/{} basarili", ok, covered.size());
        return ok;
    }
}
