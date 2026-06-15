package com.scorestv.football.sync;

import com.scorestv.football.domain.League;
import com.scorestv.football.domain.LeagueRepository;
import com.scorestv.football.domain.LeagueTopPlayer;
import com.scorestv.football.domain.LeagueTopPlayer.Category;
import com.scorestv.football.domain.LeagueTopPlayerRepository;
import com.scorestv.football.sync.dto.TopPlayerApiDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Top players verisini (scorers/assists/cards) DB'ye <b>REPLACE</b> stratejisi
 * ile yazar: o lig+sezon+kategorinin tum satirlarini siler, gelenleri tam set
 * olarak yazar. Replace; cunku siralama API'nin verdigine baglidir ve "eksik
 * oyuncu olmasi" yerine "guncel listenin tamami" istiyoruz.
 *
 * <p>Kategoriye gore birincil deger secimi:
 * <ul>
 *   <li>SCORERS  → {@code goals.total}</li>
 *   <li>ASSISTS  → {@code goals.assists}</li>
 *   <li>CARDS    → {@code cards.yellow} (kirmizi {@code valueSecondary})</li>
 * </ul>
 */
@Service
public class TopPlayersUpserter {

    private final LeagueTopPlayerRepository repository;
    private final LeagueRepository leagueRepository;
    private final PlayerUpserter playerUpserter;

    public TopPlayersUpserter(LeagueTopPlayerRepository repository,
                              LeagueRepository leagueRepository,
                              PlayerUpserter playerUpserter) {
        this.repository = repository;
        this.leagueRepository = leagueRepository;
        this.playerUpserter = playerUpserter;
    }

    @Transactional
    public int replace(Long leagueId, Integer season, Category category,
                       List<TopPlayerApiDto> items) {
        // 1) Eski satirlari sil (immediate DELETE).
        repository.deleteByLeagueSeasonCategory(leagueId, season, category);
        // Flush UNIQUE-constraint yarisini onlemek icin (digerlerinde
        // uyguladigimiz desen) JPQL DELETE zaten hemen SQL gonderir.
        if (items == null || items.isEmpty()) {
            return 0;
        }
        League leagueRef = leagueRepository.getReferenceById(leagueId);
        int rank = 1;
        int written = 0;
        for (TopPlayerApiDto item : items) {
            // player_name NOT NULL kolonu: isim bos gelirse insert 23502 verip
            // @Transactional replace tx'ini kirletir, kalan satirlar 25P02 duser.
            if (item == null || item.player() == null || item.player().id() == null
                    || item.player().name() == null || item.player().name().isBlank()) {
                continue;
            }
            LeagueTopPlayer row = toEntity(leagueRef, season, category, rank, item);
            if (row == null) {
                continue;
            }
            repository.save(row);
            // Player master upsert (foto MinIO'ya aynalanir)
            playerUpserter.upsert(
                    item.player().id(), item.player().name(), item.player().photo());
            written++;
            rank++;
        }
        return written;
    }

    private static LeagueTopPlayer toEntity(League leagueRef, Integer season,
                                            Category category, int rank,
                                            TopPlayerApiDto item) {
        TopPlayerApiDto.Player p = item.player();
        TopPlayerApiDto.Statistics stat = firstStatistics(item.statistics());

        Integer primary = null;
        Integer secondary = null;
        if (stat != null) {
            TopPlayerApiDto.Goals goals = stat.goals();
            TopPlayerApiDto.Cards cards = stat.cards();
            switch (category) {
                case SCORERS -> primary = goals != null ? goals.total() : null;
                case ASSISTS -> primary = goals != null ? goals.assists() : null;
                case YELLOW_CARDS -> {
                    primary = cards != null ? cards.yellow() : null;
                    // Cift sariden kirmizi (yellowred) ikincil bilgi olarak.
                    secondary = cards != null ? cards.yellowred() : null;
                }
                case RED_CARDS -> primary = cards != null ? cards.red() : null;
            }
        }

        LeagueTopPlayer row = new LeagueTopPlayer();
        row.setLeague(leagueRef);
        row.setSeason(season);
        row.setCategory(category);
        row.setRank(rank);
        row.setPlayerId(p.id());
        row.setPlayerName(p.name());
        row.setPlayerPhoto(p.photo());
        row.setPlayerNationality(p.nationality());
        row.setPlayerAge(p.age());
        if (stat != null && stat.team() != null) {
            row.setTeamId(stat.team().id());
            row.setTeamName(stat.team().name());
            row.setTeamLogo(stat.team().logo());
        }
        if (stat != null && stat.games() != null) {
            row.setAppearances(stat.games().appearences());
            row.setMinutes(stat.games().minutes());
        }
        row.setValuePrimary(primary);
        row.setValueSecondary(secondary);
        return row;
    }

    /** Oyuncunun istatistik dizisinden ilk dolu satiri secer. */
    private static TopPlayerApiDto.Statistics firstStatistics(
            List<TopPlayerApiDto.Statistics> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }
}
