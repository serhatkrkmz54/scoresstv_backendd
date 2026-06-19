package com.scorestv.config;

import org.springframework.beans.factory.annotation.Value;
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
 * <p><b>Broker seçimi (env-driven):</b>
 * <ul>
 *   <li>{@code scorestv.websocket.relay.enabled=false} (varsayılan) → bellek-içi
 *       {@code SimpleBroker}. Tek instance / dev için yeterli; harici broker
 *       gerekmez.</li>
 *   <li>{@code scorestv.websocket.relay.enabled=true} → harici STOMP broker
 *       (RabbitMQ {@code rabbitmq_stomp} eklentisi / ActiveMQ) RELAY. ÇOK
 *       instance için ŞART: tüm node'lar broker üzerinden yayın yapar, böylece
 *       A node'una bağlı istemci B node'unun yayınını da alır.</li>
 * </ul>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final ScorestvProperties properties;

    private final boolean relayEnabled;
    private final String relayHost;
    private final int relayPort;
    private final String relayLogin;
    private final String relayPasscode;
    private final String relayVirtualHost;

    public WebSocketConfig(
            ScorestvProperties properties,
            @Value("${scorestv.websocket.relay.enabled:false}") boolean relayEnabled,
            @Value("${scorestv.websocket.relay.host:127.0.0.1}") String relayHost,
            @Value("${scorestv.websocket.relay.port:61613}") int relayPort,
            @Value("${scorestv.websocket.relay.login:guest}") String relayLogin,
            @Value("${scorestv.websocket.relay.passcode:guest}") String relayPasscode,
            @Value("${scorestv.websocket.relay.virtual-host:/}") String relayVirtualHost) {
        this.properties = properties;
        this.relayEnabled = relayEnabled;
        this.relayHost = relayHost;
        this.relayPort = relayPort;
        this.relayLogin = relayLogin;
        this.relayPasscode = relayPasscode;
        this.relayVirtualHost = relayVirtualHost;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        if (relayEnabled) {
            // ÇOK INSTANCE: harici STOMP broker relay. Heartbeat'leri broker
            // yönetir; system bağlantısı için send/receive aralıkları ayarlanır.
            config.enableStompBrokerRelay("/topic")
                    .setRelayHost(relayHost)
                    .setRelayPort(relayPort)
                    .setClientLogin(relayLogin)
                    .setClientPasscode(relayPasscode)
                    .setSystemLogin(relayLogin)
                    .setSystemPasscode(relayPasscode)
                    .setVirtualHost(relayVirtualHost)
                    .setSystemHeartbeatSendInterval(25000)
                    .setSystemHeartbeatReceiveInterval(25000);
        } else {
            // TEK INSTANCE / DEV: bellek-içi broker (harici broker gerekmez).
            // Heartbeat (25sn) ACIK: Cloudflare/proxy idle WebSocket baglantilarini
            // ~100sn'de dusurur. Heartbeat olmadan baglanti "sessiz" kalip kopuyor,
            // istemci surekli yeniden baglaniyor ve canli update'ler kaciyordu.
            // Heartbeat icin TaskScheduler zorunlu — yoksa Spring heartbeat'i yok sayar.
            config.enableSimpleBroker("/topic")
                    .setHeartbeatValue(new long[]{25000, 25000})
                    .setTaskScheduler(webSocketHeartbeatScheduler());
        }
        // İstemci -> sunucu mesajları için (şimdilik kullanılmıyor; @MessageMapping rota öneki).
        config.setApplicationDestinationPrefixes("/app");
    }

    /// STOMP SimpleBroker heartbeat'lerini gonderen scheduler. Tek thread yeterli.
    /// (Relay modunda kullanilmaz; heartbeat'i broker yonetir.)
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
