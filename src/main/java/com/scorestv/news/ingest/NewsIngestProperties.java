package com.scorestv.news.ingest;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * Haber içe aktarma (agregatör) yapılandırması — NewsData.io üzerinden ulusal +
 * Türkçe VE İngilizce spor haberlerini periyodik çekip <b>DRAFT</b> olarak açar;
 * editör addnews panelinde onaylayıp yayınlar.
 *
 * <p><b>Çok dilli:</b> {@code feeds} listesindeki her feed kendi diliyle çekilir
 * ve o dilde saklanır (TR feed → lang=tr, EN feed → lang=en). Her çalıştırmada
 * feed başına bir NewsData çağrısı yapılır.
 *
 * <p><b>Yasal not:</b> agregatör — yalnız başlık + özet + kaynak linki saklanır,
 * tam makale metni değil. Her habere kaynak atfı yapılır.
 *
 * <p>{@code enabled=false} (varsayılan) iken hiç çağrı yapılmaz. Devreye almak
 * için: {@code NEWS_INGEST_ENABLED=true}, {@code NEWSDATA_API_KEY=...} ve
 * {@code NEWS_INGEST_AUTHOR_ID=<mevcut kullanıcı id>}.
 */
@ConfigurationProperties(prefix = "scorestv.news-ingest")
public record NewsIngestProperties(
        @DefaultValue("false") boolean enabled,
        /** DRAFT'ların açılacağı kullanıcı id'si (users.id). 0/null ise çalışmaz. */
        Long authorId,
        /** Kaynak etiketi (news_articles.source) — tekilleştirme anahtarının parçası. */
        @DefaultValue("NEWSDATA") String source,
        /** Feed BAŞINA bir çalıştırmada en fazla kaç yeni DRAFT açılır. */
        @DefaultValue("10") int maxPerRun,
        /** Zamanlanmış çekim cron'u (varsayılan 30 dk — 2 feed ile ~96 çağrı/gün). */
        @DefaultValue("0 */30 * * * *") String cron,
        // --- NewsData.io ---
        @DefaultValue("https://newsdata.io/api/1") String baseUrl,
        /** NewsData.io API anahtarı (yalnız sunucuda, .env). */
        String apiKey,
        /** Kategori — spor (tüm feed'lerde ortak). */
        @DefaultValue("sports") String category,
        /** Dil feed'leri — her biri kendi diliyle çekilir + o dilde saklanır. */
        List<Feed> feeds
) {
    /**
     * Bir dil feed'i.
     * @param lang     saklanacak dil kodu (news_articles.lang): "tr" / "en"
     * @param language NewsData language filtresi (tr, en, ...)
     * @param country  NewsData country filtresi (tr, gb, ...) — boş ise gönderilmez
     *                 (uluslararası; İngilizce için genelde boş bırakılır)
     */
    public record Feed(String lang, String language, String country) {}
}
