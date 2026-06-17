package com.scorestv.social;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Yapılandırılan hesapların tweet'lerini arka planda toplayıp bellek cache'inde
 * tutar; frontend tek bir ucu okur (ziyaretçi başına SocialData isteği YOK).
 *
 * <p>Cache {@code volatile} — job thread'i yazar, request thread'leri okur.
 * Geçici hatada (boş sonuç) eldeki cache KORUNUR; "veri kayboldu" olmaz.
 */
@Service
public class SocialTweetsService {

    private static final Logger log = LoggerFactory.getLogger(SocialTweetsService.class);

    private final SocialDataClient client;
    private final SocialDataProperties props;
    private volatile List<SocialTweet> cache = List.of();

    public SocialTweetsService(SocialDataClient client, SocialDataProperties props) {
        this.client = client;
        this.props = props;
    }

    /** Tüm hesapları çek, harmanla (en yeni üstte), kırp, cache'le. */
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
            this.cache = List.copyOf(all);
            log.info("SocialData yenilendi: {} tweet ({} hesap)",
                    all.size(), props.accounts().size());
        } else {
            log.debug("SocialData yenileme boş döndü — mevcut cache korunuyor.");
        }
    }

    public List<SocialTweet> getAll() {
        return cache;
    }
}
