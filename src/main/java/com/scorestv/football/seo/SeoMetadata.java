package com.scorestv.football.seo;

/**
 * Bir sayfa için otomatik üretilen SEO metadata'sı.
 *
 * <p>Maç verisinden deterministik üretilir, veritabanında saklanmaz. İleride
 * OpenGraph/Twitter alanları gerekirse bu record'a eklenebilir.
 *
 * @param title        sayfa başlığı (&lt;title&gt; ve h1)
 * @param description  meta description
 * @param keywords     meta keywords (virgülle ayrılmış)
 * @param canonicalUrl tam canonical URL
 * @param slug         maç slug'ı (canonical'ın son parçası)
 * @param lang         metadata'nın dili ("en" / "tr")
 */
public record SeoMetadata(
        String title,
        String description,
        String keywords,
        String canonicalUrl,
        String slug,
        String lang
) {
}
