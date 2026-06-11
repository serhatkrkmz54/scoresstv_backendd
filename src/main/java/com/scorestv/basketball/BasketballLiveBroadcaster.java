package com.scorestv.basketball;

import com.scorestv.basketball.web.BasketballGameView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Canlı basketbol maçlarını STOMP üzerinden mobile'a yayar (football'dan ayrı).
 *
 * <p>Topic: {@code /topic/basketball/live} — o an canlı (in-play) TÜM maçların
 * anlık görüntüsü (snapshot) gönderilir. Mobile id'ye göre mevcut listeye merge
 * eder (skor/durum/çeyrek tazelenir). Merge dil-bağımsızdır (sayı + durum kodu),
 * bu yüzden tek topic yeterli; takım adları ilk REST yükünden gelir.
 *
 * <p>Football'un {@code LiveBroadcaster}'ı ile aynı altyapıyı (SimpMessagingTemplate)
 * kullanır ama tamamen ayrı topic ve servis — karışmaz.
 */
@Component
public class BasketballLiveBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(BasketballLiveBroadcaster.class);
    private static final String LIVE_TOPIC = "/topic/basketball/live";

    private final SimpMessagingTemplate messagingTemplate;
    private final BasketballGameService gameService;

    public BasketballLiveBroadcaster(SimpMessagingTemplate messagingTemplate,
                                     BasketballGameService gameService) {
        this.messagingTemplate = messagingTemplate;
        this.gameService = gameService;
    }

    /**
     * O an canlı maçların snapshot'unu yayar. Canlı maç yoksa boş liste gider
     * (mobile tarafında zararsız — merge edecek bir şey bulmaz).
     */
    public void broadcastLive() {
        try {
            List<BasketballGameView> live = gameService.live(true);
            // Map sargısı: mobil STOMP istemcisi yalnızca JSON-object (dizi değil)
            // mesajları işler. {"games":[...]} olarak gönderilir.
            // payload'ı (Object)'e cast: Map argümanı convertAndSend(dest,payload)
            // ile convertAndSend(payload,headers) overload'ları arasında belirsizlik
            // yaratıyor; cast payload overload'unu kesinleştirir.
            Object payload = Map.of("games", live);
            messagingTemplate.convertAndSend(LIVE_TOPIC, payload);
            log.debug("Basketbol canlı yayın: {} maç", live.size());
        } catch (RuntimeException ex) {
            log.warn("Basketbol canlı yayın başarısız: {}", ex.getMessage());
        }
    }
}
