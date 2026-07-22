package com.scorestv.football.live;

import com.scorestv.football.FootballMessages;
import com.scorestv.football.domain.FixtureEvent;
import com.scorestv.football.web.dto.EventSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Yeni maç olaylarını ({@link FixtureEvent}) WebSocket üzerinden yayar.
 *
 * <p>Topic düzeni LiveBroadcaster ile aynı: dil-bazlı ayrı topic'ler.
 * <ul>
 *   <li>{@code /topic/fixtures/{id}/events/tr} — Türkçe (typeText/detailText TR)</li>
 *   <li>{@code /topic/fixtures/{id}/events/en} — İngilizce (typeText/detailText EN)</li>
 * </ul>
 * Frontend hangi dil seçildiyse o topic'e abone olur.
 */
@Component
public class EventBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(EventBroadcaster.class);

    private static final String EVENTS_TOPIC = "/topic/fixtures/%d/events/%s";

    private final SimpMessagingTemplate messagingTemplate;
    private final FootballMessages messages;

    public EventBroadcaster(SimpMessagingTemplate messagingTemplate,
                            FootballMessages messages) {
        this.messagingTemplate = messagingTemplate;
        this.messages = messages;
    }

    /** Tek bir yeni olayı maç-bazlı event topic'ine TR ve EN olarak gönderir. */
    public void broadcast(Long fixtureId, FixtureEvent event) {
        EventSummary tr = toSummary(event, true);
        EventSummary en = toSummary(event, false);
        messagingTemplate.convertAndSend(String.format(EVENTS_TOPIC, fixtureId, "tr"), tr);
        messagingTemplate.convertAndSend(String.format(EVENTS_TOPIC, fixtureId, "en"), en);
        log.debug("Olay yayını: fixtureId={} dk={} tip={} detay={} oyuncu={}",
                fixtureId, event.getTimeElapsed(), event.getType(),
                event.getDetail(), event.getPlayerName());
    }

    private EventSummary toSummary(FixtureEvent e, boolean turkish) {
        Long teamId = e.getTeam() != null ? e.getTeam().getId() : null;
        return new EventSummary(
                e.getTimeElapsed(), e.getTimeExtra(),
                teamId,
                e.getType(), messages.eventType(e.getType(), turkish),
                e.getDetail(), messages.eventDetail(e.getDetail(), turkish),
                messages.eventComment(e.getComments(), turkish),
                e.getPlayerId(), e.getPlayerName(),
                e.getAssistId(), e.getAssistName());
    }
}
