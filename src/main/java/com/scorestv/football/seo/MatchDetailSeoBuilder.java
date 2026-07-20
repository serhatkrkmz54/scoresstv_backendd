package com.scorestv.football.seo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scorestv.common.SlugUtil;
import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.League;
import com.scorestv.football.domain.Team;
import com.scorestv.football.domain.TranslatableName;
import com.scorestv.football.domain.Venue;
import com.scorestv.football.web.dto.MatchSeoResponse;
import com.scorestv.storage.MinioStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maç detay sayfası için tam SEO paketini ({@link MatchSeoResponse}) üretir:
 * title/description/keywords/canonical/slug + Open Graph + Twitter Card +
 * JSON-LD Schema.org SportsEvent + breadcrumbs + hreflang.
 *
 * <p>Bu sınıf {@link FixtureSeoService}'i kullanmaz — kendi içinde MessageSource
 * üzerinden tam paket üretir. Böylece detay tarafına özgü ayarlamalar (Türkçe
 * takım adlarını şablonlara geçirmek, JSON-LD vb.) tek yerden kontrol edilir.
 */
@Component
public class MatchDetailSeoBuilder {

    private static final Logger log = LoggerFactory.getLogger(MatchDetailSeoBuilder.class);

    private static final Locale TURKISH = Locale.of("tr");
    private static final Locale ENGLISH = Locale.ENGLISH;
    private static final String SITE_NAME = "Scores TV";

    /** Sonuç bilinen biten maç statüleri — özel SEO başlık/açıklama uygulanır. */
    private static final Set<String> FINISHED_STATUSES = Set.of("FT", "AET", "PEN");

    /**
     * Yerel {@code ObjectMapper} — DI'dan çekmek yerine doğrudan instantiate
     * ederiz. Spring Boot 4'ün {@code spring-boot-starter-webmvc}'si Jackson
     * sınıflarını classpath'e koysa da {@code ObjectMapper} bean'ini auto-config
     * etmiyor; bizim JSON-LD serileştirmemiz yalnız Map/List/String içerir,
     * özel ayara (JSR-310, naming strategy vb.) ihtiyacı yok — default yeterli.
     */
    private static final ObjectMapper JSON_LD_MAPPER = new ObjectMapper();

    private final MessageSource messageSource;
    private final SeoProperties seoProperties;
    private final MinioStorageService storage;

    public MatchDetailSeoBuilder(MessageSource messageSource,
                                 SeoProperties seoProperties,
                                 MinioStorageService storage) {
        this.messageSource = messageSource;
        this.seoProperties = seoProperties;
        this.storage = storage;
    }

    /**
     * Verilen maç ve dil için tam SEO paketini üretir (yayın bilgisi olmadan).
     *
     * @param fixture lig + takım ilişkileri yüklenmiş bir maç entity'si
     * @param lang    "tr" → Türkçe; aksi halde İngilizce
     */
    public MatchSeoResponse build(Fixture fixture, String lang) {
        return build(fixture, lang, List.of());
    }

