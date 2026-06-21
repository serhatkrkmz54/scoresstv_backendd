package com.scorestv.football.sync;

import com.scorestv.football.ApiFootballClient;
import com.scorestv.football.ApiFootballResponse;
import com.scorestv.football.domain.Coach;
import com.scorestv.football.domain.CoachRepository;
import com.scorestv.football.domain.CoachTrophy;
import com.scorestv.football.domain.CoachTrophyRepository;
import com.scorestv.football.sync.dto.TrophyApiDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bir kocun kupalarini API'den ceker + REPLACE pattern ile DB'ye yazar.
 *   {@code GET /trophies?coach=X}
 */
@Service
public class CoachTrophiesSyncService {

    private static final Logger log = LoggerFactory.getLogger(CoachTrophiesSyncService.class);

    private static final ParameterizedTypeReference<ApiFootballResponse<List<TrophyApiDto>>>
            TROPHIES_TYPE = new ParameterizedTypeReference<>() {
            };

    private final ApiFootballClient client;
    private final CoachRepository coachRepository;
    private final CoachTrophyRepository trophyRepository;

    public CoachTrophiesSyncService(ApiFootballClient client,
                                    CoachRepository coachRepository,
                                    CoachTrophyRepository trophyRepository) {
        this.client = client;
        this.coachRepository = coachRepository;
        this.trophyRepository = trophyRepository;
    }

    @Transactional
    public int sync(Long coachId) {
        ApiFootballResponse<List<TrophyApiDto>> response = client.get(
                "/trophies", Map.of("coach", coachId), TROPHIES_TYPE);
        List<TrophyApiDto> items = response.response();
        // Veri-kaybi korumasi: API bos dondurduyse mevcut kupalari SILME.
        if (items == null || items.isEmpty()) {
            return 0;
        }
        trophyRepository.deleteByCoachId(coachId);
        Coach coachRef = coachRepository.getReferenceById(coachId);
        int written = 0;
        Set<String> seen = new HashSet<>();
        for (TrophyApiDto item : items) {
            if (item == null) continue;
            String league = item.league() != null ? item.league() : "Unknown";
            // uq_coach_trophies_unique (coach_id, league, season, place): API
            // ayni kupayi tekrar dondurebiliyor. Dup'i parti icinde ele — yoksa
            // ikinci insert 23505 verip tx'i kirletir, kalan satirlar 25P02 duser.
            if (!seen.add(league + "|" + item.season() + "|" + item.place())) {
                continue;
            }
            CoachTrophy t = new CoachTrophy();
            t.setCoach(coachRef);
            t.setLeague(league);
            t.setCountry(item.country());
            t.setSeason(item.season());
            t.setPlace(item.place());
            trophyRepository.save(t);
            written++;
        }
        log.info("Coach trophies sync: coachId={} — {} kupa yazildi", coachId, written);
        return written;
    }
}
