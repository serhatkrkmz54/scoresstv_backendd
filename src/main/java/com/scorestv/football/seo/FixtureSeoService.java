package com.scorestv.football.seo;

import com.scorestv.common.SlugUtil;
import com.scorestv.football.domain.Fixture;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

/**
 * Bir maç için SEO metadata'sını (title, description, keywords, canonical, slug)
 * otomatik üretir.
 *
 * <p>Metinler {@code messages*.properties} dosyalarından dile göre alınır:
 * İngilizce varsayılan ({@code messages.properties}), Türkçe destekli
 * ({@code messages_tr.properties}). Takım/lig adları API-Football'dan İngilizce
 * geldiği için her iki dilde de aynıdır; yerelleştirilen yalnızca şablon metnidir.
 */
@Service
public class FixtureSeoService {

    /** Desteklenen diller. Bilinmeyen/boş dil İngilizce'ye düşer. */
    private static final Locale TURKISH = Locale.of("tr");
    private static final Locale ENGLISH = Locale.ENGLISH;

    private final MessageSource messageSource;
    private final SeoProperties seoProperties;

    public FixtureSeoService(MessageSource messageSource, SeoProperties seoProperties) {
        this.messageSource = messageSource;
        this.seoProperties = seoProperties;
    }

    /**
     * Verilen maç ve dil için SEO metadata'sı üretir.
     *
     * @param fixture lig ve takım ilişkileri erişilebilir bir maç entity'si
     * @param lang    "tr" veya "en" (null / bilinmeyen -> "en")
     */
    public SeoMetadata forFixture(Fixture fixture, String lang) {
        Locale locale = resolveLocale(lang);

        String home = fixture.getHomeTeam().getName();
        String away = fixture.getAwayTeam().getName();
        String league = fixture.getLeague().getName();
        String date = formatDate(fixture, locale);

        Object[] args = {home, away, league, date};
        String title = messageSource.getMessage("seo.fixture.title", args, locale);
        String description = messageSource.getMessage("seo.fixture.description", args, locale);
        String keywords = messageSource.getMessage("seo.fixture.keywords", args, locale);

        String slug = SlugUtil.fixtureSlug(home, away, fixture.getId());
        String canonicalUrl = trimTrailingSlash(seoProperties.siteUrl()) + "/" + slug;

        return new SeoMetadata(title, description, keywords, canonicalUrl, slug,
                locale.getLanguage());
    }

    /** "tr" -> Türkçe; diğer her şey (null dahil) -> İngilizce. */
    private Locale resolveLocale(String lang) {
        return lang != null && "tr".equalsIgnoreCase(lang.trim()) ? TURKISH : ENGLISH;
    }

    /** Maç tarihini, dile ve yapılandırılmış saat dilimine göre biçimlendirir. */
    private String formatDate(Fixture fixture, Locale locale) {
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
                .withLocale(locale)
                .withZone(ZoneId.of(seoProperties.timezone()))
                .format(fixture.getKickoffAt());
    }

    private String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
