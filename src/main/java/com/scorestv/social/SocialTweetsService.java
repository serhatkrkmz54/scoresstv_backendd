package com.scorestv.social;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Yapılandırılan hesapların tweet'lerini arka planda toplayıp <b>Redis</b>'te
 * tutar; frontend tek bir ucu okur (ziyaretçi başına SocialData isteği YOK).
 *
 * <p><b>Neden Redis (bellek değil)?</b> Tazeleme işi {@link SocialTweetsJob}
 * ShedLock ile YALNIZCA bir node'da koşar. Cache bellek-içi olsaydı diğer
 * node'lar boş dönerdi. Redis paylaşımlı olduğundan tüm node'lar aynı veriyi
 * okur. Geçici hatada (boş sonuç) eldeki Redis verisi KORUNUR.
 */
@Service
public class SocialTweetsService {

    private static final Logger log = LoggerFactory.getLogger(SocialTweetsService.class);

    /** Paylaşımlı Redis anahtarı. */
    private static final String REDIS_KEY = "scorestv:social:tweets";
    /** TTL — refresh aralığından (30 dk) uzun; job durursa bile veri kalsın. */
    private static final Duration TTL = Duration.ofHours(6);

    private static final ObjectMapper MAPPER =
            new ObjectMapper().registerModule(new JavaTimeModule());
    private static final TypeReference<List<SocialTweet>> LIST_TYPE =
            new TypeReference<>() {};

    private final SocialDataClient client;
    private final SocialDataProperties props;
    private final StringRedisTemplate redis;

    public SocialTweetsService(SocialDataClient client, SocialDataProperties props,
                               StringRedisTemplate redis) {
        this.client = client;
        this.props = props;
        this.redis = redis;
    }

    /** Tüm hesapları çek, harmanla (en yeni üstte), kırp, Redis'e yaz. */
    public void refresh() {
        if (!props.enabled() || props.apiKey() == null || props.apiKey().isBlank()) {
            return;
        }
        List<SocialTweet> all = new ArrayList<>();
        for (String handle : props.accounts()) {
            try {
                all.addAll(client.fetchForAccount(handle.trim()));
            } catch (Exception e) {
                log.warn("SocialData hesap atlandı ({}): {}", handle, e.toString());
            }
        }
        all.sort(Comparator.comparing(SocialTweet::createdAt).reversed());
        if (all.size() > props.maxTotal()) {
            all = new ArrayList<>(all.subList(0, props.maxTotal()));
        }
        if (!all.isEmpty()) {
            try {
                redis.opsForValue().set(REDIS_KEY, MAPPER.writeValueAsString(all), TTL);
                log.info("SocialData yenilendi: {} tweet ({} hesap)",
                        all.size(), props.accounts().size());
            } catch (Exception e) {
                log.warn("SocialData Redis'e yazılamadı: {}", e.toString());
            }
        } else {
            log.debug("SocialData yenileme boş döndü — mevcut Redis verisi korunuyor.");
        }
    }

    public List<SocialTweet> getAll() {
        try {
            String json = redis.opsForValue().get(REDIS_KEY);
            if (json == null || json.isBlank()) return List.of();
            return MAPPER.readValue(json, LIST_TYPE);
        } catch (Exception e) {
            log.warn("SocialData Redis'ten okunamadı: {}", e.toString());
            return List.of();
        }
    }
}
