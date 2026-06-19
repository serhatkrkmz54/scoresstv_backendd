package com.scorestv.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Dağıtık scheduled-task kilidi (ShedLock).
 *
 * <p><b>Neden?</b> Birden fazla backend instance çalıştırıldığında {@code
 * @Scheduled} işler (canlı tick, outbox worker, sync job'ları) varsayılan
 * olarak HER node'da çalışır → çift API çağrısı, çift iş, yarış. ShedLock ile
 * {@code @SchedulerLock} işaretli her iş aynı anda YALNIZCA bir node'da koşar.
 *
 * <p><b>Lock store:</b> Redis (zaten kuruluyuz). Anahtarlar {@code
 * job-lock:scorestv:<name>} biçiminde namespace'lenir; tek instance'lı kurulumda
 * da sorunsuz çalışır (kilidi anında alır).
 *
 * <p><b>defaultLockAtMostFor:</b> bir node iş sırasında çökerse kilit en geç bu
 * süre sonunda serbest kalır (güvenlik ağı). Hızlı işler {@code @SchedulerLock}
 * üzerinde kendi kısa lockAtMostFor değerini verir.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory, "scorestv");
    }
}