    /**
     * Verilen maç, dil ve yayın kanalları için tam SEO paketini üretir.
     *
     * <p>Yaklaşan (oynanmamış) maçlarda başlık/açıklama "saat kaçta / hangi
     * kanalda" niyetine göre üretilir; {@code channelNames} doluysa kanal(lar)
     * açıklamaya ve JSON-LD {@code BroadcastEvent}'e işlenir.
     *
     * @param fixture      maç entity'si
     * @param lang         "tr" → Türkçe; aksi halde İngilizce
     * @param channelNames yayın kanalı adları (dile göre çözülmüş); boş olabilir
     */
    public MatchSeoResponse build(Fixture fixture, String lang, List<String> channelNames) {
        boolean turkish = "tr".equalsIgnoreCase(lang);
        Locale locale = turkish ? TURKISH : ENGLISH;
        String localeCode = turkish ? "tr" : "en";

        Team home = fixture.getHomeTeam();
        Team away = fixture.getAwayTeam();
        League league = fixture.getLeague();

        // Dil-bağımlı görünen adlar (name_tr varsa Türkçe, yoksa İngilizce).
        String homeName = displayName(home, turkish);
        String awayName = displayName(away, turkish);
        String leagueName = displayName(league, turkish);
        String date = formatDate(fixture, locale);

        // Slug DİLE GÖRE: TR takım adlarından /mac/, EN adlarından /match/.
        // Her iki dilin de gerçek URL'i üretilir; canonical o dilin KENDİ URL'ine
        // işaret eder (self-referencing), hreflang ikisini karşılıklı bağlar.
        String baseUrl = trimTrailingSlash(seoProperties.siteUrl());
        String slugTr = SlugUtil.fixtureSlug(
                displayName(home, true), displayName(away, true), fixture.getId());
        String slugEn = SlugUtil.fixtureSlug(
                home.getName(), away.getName(), fixture.getId());
        String urlTr = baseUrl + "/mac/" + slugTr;
        String urlEn = baseUrl + "/match/" + slugEn;
        String slug = turkish ? slugTr : slugEn;
        String canonicalUrl = turkish ? urlTr : urlEn;

        // Title/desc/keywords — messages_tr veya messages_en'den.
        Object[] args = {homeName, awayName, leagueName, date};
        String channel = joinChannels(channelNames);
        String title;
        String description;
        if (isFinishedWithScore(fixture)) {
            // FT/AET/PEN — sonuç odaklı SEO başlık + açıklama.
            title = buildFinishedTitle(fixture, locale, homeName, awayName);
            description = buildFinishedDescription(
                    fixture, locale, homeName, awayName, leagueName, date);
        } else if (isUpcoming(fixture)) {
            // Oynanmamış maç — "saat kaçta / hangi kanalda" niyeti. Kanal DOLUYSA
            // hem başlık hem açıklama "hangi kanalda" ifadesini içerir; kanal yoksa
            // yalnız saat/ne-zaman odaklı (başlıkta cevaplayamayacağımız soruyu sormayız).
            String time = formatTime(fixture, locale);
            Object[] sArgs = {homeName, awayName, date, time, channel, leagueName};
            boolean hasChannel = !channel.isBlank();
            title = messageSource.getMessage(
                    hasChannel ? "seo.fixture.title.scheduled.tv" : "seo.fixture.title.scheduled",
                    sArgs, locale);
            description = messageSource.getMessage(
                    hasChannel ? "seo.fixture.description.scheduled.tv" : "seo.fixture.description.scheduled",
                    sArgs, locale);
        } else {
            // Devam eden / canlı maç — genel "canlı skor" başlığı.
            title = messageSource.getMessage("seo.fixture.title", args, locale);
            description = messageSource.getMessage("seo.fixture.description", args, locale);
        }
        String keywords = messageSource.getMessage("seo.fixture.keywords", args, locale);

        String image = ogImage(fixture);

        MatchSeoResponse.OpenGraph og = new MatchSeoResponse.OpenGraph(
                title,
                description,
                "website",
                canonicalUrl,
                image,
                SITE_NAME,
                turkish ? "tr_TR" : "en_US");

        MatchSeoResponse.TwitterCard twitter = new MatchSeoResponse.TwitterCard(
                "summary_large_image",
                title,
                description,
                image);

        String jsonLd = buildJsonLd(
                fixture, canonicalUrl, baseUrl, homeName, awayName, leagueName, image,
                description, channelNames);

        List<MatchSeoResponse.Breadcrumb> breadcrumbs = buildBreadcrumbs(
                fixture, homeName, awayName, leagueName, canonicalUrl, turkish, baseUrl);

        List<MatchSeoResponse.Hreflang> hreflang = buildHreflang(urlTr, urlEn);

        return new MatchSeoResponse(
                title, description, keywords, canonicalUrl, slug, localeCode,
                og, twitter, jsonLd, breadcrumbs, hreflang);
    }

    /** Schema.org SportsEvent JSON-LD'sini hazır string olarak üretir. */
    private String buildJsonLd(Fixture fixture, String canonicalUrl, String baseUrl,
                               String homeName, String awayName, String leagueName,
                               String image, String description,
                               List<String> channelNames) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("@context", "https://schema.org");
        root.put("@type", "SportsEvent");
        root.put("name", homeName + " vs " + awayName);
        root.put("url", canonicalUrl);
        if (description != null && !description.isBlank()) {
            root.put("description", description);
        }
        root.put("startDate", fixture.getKickoffAt().toString());
        // Tahmini bitiş: ilk düdük + 2 saat (devre arası dâhil tipik maç süresi).
        root.put("endDate", fixture.getKickoffAt().plus(Duration.ofHours(2)).toString());
        root.put("eventStatus", schemaEventStatus(fixture.getStatusShort()));
        root.put("eventAttendanceMode", "https://schema.org/OfflineEventAttendanceMode");

        // location — Google Event için ZORUNLU. Venue varsa stadyum, yoksa lig ülkesi.
        Map<String, Object> location = buildLocation(fixture);
        if (location != null) {
            root.put("location", location);
        }

