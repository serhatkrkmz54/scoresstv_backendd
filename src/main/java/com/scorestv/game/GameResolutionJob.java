package com.scorestv.game;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Dönemi biten yarışmaları periyodik olarak çözer. endAt geçmiş + henüz RESOLVED
 * olmayanları alır. @SchedulerLock ile tek node'da koşar. Admin endAt'ı, tüm
 * maçlar + player_stats sync'i bittikten SONRAsına ayarlamalı (ör. pazartesi 06:00).
 */
@Component
public class GameResolutionJob {

    private static final Logger log = LoggerFactory.getLogger(GameResolutionJob.class);

    private final GameCompetitionRepository competitionRepo;
    private final GameResolutionService resolutionService;

    public GameResolutionJob(GameCompetitionRepository competitionRepo,
                             GameResolutionService resolutionService) {
        this.competitionRepo = competitionRepo;
        this.resolutionService = resolutionService;
    }

    @Scheduled(fixedDelayString = "${scorestv.game.resolve-interval-ms:300000}")
    @SchedulerLock(name = "gameResolution", lockAtMostFor = "PT10M")
    public void run() {
        final List<GameCompetition> due = competitionRepo.findByStatusInAndEndAtLessThanEqual(
                List.of(GameStatus.OPEN, GameStatus.LOCKED), Instant.now());
        for (GameCompetition c : due) {
            try {
                resolutionService.resolveCompetition(c.getId());
            } catch (RuntimeException ex) {
                log.error("Oyun yarismasi cozumleme hatasi id={}: {}", c.getId(), ex.getMessage(), ex);
            }
        }
    }
}
