package com.scorestv.volleyball;

import com.scorestv.volleyball.web.VolleyballGameView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Canli voleybol maclarini STOMP uzerinden mobile'a yayar.
 *
 * <p>Topic: {@code /topic/volleyball/live} — o an canli (in-play) TUM maclarin
 * anlik goruntusu (snapshot). Mobile id'ye gore mevcut listeye merge eder.
 *
 * <p>"Just-finished" inclusion: bir oyun bittiginde live sorgusundan duser; bir
 * onceki tickteki canli id'leri akilda tutup, dusen oyunlarin FT halini DB'den
 * cekip snapshot'a tek seferlik ekliyoruz.
 */
@Component
public class VolleyballLiveBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(VolleyballLiveBroadcaster.class);
    private static final String LIVE_TOPIC = "/topic/volleyball/live";

    private final SimpMessagingTemplate messagingTemplate;
    private final VolleyballGameService gameService;

    private Set<Long> previousLiveIds = Collections.emptySet();

    public VolleyballLiveBroadcaster(SimpMessagingTemplate messagingTemplate,
                                     VolleyballGameService gameService) {
        this.messagingTemplate = messagingTemplate;
        this.gameService = gameService;
    }

    public void broadcastLive() {
        try {
            List<VolleyballGameView> live = gameService.live(true);

            Set<Long> currentLiveIds = new HashSet<>(live.size());
            for (var g : live) currentLiveIds.add(g.id());

            Set<Long> justFinished = new HashSet<>(previousLiveIds);
            justFinished.removeAll(currentLiveIds);

            List<VolleyballGameView> payload;
            if (justFinished.isEmpty()) {
                payload = live;
            } else {
                List<VolleyballGameView> finished = gameService.byIds(justFinished, true);
                Map<Long, VolleyballGameView> merged = new LinkedHashMap<>(
                        live.size() + finished.size());
                for (var g : live) merged.put(g.id(), g);
                for (var g : finished) merged.putIfAbsent(g.id(), g);
                payload = new ArrayList<>(merged.values());
                log.debug("Voleybol final yayini: {} oyun (az once FT)", finished.size());
            }

            previousLiveIds = currentLiveIds;

            Object body = Map.of("games", payload);
            messagingTemplate.convertAndSend(LIVE_TOPIC, body);
            log.debug("Voleybol canli yayin: {} mac (live={})", payload.size(), live.size());
        } catch (RuntimeException ex) {
            log.warn("Voleybol canli yayin basarisiz: {}", ex.getMessage());
        }
    }
}
