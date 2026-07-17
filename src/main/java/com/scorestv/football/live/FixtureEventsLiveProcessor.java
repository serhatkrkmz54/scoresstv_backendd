package com.scorestv.football.live;

import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureEvent;
import com.scorestv.football.domain.FixtureEventRepository;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.football.sync.FixtureEventsSyncService;
import com.scorestv.football.sync.dto.EventApiDto;
import com.scorestv.mobile.notify.NotificationDispatcherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Tek bir maçın olaylarını API'den sync'leyip <b>yeni</b> olanları
 * {@link EventBroadcaster} ile yayar. Hem periyodik {@link LiveEventsJob} hem
 * de skor değişimi tetikleyen LiveTickerService bunu çağırır — diff ve yayın
 * mantığı tek yerde.
 *
 * <p>İdempotent: aynı anda iki çağrı gelse bile aynı olay iki kez yayınlanmaz;
 * imza-tabanlı diff (DB id'sinden bağımsız) tekrarları yutar.
 */
@Component
public class FixtureEventsLiveProcessor {

    private static final Logger log = LoggerFactory.getLogger(FixtureEventsLiveProcessor.class);

    private final FixtureEventRepository eventRepository;
    private final FixtureEventsSyncService eventsSyncService;
    private final EventBroadcaster broadcaster;
    private final FixtureRepository fixtureRepository;
    private final NotificationDispatcherService notificationDispatcher;
    private final com.scorestv.football.detail.FixtureDetailCacheEvictor cacheEvictor;

    public FixtureEventsLiveProcessor(FixtureEventRepository eventRepository,
                                      FixtureEventsSyncService eventsSyncService,
                                      EventBroadcaster broadcaster,
                                      FixtureRepository fixtureRepository,
                                      NotificationDispatcherService notificationDispatcher,
                                      com.scorestv.football.detail.FixtureDetailCacheEvictor cacheEvictor) {
        this.eventRepository = eventRepository;
        this.eventsSyncService = eventsSyncService;
        this.broadcaster = broadcaster;
        this.fixtureRepository = fixtureRepository;
        this.notificationDispatcher = notificationDispatcher;
        this.cacheEvictor = cacheEvictor;
    }

    /**
     * Sync'le ve yeni gelen olayları kronolojik sırada yayar.
     *
     * @return yayınlanan yeni olay sayısı
     */
    public int syncAndBroadcast(Long fixtureId) {
        Set<EventSignature> before = signaturesOf(
                eventRepository.findByFixtureIdOrderByTimeElapsedAsc(fixtureId));
        eventsSyncService.sync(fixtureId);
        return diffBroadcastNotify(fixtureId, before);
    }

    /**
     * Bundle'dan ({@code /fixtures?ids=}) gelen ÖNCEDEN ÇEKİLMİŞ olay listesiyle
     * upsert + yayın — API çağrısı YAPMAZ. Canlı detay batch'i kullanır. Liste
     * boşsa hiçbir şey yapmaz (mevcut olayları SİLMEZ — coverage/transient boş
     * yanıtta veri kaybını önler).
     */
    public int syncAndBroadcast(Long fixtureId, List<EventApiDto> preFetched) {
        if (preFetched == null || preFetched.isEmpty()) {
            return 0;
        }
        Set<EventSignature> before = signaturesOf(
                eventRepository.findByFixtureIdOrderByTimeElapsedAsc(fixtureId));
        eventsSyncService.sync(fixtureId, preFetched);
        return diffBroadcastNotify(fixtureId, before);
    }

    /** Sync sonrası DB'yi okuyup YENİ olayları diff'ler, yayar ve FCM tetikler. */
    private int diffBroadcastNotify(Long fixtureId, Set<EventSignature> before) {
        List<FixtureEvent> after =
                eventRepository.findByFixtureIdOrderByTimeElapsedAsc(fixtureId);

        List<FixtureEvent> newEvents = new ArrayList<>();
        for (FixtureEvent ev : after) {
            if (!before.contains(EventSignature.of(ev))) {
                newEvents.add(ev);
            }
        }
        if (newEvents.isEmpty()) {
            return 0;
        }
        // Kronolojik: frontend timeline'a doğru sırada düşer.
        newEvents.sort(Comparator
                .comparingInt(FixtureEvent::getTimeElapsed)
                .thenComparingInt(e -> e.getTimeExtra() == null ? 0 : e.getTimeExtra()));
        for (FixtureEvent ev : newEvents) {
            broadcaster.broadcast(fixtureId, ev);
        }

        // Yeni olay(lar) geldi → detay Redis cache'ini evict et. Olaylar zaten
        // events topic'inde TAM push'lu; bu evict, cache coherency içindir:
        // maçı O ANDA (WS öncesi) açan ya da refetch eden kullanıcı yeni olayı
        // bayat cache yerine taze görsün (kart/değişiklik gibi skorsuz olaylarda
        // LiveTicker evict'i tetiklenmez — burada garanti altına alınır).
        cacheEvictor.evictAll(fixtureId);

        // Push notification dispatch — her yeni event icin FCM gonderim
        // tetiklenir (gol/kart/penalti). Async oldugu icin live ticker'i bekletmez.
        // Fixture'i bir kez yukle, tum eventlerde reuse et.
        if (!newEvents.isEmpty()) {
            Fixture fixture = fixtureRepository.findById(fixtureId).orElse(null);
            if (fixture != null) {
                for (FixtureEvent ev : newEvents) {
                    try {
                        notificationDispatcher.dispatchEvent(fixture, ev);
                    } catch (RuntimeException ex) {
                        log.warn("FCM event dispatch hata fixtureId={} evId={}: {}",
                                fixtureId, ev.getId(), ex.getMessage());
                    }
                }
            }
        }

        log.debug("Events sync + yayın: fixtureId={} yeni={}", fixtureId, newEvents.size());
        return newEvents.size();
    }

    private static Set<EventSignature> signaturesOf(List<FixtureEvent> events) {
        Set<EventSignature> set = new HashSet<>();
        for (FixtureEvent e : events) {
            set.add(EventSignature.of(e));
        }
        return set;
    }

    /**
     * Bir olayın içerik imzası — DB id'sinden bağımsız. VAR iptali sonrası
     * tekrar eklenen olay yeni id alsa bile imzası aynı kalır → tekrar
     * yayınlanmaz.
     */
    private record EventSignature(
            int elapsed,
            Integer extra,
            Long teamId,
            String type,
            String detail,
            Long playerId
    ) {
        static EventSignature of(FixtureEvent e) {
            return new EventSignature(
                    e.getTimeElapsed() == null ? 0 : e.getTimeElapsed(),
                    e.getTimeExtra(),
                    e.getTeam() != null ? e.getTeam().getId() : null,
                    e.getType(),
                    e.getDetail(),
                    e.getPlayerId());
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof EventSignature s)) return false;
            return elapsed == s.elapsed
                    && Objects.equals(extra, s.extra)
                    && Objects.equals(teamId, s.teamId)
                    && Objects.equals(type, s.type)
                    && Objects.equals(detail, s.detail)
                    && Objects.equals(playerId, s.playerId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(elapsed, extra, teamId, type, detail, playerId);
        }
    }
}
