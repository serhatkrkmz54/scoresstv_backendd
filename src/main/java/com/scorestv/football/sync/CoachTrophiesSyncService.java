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

import java.util.List;
import java.util.Map;

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
        // Replace pattern
        trophyRepository.deleteByCoachId(coachId);
        if (items == null || items.isEmpty()) {
            return 0;
        }
        Coach coachRef = coachRepository.getReferenceById(coachId);
        int written = 0;
        for (TrophyApiDto item : items) {
            if (item == null) continue;
            CoachTrophy t = new CoachTrophy();
            t.setCoach(coachRef);
            t.setLeague(item.league() != null ? item.league() : "Unknown");
            t.setCountry(item.country());
            t.setSeason(item.season());
            t.setPlace(item.place());
            try {
                trophyRepository.save(t);
                written++;
            } catch (RuntimeException ex) {
                log.debug("Trophy duplicate (UNIQUE): coachId={} league={} season={} place={}",
                        coachId, item.league(), item.season(), item.place());
            }
        }
        log.info("Coach trophies sync: coachId={} — {} kupa yazildi", coachId, written);
        return written;
    }
}
