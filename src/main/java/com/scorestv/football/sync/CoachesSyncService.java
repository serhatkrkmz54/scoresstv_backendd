package com.scorestv.football.sync;

import com.scorestv.football.ApiFootballClient;
import com.scorestv.football.ApiFootballResponse;
import com.scorestv.football.domain.Coach;
import com.scorestv.football.domain.CoachRepository;
import com.scorestv.football.domain.FixtureLineupRepository;
import com.scorestv.football.domain.Team;
import com.scorestv.football.domain.TeamRepository;
import com.scorestv.football.sync.dto.CoachApiDto;
import com.scorestv.search.events.EntityIndexedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * Tekik direktor senkron servisi. API:
 *   {@code GET /coachs?team=X}    (takima bagli TUM coach'lar — bas, asistan, altyapi, gecmis interim)
 *   {@code GET /coachs?id=X}      (tek koc detayi — kariyer dahil)
 *
 * <p><b>Cogul coach problemi:</b> {@code /coachs?team=X} ayni takim id'sine
 * bagli birden cok coach doner. Ornek (Fenerbahce 611):
 * <ul>
 *   <li>D. Tedesco — career[0]: team 611, 2025-07-01 → null  (gercek bas antrenor)</li>
 *   <li>Z. Gole — career[0]: team 611, 2021-12-01 → null  (altyapi/asistan, hala bagli)</li>
 *   <li>Ş. Çorlu — 2021-04-01 → 2021-05-01  (gecmis caretaker)</li>
 *   <li>T. Karapinar — 2020-06-01 → 2020-07-01  (gecmis caretaker)</li>
 * </ul>
 *
 * <p><b>Picker kurali:</b> "team.id == sorgulanan AND career[*]'inde bu takim
 * icin end IS NULL VAR + en yeni start_date" → bas antrenor. Yukaridaki ornekte:
 * Hem Tedesco hem Gole end=null tasiyor, ama Tedesco daha yeni (2025 > 2021)
 * → Tedesco kazanir.
 *
 * <p>Sync akisi:
 * <ol>
 *   <li>API'den TUM coach'lari upsert et (master + career)</li>
 *   <li>Takimda eski "current" isaretlerini temizle ({@code clearCurrentTeam})</li>
 *   <li>Picker ile bas antrenoru sec, yalniz ONUN icin {@code current_team_id = teamId}</li>
 * </ol>
 */
@Service
public class CoachesSyncService {

    private static final Logger log = LoggerFactory.getLogger(CoachesSyncService.class);

    private static final ParameterizedTypeReference<ApiFootballResponse<List<CoachApiDto>>>
            COACH_TYPE = new ParameterizedTypeReference<>() {
            };

    private final ApiFootballClient client;
    private final CoachUpserter upserter;
    private final CoachRepository coachRepository;
    private final FixtureLineupRepository lineupRepository;
    private final TeamRepository teamRepository;
    private final ApplicationEventPublisher events;

    public CoachesSyncService(ApiFootballClient client,
                              CoachUpserter upserter,
                              CoachRepository coachRepository,
                              FixtureLineupRepository lineupRepository,
                              TeamRepository teamRepository,
                              ApplicationEventPublisher events) {
        this.client = client;
        this.upserter = upserter;
        this.coachRepository = coachRepository;
        this.lineupRepository = lineupRepository;
        this.teamRepository = teamRepository;
        this.events = events;
    }

    /**
     * Bir takimin TUM coach'larini API'den ceker, master upsert eder ve
     * dogru bas antrenoru {@code current_team_id} ile isaretler.
     *
     * @return secilen bas antrenorun id'si; coach bulunamazsa null
     */
    @Transactional
    public Long syncByTeam(Long teamId) {
        ApiFootballResponse<List<CoachApiDto>> response = client.get(
                "/coachs", Map.of("team", teamId), COACH_TYPE);
        List<CoachApiDto> items = response.response();
        if (items == null || items.isEmpty()) {
            log.info("Coach sync: bos yanit teamId={}", teamId);
            // Yine de var olan isaretleri temizle — takim coach'siz kalmis olabilir.
            coachRepository.clearCurrentTeam(teamId);
            return null;
        }

        // 1) Tum coach'lari upsert et — master + career REPLACE.
        for (CoachApiDto dto : items) {
            try {
                upserter.upsert(dto);
            } catch (RuntimeException ex) {
                log.warn("Coach upsert hatasi: coachId={} — {}",
                        dto.id(), ex.getMessage());
            }
        }

        // 2) Picker — "end=null + en yeni start" kurali ile bas antrenor.
        CoachApiDto headCoach = pickHeadCoach(items, teamId);

        // 3) Eski "current" isaretlerini temizle.
        coachRepository.clearCurrentTeam(teamId);

        if (headCoach == null) {
            log.warn("Coach sync: teamId={} icin bas antrenor secimi yapilamadi "
                    + "({} coach donulmus ama hicbiri end=null kriterini saglamiyor)",
                    teamId, items.size());
            return null;
        }

        // 4) Secilen coach'a current_team_id = teamId yaz.
        Coach selected = coachRepository.findById(headCoach.id()).orElse(null);
        if (selected != null) {
            selected.setCurrentTeamId(teamId);
            coachRepository.save(selected);
            // currentTeamId artik set — ES koç dokumanini tazele ki aramada
            // "mevcut takim" + "Takima git" calissin (commit sonrasi @Async).
            events.publishEvent(new EntityIndexedEvent.CoachIndexed(selected));
        }
        log.info("Coach sync: teamId={} — bas antrenor coachId={} ({})",
                teamId, headCoach.id(), headCoach.name());
        return headCoach.id();
    }

    /**
     * Donen coach listesinden bas antrenoru secer. UC katmanli karar:
     *
     * <p><b>1. ADMIN OVERRIDE:</b> {@code team.head_coach_override_id} set
     * ediliyse direkt onu kullan. Lineup/rule kontrolu calismaz. Off-season
     * gibi otomatik sinyallerin gucsuz oldugu durumlar icin admin guvenlik agi.
     *
     * <p><b>2. LINEUP:</b> Bizdeki en son sync edilmis macin lineup'inda
     * bench'te oturan kocun id'si. Sezon ici icin en guvenilir sinyal.
     *
     * <p><b>3. KURAL FALLBACK:</b> "team.id == queriedTeamId AND career[*]'inde
     * bu takim icin end=null entry VAR + en yeni start_date" kuralina dus.
     *
     * <p>Hicbiri saglamiyorsa null doner.
     */
    private CoachApiDto pickHeadCoach(List<CoachApiDto> items, Long teamId) {
        // 1) ADMIN OVERRIDE — varsa daima kazanir
        Team team = teamRepository.findById(teamId).orElse(null);
        if (team != null && team.getHeadCoachOverrideId() != null) {
            Long overrideId = team.getHeadCoachOverrideId();
            for (CoachApiDto dto : items) {
                if (dto != null && overrideId.equals(dto.id())) {
                    log.info("Coach picker (admin override): teamId={} → coachId={} ({})",
                            teamId, dto.id(), dto.name());
                    return dto;
                }
            }
            log.warn("Coach picker: admin override coachId={} ama /coachs?team={} listede yok, "
                    + "otomatik picker'a donuyor", overrideId, teamId);
        }

        // 2) Lineup-based: son maclardan birinin coach'i
        Long lineupCoachId = mostRecentLineupCoachId(teamId);
        if (lineupCoachId != null) {
            for (CoachApiDto dto : items) {
                if (dto != null && lineupCoachId.equals(dto.id())) {
                    log.info("Coach picker (lineup): teamId={} → coachId={} ({})",
                            teamId, dto.id(), dto.name());
                    return dto;
                }
            }
            // Lineup gosterdigi coach API yanitinda yok — log + kural'a dus
            log.warn("Coach picker: lineup'ta coachId={} ama /coachs?team={} listede yok, "
                    + "kural fallback'ine donuyor", lineupCoachId, teamId);
        }

        // 2) Fallback: "end=null + en yeni start" kurali
        CoachApiDto best = null;
        LocalDate bestStart = null;
        for (CoachApiDto dto : items) {
            if (dto == null || dto.id() == null) continue;
            if (dto.team() == null || !teamId.equals(dto.team().id())) continue;
            if (dto.career() == null) continue;
            for (CoachApiDto.CareerEntry entry : dto.career()) {
                if (entry == null || entry.team() == null) continue;
                if (!teamId.equals(entry.team().id())) continue;
                if (entry.end() != null && !entry.end().isBlank()) continue;  // ended
                LocalDate start = parseDate(entry.start());
                if (start == null) continue;
                if (bestStart == null || start.isAfter(bestStart)) {
                    bestStart = start;
                    best = dto;
                }
            }
        }
        if (best != null) {
            log.info("Coach picker (kural fallback): teamId={} → coachId={} ({}), start={}",
                    teamId, best.id(), best.name(), bestStart);
        }
        return best;
    }

    /** Lineup tablosundan bir takimin son macindaki bench coach id'si. */
    private Long mostRecentLineupCoachId(Long teamId) {
        List<Long> ids = lineupRepository.findMostRecentCoachIds(
                teamId, org.springframework.data.domain.PageRequest.of(0, 1));
        return ids.isEmpty() ? null : ids.get(0);
    }

    /** Tek bir kocu id ile cek (detay sayfasi icin). */
    public void syncOne(Long coachId) {
        ApiFootballResponse<List<CoachApiDto>> response = client.get(
                "/coachs", Map.of("id", coachId), COACH_TYPE);
        List<CoachApiDto> items = response.response();
        if (items == null || items.isEmpty()) {
            return;
        }
        upserter.upsert(items.get(0));
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }
}
