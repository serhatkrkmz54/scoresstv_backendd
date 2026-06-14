package com.scorestv.basketball;

import com.scorestv.basketball.domain.BasketballTeam;
import com.scorestv.basketball.domain.BasketballTeamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Basketbol takimi profil senkronu — {@code /teams?id=X} ile ulke, kurulus,
 * code, venue gibi sabit bilgileri taze tutar.
 *
 * <p>Freshness gate: bir takim profil 7 gunden eski degilse atlanir
 * (cron / lazy sync agresif kalsa bile kotanin gereksiz tuketilmemesi icin).
 */
@Service
public class BasketballTeamProfileSyncService {

    private static final Logger log =
            LoggerFactory.getLogger(BasketballTeamProfileSyncService.class);

    /** Profil senkronu en az bu kadar siklikla tekrarlanabilir. */
    private static final Duration FRESHNESS = Duration.ofDays(7);

    private final BasketballApiClient client;
    private final BasketballTeamRepository teamRepo;
    private final BasketballTeamUpserter upserter;

    public BasketballTeamProfileSyncService(BasketballApiClient client,
                                             BasketballTeamRepository teamRepo,
                                             BasketballTeamUpserter upserter) {
        this.client = client;
        this.teamRepo = teamRepo;
        this.upserter = upserter;
    }

    /**
     * Bir takimin profilini senkronla. Freshness gate aktif:
     * {@code force=false} ise son 7 gun icinde senkronlanmis takim atlanir.
     */
    @Transactional
    public Optional<BasketballTeam> syncProfile(long teamId, boolean force) {
        var existing = teamRepo.findById(teamId).orElse(null);

        if (!force && existing != null && existing.getLastProfileSyncedAt() != null) {
            Instant cutoff = Instant.now().minus(FRESHNESS);
            if (existing.getLastProfileSyncedAt().isAfter(cutoff)) {
                log.debug("Basketbol team profile freshness OK id={} syncedAt={}",
                        teamId, existing.getLastProfileSyncedAt());
                return Optional.of(existing);
            }
        }

        List<BkTeamDto> resp;
        try {
            resp = client.fetchTeamProfile(teamId);
        } catch (Exception e) {
            log.warn("Basketbol team profile fetch hatasi id={}: {}",
                    teamId, e.toString());
            return Optional.ofNullable(existing);
        }

        if (resp.isEmpty()) {
            log.debug("Basketbol team profile bos yanit id={}", teamId);
            return Optional.ofNullable(existing);
        }

        BasketballTeam saved = upserter.upsertFromProfile(resp.get(0));
        return Optional.ofNullable(saved);
    }

    /** {@link #syncProfile(long, boolean)} — varsayilan {@code force=false}. */
    public Optional<BasketballTeam> syncProfile(long teamId) {
        return syncProfile(teamId, false);
    }
}
