package com.scorestv.football.sync;

import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.football.domain.FixtureStatistic;
import com.scorestv.football.domain.FixtureStatisticRepository;
import com.scorestv.football.domain.Team;
import com.scorestv.football.domain.TeamRepository;
import com.scorestv.football.sync.dto.StatisticApiDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Maç istatistiklerini DB'ye <b>replace</b> stratejisiyle yazar:
 * o maçın TÜM stat satırlarını siler, gelen yeni listeyi yazar.
 *
 * <p>Bu strateji eski tip API'dan kaybolduğunda otomatik temizler ve
 * idempotent çalışır. Boş yanıt → "0 yazıldı, eskiler silindi".
 */
@Service
public class FixtureStatisticsUpserter {

    private final FixtureStatisticRepository statisticRepository;
    private final FixtureRepository fixtureRepository;
    private final TeamRepository teamRepository;

    public FixtureStatisticsUpserter(FixtureStatisticRepository statisticRepository,
                                     FixtureRepository fixtureRepository,
                                     TeamRepository teamRepository) {
        this.statisticRepository = statisticRepository;
        this.fixtureRepository = fixtureRepository;
        this.teamRepository = teamRepository;
    }

    @Transactional
    public int replace(Long fixtureId, List<StatisticApiDto> items) {
        Fixture fixtureRef = fixtureRepository.getReferenceById(fixtureId);

        statisticRepository.deleteByFixtureId(fixtureId);

        if (items == null || items.isEmpty()) {
            return 0;
        }
        int written = 0;
        for (StatisticApiDto item : items) {
            if (item == null || item.team() == null || item.team().id() == null) {
                continue;
            }
            Team teamRef = teamRepository.getReferenceById(item.team().id());
            List<StatisticApiDto.StatItem> stats = item.statistics();
            if (stats == null || stats.isEmpty()) {
                continue;
            }
            for (StatisticApiDto.StatItem stat : stats) {
                if (stat == null || stat.type() == null) {
                    continue;
                }
                FixtureStatistic entity = new FixtureStatistic();
                entity.setFixture(fixtureRef);
                entity.setTeam(teamRef);
                entity.setStatType(stat.type());
                // value karışık tip — Objects.toString güvenli (null → null).
                entity.setStatValue(Objects.toString(stat.value(), null));
                statisticRepository.save(entity);
                written++;
            }
        }
        return written;
    }
}
