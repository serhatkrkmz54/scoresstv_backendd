package com.scorestv.football.live;

import com.scorestv.football.domain.Fixture;
import com.scorestv.football.web.dto.LiveFixturesResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * Değişen canlı maçları STOMP üzerinden iki dilde de yayar.
 *
 * <p>Topic düzeni:
 * <ul>
 *   <li>{@code /topic/fixtures/live/tr} — TÜM canlı güncellemeler (Türkçe)</li>
 *   <li>{@code /topic/fixtures/live/en} — TÜM canlı güncellemeler (İngilizce)</li>
 *   <li>{@code /topic/fixtures/{id}/tr} — tek maçın güncellemeleri (Türkçe)</li>
 *   <li>{@code /topic/fixtures/{id}/en} — tek maçın güncellemeleri (İngilizce)</li>
 * </ul>
 * Frontend hangi dil seçildiyse o topic'e abone olur. Her değişim 4 topic'e
 * gider; broker tarafı multiplex'i halleder.
 */
@Component
public class LiveBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(LiveBroadcaster.class);

    /** Anasayfa banner + canlı sekme için global topic. */
    private static final String GLOBAL_TOPIC = "/topic/fixtures/live/%s";
    /** Maç detay sayfası için maç-bazlı topic. */
    private static final String FIXTURE_TOPIC = "/topic/fixtures/%d/%s";

    private final SimpMessagingTemplate messagingTemplate;
    private final LiveFixtureMapper mapper;

    public LiveBroadcaster(SimpMessagingTemplate messagingTemplate, LiveFixtureMapper mapper) {
        this.messagingTemplate = messagingTemplate;
        this.mapper = mapper;
    }

    /** Tek bir maçın güncellemesini Türkçe ve İngilizce olarak yayar. */
    public void broadcast(Fixture fixture) {
        LiveFixturesResponse.LiveFixture tr = mapper.toLiveFixture(fixture, true);
        LiveFixturesResponse.LiveFixture en = mapper.toLiveFixture(fixture, false);

        messagingTemplate.convertAndSend(String.format(GLOBAL_TOPIC, "tr"), tr);
        messagingTemplate.convertAndSend(
                String.format(FIXTURE_TOPIC, fixture.getId(), "tr"), tr);
        messagingTemplate.convertAndSend(String.format(GLOBAL_TOPIC, "en"), en);
        messagingTemplate.convertAndSend(
                String.format(FIXTURE_TOPIC, fixture.getId(), "en"), en);

        log.debug("Canlı yayın: fixtureId={} skor={}-{} durum={} dk={}",
                fixture.getId(), fixture.getHomeGoals(), fixture.getAwayGoals(),
                fixture.getStatusShort(), fixture.getElapsed());
    }

    /**
     * Birden çok değişen maçı yayar; bir maçın yayını başarısız olursa
     * diğerleri etkilenmez.
     */
    public void broadcastAll(Collection<Fixture> fixtures) {
        for (Fixture fixture : fixtures) {
            try {
                broadcast(fixture);
            } catch (RuntimeException ex) {
                log.warn("Canlı yayın başarısız: fixtureId={} hata={}",
                        fixture.getId(), ex.getMessage());
            }
        }
    }
}
