package com.scorestv.volleyball;

import com.scorestv.volleyball.domain.VolleyballTeam;
import com.scorestv.volleyball.domain.VolleyballTeamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Voleybol takimi profil senkronu — {@code /teams?id=X} ile ulke, national
 * gibi bilgileri taze tutar.
 *
 * <p>Freshness gate: 7 gunden eski degilse atlanir.
 */
@Service
public class VolleyballTeamProfileSyncService {

    private static final Logger log =
            LoggerFactory.getLogger(VolleyballTeamProfileSyncService.class);

    private static final Duration FRESHNESS = Duration.ofDays(7);

    private final VolleyballApiClient client;
    private final VolleyballTeamRepository teamRepo;
    private final VolleyballTeamUpserter upserter;

    public VolleyballTeamProfileSyncService(VolleyballApiClient client,
                                            VolleyballTeamRepository teamRepo,
                                            VolleyballTeamUpserter upserter) {
        this.client = client;
        this.teamRepo = teamRepo;
        this.upserter = upserter;
    }

    @Transactional
    public Optional<VolleyballTeam> syncProfile(long teamId, boolean force) {
        var existing = teamRepo.findById(teamId).orElse(null);

        if (!force && existing != null && existing.getLastProfileSyncedAt() != null) {
            Instant cutoff = Instant.now().minus(FRESHNESS);
            if (existing.getLastProfileSyncedAt().isAfter(cutoff)) {
                log.debug("Voleybol team profile freshness OK id={} syncedAt={}",
                        teamId, existing.getLastProfileSyncedAt());
                return Optional.of(existing);
            }
        }

        List<VbTeamDto> resp;
        try {
            resp = client.fetchTeamProfile(teamId);
        } catch (Exception e) {
            log.warn("Voleybol team profile fetch hatasi id={}: {}", teamId, e.toString());
            return Optional.ofNullable(existing);
        }

        if (resp.isEmpty()) {
            log.debug("Voleybol team profile bos yanit id={}", teamId);
            return Optional.ofNullable(existing);
        }

        VolleyballTeam saved = upserter.upsertFromProfile(resp.get(0));
        return Optional.ofNullable(saved);
    }

    public Optional<VolleyballTeam> syncProfile(long teamId) {
        return syncProfile(teamId, false);
    }
}
