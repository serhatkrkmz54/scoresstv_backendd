package com.scorestv.basketball;

import com.scorestv.basketball.domain.BasketballLeague;
import com.scorestv.basketball.domain.BasketballLeagueTopPlayer;
import com.scorestv.basketball.domain.BasketballLeagueTopPlayer.Category;
import com.scorestv.basketball.domain.BasketballLeagueTopPlayerRepository;
import com.scorestv.basketball.domain.BasketballPlayer;
import com.scorestv.basketball.domain.BasketballTeam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@link BasketballLeagueTopPlayer} icin replace-strateji upsert.
 *
 * <p>Sezon basina kategori (SCORERS/REBOUNDERS/ASSISTS) listesi tek
 * atomik islemde silinir + insert edilir. Yani sezon ortasinda sirada
 * yer degisirse "kismi update" yerine "full refresh" yapilir; bu sirada
 * tutarsizlik olmaz.
 *
 * <p>Cagrildigi yer: {@code BasketballTopPlayersSyncService} — bir lig +
 * sezondaki tum oyuncularin sezon istatistiklerini cekip top 10'u her
 * kategori icin sirayla yazar.
 */
@Component
public class BasketballLeagueTopPlayerUpserter {

    private static final Logger log = LoggerFactory.getLogger(BasketballLeagueTopPlayerUpserter.class);

    private final BasketballLeagueTopPlayerRepository repo;

    public BasketballLeagueTopPlayerUpserter(BasketballLeagueTopPlayerRepository repo) {
        this.repo = repo;
    }

    /**
     * Bir kategori icin sirali listeyi replace eder.
     *
     * <p>Adimlar:
     * <ol>
     *   <li>{@code (league, season, category)} icin mevcut satirlari sil.
     *   <li>Yeni sirali listeyi 1..N pozisyon ile yaz (girdideki sira korunur).
     * </ol>
     *
     * @param league   lig (FK)
     * @param season   sezon string (orn. "2024-2025")
     * @param category kategori
     * @param entries  sirali liste — 1. pozisyon en yuksek deger
     * @return yazilan satir sayisi
     */
    @Transactional
    public int replaceCategory(BasketballLeague league,
                               String season,
                               Category category,
                               List<Entry> entries) {
        if (league == null || season == null || category == null) {
            log.warn("TopPlayer replaceCategory: null arg league={} season={} category={}",
                    league, season, category);
            return 0;
        }
        // Once mevcut listeyi temizle (atomik refresh)
        int deleted = repo.deleteByLeagueSeasonCategory(league.getId(), season, category);
        if (deleted > 0) {
            log.debug("TopPlayer eski liste silindi league={} season={} category={} silinen={}",
                    league.getId(), season, category, deleted);
        }

        if (entries == null || entries.isEmpty()) {
            log.debug("TopPlayer replaceCategory: bos entry — sadece sildi");
            return 0;
        }

        int inserted = 0;
        int position = 1;
        for (Entry e : entries) {
            if (e == null || e.player() == null) continue;
            BasketballLeagueTopPlayer row = new BasketballLeagueTopPlayer();
            row.setLeague(league);
            row.setSeason(season);
            row.setCategory(category);
            row.setPosition(position++);
            row.setPlayer(e.player());
            row.setTeam(e.team());
            row.setValue(e.value());
            row.setGamesPlayed(e.gamesPlayed());
            repo.save(row);
            inserted++;
        }
        log.info("TopPlayer yazildi league={} season={} category={} count={}",
                league.getId(), season, category, inserted);
        return inserted;
    }

    /**
     * Tek satir veri tasiyici — sync servisi PlayerSeasonStat'lardan donusturup
     * verir, sira pozisyonu burada degil upserter tarafindan atanir.
     */
    public record Entry(
            BasketballPlayer player,
            BasketballTeam team,
            BigDecimal value,
            Integer gamesPlayed
    ) {}
}
