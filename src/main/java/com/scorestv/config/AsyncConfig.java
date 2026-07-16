package com.scorestv.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * {@code @Async} icin SINIRLI havuz.
 *
 * <p>Virtual threads acik ({@code spring.threads.virtual.enabled=true}) oldugu
 * icin, varsayilan async executor sinirsizdir. Bot taramasi (sitemap'teki on
 * binlerce takim/oyuncu/mac) ayni anda binlerce lazy-sync tetikleyince, her biri
 * bir DB baglantisi kapip Hikari havuzunu (50) tuketebilir ve gercek kullanicilar
 * "connection timeout" alir. Bu bean async'i platform-thread havuzu ile sinirlar:
 * en fazla 16 es zamanli arka-plan gorevi → en fazla 16 async DB baglantisi.
 *
 * <p>Kuyruk dolunca yeni gorevler {@link ThreadPoolExecutor.DiscardPolicy} ile
 * SESSIZCE dusurulur — lazy-sync zaten gunluk job + sonraki ziyaretle tamamlanir,
 * yani veri kaybi olmaz; sadece o anki tazeleme atlanir. Boylece asiri yukte
 * bellek/baglanti patlamasi yerine zarif degradasyon olur.
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Bean
    public ThreadPoolTaskExecutor asyncExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(8);
        ex.setMaxPoolSize(16);
        ex.setQueueCapacity(1000);
        ex.setThreadNamePrefix("stv-async-");
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.setAwaitTerminationSeconds(20);
        ex.initialize();
        return ex;
    }

    /**
     * BİLDİRİM enqueue için AYRI havuz. Gol/kart/kadro dispatch'i ({@code @Async})
     * eskiden {@link #asyncExecutor()} (stv-async) havuzunu lazy-sync + bot
     * taramasıyla PAYLAŞIYORDU; o havuz dolunca bildirim enqueue'su gecikiyor ya
     * da {@code DiscardPolicy} ile DÜŞÜYORDU (bildirim hiç gitmiyordu). Bu havuz
     * bildirime ayrılmıştır; kuyruk dolarsa {@link ThreadPoolExecutor.CallerRunsPolicy}
     * ile çağıran (canlı tick) enqueue'yu satır-içi yapar → bildirim ASLA düşmez,
     * sadece nadiren birkaç ms yavaşlar. İş kısa (DB'ye outbox satırı yazımı);
     * asıl FCM gönderimi worker'da.
     */
    @Bean
    public ThreadPoolTaskExecutor notifyExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(4);
        ex.setMaxPoolSize(8);
        ex.setQueueCapacity(500);
        ex.setThreadNamePrefix("stv-notify-");
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        ex.setWaitForTasksToCompleteOnShutdown(true);
        ex.setAwaitTerminationSeconds(20);
        ex.initialize();
        return ex;
    }

    @Override
    public Executor getAsyncExecutor() {
        return asyncExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) ->
                log.warn("Async gorev hatasi: {} — {}", method.getName(), throwable.getMessage());
    }
}