        List<Map<String, Object>> competitors = new ArrayList<>(2);
        competitors.add(teamSchema(homeName, fixture.getHomeTeam().getLogoKey()));
        competitors.add(teamSchema(awayName, fixture.getAwayTeam().getLogoKey()));
        root.put("competitor", competitors);
        // performer — aynı takımlar (Google'ın opsiyonel "performer" ipucunu karşılar).
        root.put("performer", competitors);

        // organizer — ligi/organizasyonu temsil eder. ESKİDEN superEvent (SportsEvent)
        // idi; Google onu AYRI bir Event sanıp startDate/location zorunlu kılıyordu
        // ("Dünya Kupası" kritik hataları). Organization bu zorunluluğu taşımaz.
        Map<String, Object> organizer = new LinkedHashMap<>();
        organizer.put("@type", "Organization");
        organizer.put("name", leagueName);
        // organizer.url — lig sayfası (Google Event "organizer.url" önerisi).
        // baseUrl doğrudan parametreden gelir (canonical artık /mac|/match önekli).
        if (fixture.getLeague() != null && baseUrl != null) {
            String leagueSlug = SlugUtil.slugify(fixture.getLeague().getName());
            if (leagueSlug.isEmpty()) {
                leagueSlug = "league";
            }
            organizer.put("url",
                    baseUrl + "/league/" + leagueSlug + "-" + fixture.getLeague().getId());
        }
        root.put("organizer", organizer);

        // subEvent — TV yayını (BroadcastEvent). Kanal varsa "nerede izlenir"
        // bilgisini geçerli Schema.org ile verir (yayıncı = publishedOn).
        if (channelNames != null && !channelNames.isEmpty()) {
            List<Map<String, Object>> subEvents = new ArrayList<>();
            for (String ch : channelNames.stream().limit(3).toList()) {
                if (ch == null || ch.isBlank()) {
                    continue;
                }
                Map<String, Object> be = new LinkedHashMap<>();
                be.put("@type", "BroadcastEvent");
                be.put("name", homeName + " vs " + awayName);
                be.put("startDate", fixture.getKickoffAt().toString());
                be.put("isLiveBroadcast", true);
                Map<String, Object> publishedOn = new LinkedHashMap<>();
                publishedOn.put("@type", "BroadcastService");
                publishedOn.put("name", ch);
                be.put("publishedOn", publishedOn);
                subEvents.add(be);
            }
            if (!subEvents.isEmpty()) {
                root.put("subEvent", subEvents);
            }
        }

        if (image != null) {
            root.put("image", image);
        }

