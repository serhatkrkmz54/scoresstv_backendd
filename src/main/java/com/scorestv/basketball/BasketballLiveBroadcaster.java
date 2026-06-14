package com.scorestv.basketball;

import com.scorestv.basketball.web.BasketballGameView;
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
 * Canlı basketbol maçlarını STOMP üzerinden mobile'a yayar (football'dan ayrı).
 *
 * <p>Topic: {@code /topic/basketball/live} — o an canlı (in-play) TÜM maçların
 * anlık görüntüsü (snapshot) gönderilir. Mobile id'ye göre mevcut listeye merge
 * eder (skor/durum/çeyrek tazelenir). Merge dil-bağımsızdır (sayı + durum kodu),
 * bu yüzden tek topic yeterli; takım adları ilk REST yükünden gelir.
 *
 * <p>Football'un {@code LiveBroadcaster}'ı ile aynı altyapıyı (SimpMessagingTemplate)
 * kullanır ama tamamen ayrı topic ve servis — karışmaz.
 *
 * <p><b>"Just-finished" inclusion</b>: Bir oyun bittiğinde (FT) {@code live()}
 * sorgusundan düşer — bu durumda mobile {@code bkLiveMap}'ten oyun silinir ve
 * favoriler tabı son canlı statu'da (örn. Q4) takılı kalırdı. Bunu önlemek için
 * bir önceki tickteki canlı id'leri akılda tutuyoruz: yeni tickte canlı listede
 * OLMAYAN ama bir önceki tickte canlı OLAN oyunları DB'den (FT halini) çekip
 * snapshot'a EKLİYORUZ. Mobile bu son final yayını ile FT'yi görüp UI'ı
 * günceller. Bir sonraki tickte previousLiveIds yalnız mevcut canlılara eşit
 * olduğu için FT oyun ikinci kez gönderilmez (tek seferlik "final" yayını).
 */
@Component
public class BasketballLiveBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(BasketballLiveBroadcaster.class);
    private static final String LIVE_TOPIC = "/topic/basketball/live";

    private final SimpMessagingTemplate messagingTemplate;
    private final BasketballGameService gameService;

    /**
     * Bir onceki broadcastteki canlı (in-play) oyun ID'leri. Thread-safe degil
     * cunku BasketballLiveJob single-threaded sceduler — tek yazar/okuyucu.
     */
    private Set<Long> previousLiveIds = Collections.emptySet();

    public BasketballLiveBroadcaster(SimpMessagingTemplate messagingTemplate,
                                     BasketballGameService gameService) {
        this.messagingTemplate = messagingTemplate;
        this.gameService = gameService;
    }

    /**
     * O an canlı maçların snapshot'unu yayar. Canlı maç yoksa boş liste gider
     * (mobile tarafında zararsız — merge edecek bir şey bulmaz).
     *
     * <p>Ayrıca bir önceki tickte canlıydı ama şimdi canlı olmayan oyunları
     * (yani az önce FT olanları) DB'den çekip snapshot'a tek seferlik dahil
     * eder — mobile bu sayede final statuyu görür.
     */
    public void broadcastLive() {
        try {
            List<BasketballGameView> live = gameService.live(true);

            // Yeni canlı ID set'i
            Set<Long> currentLiveIds = new HashSet<>(live.size());
            for (var g : live) currentLiveIds.add(g.id());

            // Bir onceki tickte canlı olup simdi canli olmayanlar = az önce FT
            // olanlar (veya nadiren CANC/POSTPONE). DB'den çekip dahil et.
            Set<Long> justFinished = new HashSet<>(previousLiveIds);
            justFinished.removeAll(currentLiveIds);

            List<BasketballGameView> payload;
            if (justFinished.isEmpty()) {
                payload = live;
            } else {
                // FT'ye gecmis oyunlari DB'den cek; eger yine canli durumdaysa
                // (theoretik) tekrar gelir ama set duplicate'i map ile elenir.
                List<BasketballGameView> finished = gameService.byIds(justFinished, true);
                // ID bazli birlestir: live + finished, sira korunmaz ama mobile
                // merge'i ID'ye göredir.
                Map<Long, BasketballGameView> merged = new LinkedHashMap<>(
                        live.size() + finished.size());
                for (var g : live) merged.put(g.id(), g);
                for (var g : finished) merged.putIfAbsent(g.id(), g);
                payload = new ArrayList<>(merged.values());
                log.debug("Basketbol final yayini: {} oyun (az once FT)", finished.size());
            }

            // previousLiveIds'i yalnız mevcut canlilarla guncelle —
            // FT olanlar 2.tickte tekrar gonderilmesin (tek seferlik).
            previousLiveIds = currentLiveIds;

            // Map sargısı: mobil STOMP istemcisi yalnızca JSON-object (dizi değil)
            // mesajları işler. {"games":[...]} olarak gönderilir.
            Object body = Map.of("games", payload);
            messagingTemplate.convertAndSend(LIVE_TOPIC, body);
            log.debug("Basketbol canlı yayın: {} maç (live={}, finished={})",
                    payload.size(), live.size(), payload.size() - live.size());
        } catch (RuntimeException ex) {
            log.warn("Basketbol canlı yayın başarısız: {}", ex.getMessage());
        }
    }
}
