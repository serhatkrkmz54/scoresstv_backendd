package com.scorestv.football.sync;

import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureEvent;
import com.scorestv.football.domain.FixtureEventRepository;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.football.domain.Team;
import com.scorestv.football.domain.TeamRepository;
import com.scorestv.football.sync.dto.EventApiDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Bir maçın olaylarını DB'ye <b>replace</b> stratejisiyle yazar:
 * önce o maçın TÜM olaylarını siler, sonra gelenleri tam set olarak ekler.
 *
 * <p>Bu strateji API-Football'un VAR iptali gibi geri çekme akışlarını da
 * doğal olarak yansıtır: API'den dönen liste neyse DB'deki liste o olur.
 * İdempotent — aynı tickte birden fazla çağrı aynı sonucu üretir.
 *
 * <p>Çağrıyı yapan akış {@code @Transactional} dışında durabilir; bu metot
 * kendi transaction'ını açar.
 */
@Service
public class FixtureEventsUpserter {

    private final FixtureEventRepository eventRepository;
    private final FixtureRepository fixtureRepository;
    private final TeamRepository teamRepository;
    private final PlayerUpserter playerUpserter;

    public FixtureEventsUpserter(FixtureEventRepository eventRepository,
                                 FixtureRepository fixtureRepository,
                                 TeamRepository teamRepository,
                                 PlayerUpserter playerUpserter) {
        this.eventRepository = eventRepository;
        this.fixtureRepository = fixtureRepository;
        this.teamRepository = teamRepository;
        this.playerUpserter = playerUpserter;
    }

    /**
     * O maçın tüm olaylarını siler, gelenleri tam set olarak yazar.
     *
     * @return DB'ye yazılan olay sayısı
     */
    @Transactional
    public int replace(Long fixtureId, List<EventApiDto> items) {
        // Fixture'ın varlığını proxy ile garantile (yoksa ConstraintViolation atar).
        Fixture fixtureRef = fixtureRepository.getReferenceById(fixtureId);

        // Önce eski olayları sil — derived delete, transaction içinde flush eder.
        eventRepository.deleteByFixtureId(fixtureId);

        if (items == null || items.isEmpty()) {
            return 0;
        }

        int written = 0;
        for (EventApiDto item : items) {
            // type NOT NULL kolonu: API tipi bos gonderirse insert 23502 verir.
            if (item == null || item.type() == null
                    || item.time() == null || item.time().elapsed() == null) {
                continue;
            }
            FixtureEvent event = new FixtureEvent();
            event.setFixture(fixtureRef);
            event.setTimeElapsed(item.time().elapsed());
            event.setTimeExtra(item.time().extra());
            event.setType(item.type());
            event.setDetail(item.detail());
            event.setComments(item.comments());
            if (item.team() != null && item.team().id() != null) {
                Team teamRef = teamRepository.getReferenceById(item.team().id());
                event.setTeam(teamRef);
            }
            if (item.player() != null) {
                event.setPlayerId(item.player().id());
                event.setPlayerName(item.player().name());
                // Player master (eventlerden de foto gelmez — ad upsert)
                playerUpserter.upsert(item.player().id(), item.player().name(), null);
            }
            if (item.assist() != null) {
                event.setAssistId(item.assist().id());
                event.setAssistName(item.assist().name());
                playerUpserter.upsert(item.assist().id(), item.assist().name(), null);
            }
            eventRepository.save(event);
            written++;
        }
        return written;
    }
}
