package com.scorestv.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Canlı maç güncellemeleri için STOMP-over-WebSocket altyapısı.
 *
 * <p>İstemciler {@code /ws/fixtures} adresine bağlanır ve topic'lere abone olur:
 * <ul>
 *   <li>{@code /topic/fixtures/live/{lang}} — TÜM canlı maç güncellemeleri
 *       (anasayfa banner + canlı sekme için)</li>
 *   <li>{@code /topic/fixtures/{fixtureId}/{lang}} — tek bir maçın
 *       güncellemeleri (maç detay sayfası için)</li>
 * </ul>
 * {@code lang} {@code tr} veya {@code en}. {@link com.scorestv.football.live.LiveBroadcaster}
 * her değişimi iki dilde de yayar.
 *
 * <p>Şu an in-memory simple broker kullanılır — tek backend instance için
 * yeterli. İleride çoklu instance gerekirse RabbitMQ/ActiveMQ relay'e geçilir.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final ScorestvProperties properties;

    public WebSocketConfig(ScorestvProperties properties) {
        this.properties = properties;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Sunucudan istemciye yayın için "broker" tarafı.
        // Heartbeat (25sn) ACIK: Cloudflare/proxy idle WebSocket baglantilarini
        // ~100sn'de dusurur. Heartbeat olmadan baglanti "sessiz" kalip kopuyor,
        // istemci surekli yeniden baglaniyor ve canli update'ler kaciyordu.
        // setHeartbeatValue server'in {gonderme, bekleme} araliklari (ms).
        // Heartbeat icin TaskScheduler zorunlu — yoksa Spring heartbeat'i yok sayar.
        config.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[]{25000, 25000})
                .setTaskScheduler(webSocketHeartbeatScheduler());
        // İstemci -> sunucu mesajları için (şimdilik kullanılmıyor; @MessageMapping rota öneki).
        config.setApplicationDestinationPrefixes("/app");
    }

    /// STOMP broker heartbeat'lerini gonderen scheduler. Tek thread yeterli.
    private TaskScheduler webSocketHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // SockJS fallback eklemiyoruz — modern tarayıcı + mobil STOMP istemcileri
        // raw WebSocket destekler ve fallback gereksiz yere bağlantı kurmaya çalışır.
        registry.addEndpoint("/ws/fixtures")
                .setAllowedOriginPatterns(
                        properties.cors().allowedOrigins().toArray(new String[0]));
    }
}