        try {
            return JSON_LD_MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            log.warn("JSON-LD serileştirilemedi: fixtureId={} — {}",
                    fixture.getId(), ex.getMessage());
            return "{}";
        }
    }

    /**
     * SportsEvent {@code location}'ı: önce stadyum (venue), yoksa lig ülkesi.
     * Google Event için location zorunlu — venue verisi olmayan maçlarda da
     * en azından ülke seviyesinde geçerli bir {@code Place} döndürürüz.
     */
    private Map<String, Object> buildLocation(Fixture fixture) {
        Venue venue = fixture.getVenue();
        if (venue != null && venue.getName() != null && !venue.getName().isBlank()) {
            Map<String, Object> location = new LinkedHashMap<>();
            location.put("@type", "Place");
            location.put("name", venue.getName());
            // address — Google Event "location.address" önerisi. Şehir varsa
            // addressLocality, ayrıca lig ülkesi varsa addressCountry ekle.
            Map<String, Object> address = new LinkedHashMap<>();
            address.put("@type", "PostalAddress");
            if (venue.getCity() != null && !venue.getCity().isBlank()) {
                address.put("addressLocality", venue.getCity());
            }
            String venueCountry = fixture.getLeague() != null
                    ? fixture.getLeague().getCountryName() : null;
            if (venueCountry != null && !venueCountry.isBlank()) {
                address.put("addressCountry", venueCountry);
            }
            // Yalnızca @type varsa (hiç alan yoksa) address ekleme.
            if (address.size() > 1) {
                location.put("address", address);
            }
            return location;
        }
        // Fallback — lig ülkesi (venue yoksa).
        String country = fixture.getLeague() != null
                ? fixture.getLeague().getCountryName() : null;
        if (country != null && !country.isBlank()) {
            Map<String, Object> location = new LinkedHashMap<>();
            location.put("@type", "Place");
            location.put("name", country);
            Map<String, Object> address = new LinkedHashMap<>();
            address.put("@type", "PostalAddress");
            address.put("addressCountry", country);
            location.put("address", address);
            return location;
        }
        return null;
    }

    private Map<String, Object> teamSchema(String name, String logoKey) {
        Map<String, Object> team = new LinkedHashMap<>();
        team.put("@type", "SportsTeam");
        team.put("name", name);
        if (logoKey != null) {
            team.put("logo", storage.publicUrl(logoKey));
        }
        return team;
    }

    /** API durum kodunu Schema.org EventStatus URI'sine çevirir. */
    private static String schemaEventStatus(String statusShort) {
        if (statusShort == null) {
            return "https://schema.org/EventScheduled";
        }
        return switch (statusShort) {
            case "PST" -> "https://schema.org/EventPostponed";
            case "CANC", "ABD", "AWD", "WO" -> "https://schema.org/EventCancelled";
            default -> "https://schema.org/EventScheduled";
        };
    }

    /** Breadcrumb zinciri: Ana Sayfa › Lig › Maç. */
    private List<MatchSeoResponse.Breadcrumb> buildBreadcrumbs(Fixture fixture,
                                                               String homeName,
                                                               String awayName,
                                                               String leagueName,
                                                               String matchUrl,
                                                               boolean turkish,
                                                               String baseUrl) {
        List<MatchSeoResponse.Breadcrumb> list = new ArrayList<>();
        list.add(new MatchSeoResponse.Breadcrumb(
                1, turkish ? "Ana Sayfa" : "Home", baseUrl + "/"));

        // Lig URL'i — frontend henüz olmasa da kararlı bir URL şeması:
        // /league/{lig-slug}-{lig-id}
        String leagueSlug = SlugUtil.slugify(fixture.getLeague().getName());
        if (leagueSlug.isEmpty()) {
            leagueSlug = "league";
        }
        list.add(new MatchSeoResponse.Breadcrumb(
                2, leagueName, baseUrl + "/league/" + leagueSlug + "-" + fixture.getLeague().getId()));

        list.add(new MatchSeoResponse.Breadcrumb(
                3, homeName + " - " + awayName, matchUrl));
        return list;
    }

    /** Dil alternatifleri: tr (/mac/...), en (/match/...), x-default (en). */
    private List<MatchSeoResponse.Hreflang> buildHreflang(String urlTr, String urlEn) {
        return List.of(
                new MatchSeoResponse.Hreflang("tr", urlTr),
                new MatchSeoResponse.Hreflang("en", urlEn),
                new MatchSeoResponse.Hreflang("x-default", urlEn));
    }

    /** OG/Twitter görseli: önce lig logosu, yoksa ev sahibi takım logosu, yoksa null. */
    private String ogImage(Fixture fixture) {
        String key = fixture.getLeague().getLogoKey();
        if (key == null) {
            key = fixture.getHomeTeam().getLogoKey();
        }
        return key != null ? storage.publicUrl(key) : null;
    }

    /**
     * Maç sonucu kesinleşmiş mi? FT/AET/PEN ve hem ev hem deplasman gol
     * sayısı dolu olmalı (örn. iptal/abandoned skorsuz bitebiliyor).
     */
    private static boolean isFinishedWithScore(Fixture f) {
        return f.getStatusShort() != null
                && FINISHED_STATUSES.contains(f.getStatusShort())
                && f.getHomeGoals() != null
                && f.getAwayGoals() != null;
    }

    /**
     * Bitmiş maç için sonuç-odaklı SEO başlık.
     * <ul>
     *   <li>FT → "Real - Madrid Maçı 2-1 Bitti | ..."</li>
     *   <li>AET → "... Maçı 2-1 (Uzatmalarda) Bitti | ..."</li>
     *   <li>PEN → "... Maçı Penaltılarda 5-4 Bitti | ..."</li>
     * </ul>
     */
    private String buildFinishedTitle(Fixture f, Locale locale,
                                      String home, String away) {
        Object[] args = scoreArgs(f, home, away, null, null);
        String key = switch (f.getStatusShort()) {
            case "AET" -> "seo.fixture.title.aet";
            case "PEN" -> "seo.fixture.title.pen";
            default -> "seo.fixture.title.ft";  // FT
        };
        return messageSource.getMessage(key, args, locale);
    }

    /**
     * Bitmiş maç için sonuç-odaklı SEO açıklama.
     * <ul>
     *   <li>FT (beraberlik) → "... maçı normal süre içerisinde 1-1 berabere sona ermiştir. {lig}, {tarih}."</li>
     *   <li>FT (galip var) → "... 2-1 sona ermiştir. {kazanan} galip gelmiştir. ..."</li>
     *   <li>AET → uzatmalarda
     *   <li>PEN → penaltılarda
     * </ul>
     */
    private String buildFinishedDescription(Fixture f, Locale locale,
                                            String home, String away,
                                            String league, String date) {
        int hG = f.getHomeGoals();
        int aG = f.getAwayGoals();
        String status = f.getStatusShort();
        String winner = resolveWinner(f, home, away);  // null → beraberlik
        Object[] args = scoreArgs(f, home, away, league, date);
        // 9. arg kazanan — switch içinde alıyoruz.
        args = appendWinner(args, winner == null ? "" : winner);

        String key;
        if ("PEN".equals(status)) {
            key = "seo.fixture.description.pen";
        } else if ("AET".equals(status)) {
            key = "seo.fixture.description.aet";
        } else {
            // FT
            key = (hG == aG)
                    ? "seo.fixture.description.ft.draw"
                    : "seo.fixture.description.ft.win";
        }
        return messageSource.getMessage(key, args, locale);
    }

    /**
     * Kazananı belirler:
     * <ul>
     *   <li>PEN için scorePenHome vs scorePenAway karşılaştırması</li>
     *   <li>FT/AET için homeGoals vs awayGoals</li>
     *   <li>Beraberlikte null</li>
     * </ul>
     */
    private static String resolveWinner(Fixture f, String home, String away) {
        if ("PEN".equals(f.getStatusShort())
                && f.getScorePenHome() != null && f.getScorePenAway() != null) {
            int ph = f.getScorePenHome();
            int pa = f.getScorePenAway();
            if (ph > pa) return home;
            if (pa > ph) return away;
            return null;
        }
        int hG = f.getHomeGoals() == null ? 0 : f.getHomeGoals();
        int aG = f.getAwayGoals() == null ? 0 : f.getAwayGoals();
        if (hG > aG) return home;
        if (aG > hG) return away;
        return null;
    }

    /**
     * Şablonların paylaştığı 8'li arg dizisi:
     * {0}=ev, {1}=deplasman, {2}=evGol, {3}=deplGol, {4}=penEv, {5}=penDepl,
     * {6}=lig (null → ""), {7}=tarih (null → "").
     */
    private static Object[] scoreArgs(Fixture f, String home, String away,
                                      String league, String date) {
        Integer penH = f.getScorePenHome();
        Integer penA = f.getScorePenAway();
        return new Object[]{
                home, away,
                f.getHomeGoals() == null ? 0 : f.getHomeGoals(),
                f.getAwayGoals() == null ? 0 : f.getAwayGoals(),
                penH == null ? 0 : penH,
                penA == null ? 0 : penA,
                league == null ? "" : league,
                date == null ? "" : date
        };
    }

    /** scoreArgs sonuna kazanan adını (arg {8}) ekler. */
    private static Object[] appendWinner(Object[] base, String winner) {
        Object[] out = new Object[base.length + 1];
        System.arraycopy(base, 0, out, 0, base.length);
        out[base.length] = winner;
        return out;
    }

    /** Tarihi dil + saat dilimine göre uzun biçimde döner. */
    private String formatDate(Fixture fixture, Locale locale) {
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
                .withLocale(locale)
                .withZone(ZoneId.of(seoProperties.timezone()))
                .format(fixture.getKickoffAt());
    }

    /** Başlangıç saatini site saat dilimine göre kısa biçimde döner (örn "22:00"). */
    private String formatTime(Fixture fixture, Locale locale) {
        return DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
                .withLocale(locale)
                .withZone(ZoneId.of(seoProperties.timezone()))
                .format(fixture.getKickoffAt());
    }

    /**
     * Maç oynanmamış (yaklaşan) mı? Kesin sonuç yok VE başlangıç anı gelecekte.
     * Böylece canlı/devam eden maçlar bu daldan hariç tutulur.
     */
    private static boolean isUpcoming(Fixture f) {
        return !isFinishedWithScore(f)
                && f.getKickoffAt() != null
                && f.getKickoffAt().isAfter(Instant.now());
    }

    /** İlk 2 kanal adını virgülle birleştirir (boşları eler, yoksa boş string). */
    private static String joinChannels(List<String> channelNames) {
        if (channelNames == null || channelNames.isEmpty()) {
            return "";
        }
        return channelNames.stream()
                .filter(c -> c != null && !c.isBlank())
                .limit(2)
                .collect(Collectors.joining(", "));
    }

    /** Dil "tr" ise ve Türkçe karşılığı girilmişse Türkçe ad; aksi halde İngilizce. */
    private static String displayName(TranslatableName entity, boolean turkish) {
        if (turkish) {
            String tr = entity.getNameTr();
            if (tr != null && !tr.isBlank()) {
                return tr;
            }
        }
        return entity.getName();
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
