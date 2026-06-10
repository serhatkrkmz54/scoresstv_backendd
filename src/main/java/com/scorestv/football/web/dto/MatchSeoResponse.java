package com.scorestv.football.web.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Maç detay sayfası için tam SEO paketi.
 *
 * <p>Frontend bunu doğrudan kullanır:
 * <ul>
 *   <li>{@code title} → {@code <title>}</li>
 *   <li>{@code description}/{@code keywords} → {@code <meta name="...">}</li>
 *   <li>{@code openGraph} → {@code <meta property="og:*">}</li>
 *   <li>{@code twitter} → {@code <meta name="twitter:*">}</li>
 *   <li>{@code jsonLd} → {@code <script type="application/ld+json">…</script>}
 *       (Google "Sports Event" zengin sonuç paneli için)</li>
 *   <li>{@code breadcrumbs} → BreadcrumbList JSON-LD'si VEYA navigasyon UI'sı için</li>
 *   <li>{@code hreflang} → {@code <link rel="alternate" hreflang="…">}</li>
 * </ul>
 */
public record MatchSeoResponse(
        String title,
        String description,
        String keywords,
        String canonicalUrl,
        String slug,
        /** Aktif dil ("tr" veya "en"). */
        String locale,
        OpenGraph openGraph,
        TwitterCard twitter,
        /** {@code application/ld+json} olarak embed edilecek hazır JSON metni. */
        String jsonLd,
        List<Breadcrumb> breadcrumbs,
        List<Hreflang> hreflang
) implements Serializable {

    /** Facebook / WhatsApp / LinkedIn paylaşımları için OG kartı. */
    public record OpenGraph(
            String title,
            String description,
            /** Genellikle "website"; canlı maçta "video.other" da kullanılabilir. */
            String type,
            String url,
            /** 1200x630 ideal; yoksa takım/lig logosu — frontend daha iyiyse override eder. */
            String image,
            String siteName,
            /** "tr_TR" / "en_US" gibi. */
            String locale
    ) implements Serializable {
    }

    /** Twitter Card için. */
    public record TwitterCard(
            /** "summary" veya "summary_large_image". */
            String card,
            String title,
            String description,
            String image
    ) implements Serializable {
    }

    /** Tek bir breadcrumb basamağı (1-tabanlı sıra). */
    public record Breadcrumb(
            int position,
            String name,
            String url
    ) implements Serializable {
    }

    /** Aynı içeriğin başka bir dildeki sürümünün URL'i. */
    public record Hreflang(
            /** "tr", "en" ya da "x-default". */
            String lang,
            String href
    ) implements Serializable {
    }
}
