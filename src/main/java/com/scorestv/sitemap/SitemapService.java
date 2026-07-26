package com.scorestv.sitemap;

import com.scorestv.common.SlugUtil;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sitemap uretimi icin hafif listeleme. Tam entity yuklemez — sadece
 * id + name (+ nameTr) + updatedAt projeksiyonu, sayfali.
 *
 * <p>Her varlik icin HEM EN HEM TR path doner (dile gore slug):
 * <ul>
 *   <li>takim:  EN {@code /team/{slug}-{id}}     TR {@code /takim/{slugTr}-{id}}</li>
 *   <li>oyuncu: EN {@code /player/{slug}-{id}}    TR {@code /oyuncu/{slug}-{id}} (oyuncu adi cevrilmez)</li>
 *   <li>lig:    EN {@code /league/{slug}-{id}}    TR {@code /lig/{slugTr}-{id}}</li>
 *   <li>mac:    EN {@code /match/{ev-dep-id}}     TR {@code /mac/{evTr-depTr-id}}</li>
 * </ul>
 */
@Service
public class SitemapService {

    @PersistenceContext
    private EntityManager em;

    @Transactional(readOnly = true)
    public Map<String, Long> counts() {
        return Map.ofEntries(
                Map.entry("teams", count("Team")),
                Map.entry("players", indexablePlayerCount()),
                Map.entry("leagues", count("League")),
                Map.entry("matches", indexableMatchCount()),
                Map.entry("basketballLeagues", count("BasketballLeague")),
                Map.entry("basketballTeams", count("BasketballTeam")),
                Map.entry("basketballGames", indexableBasketballGameCount()),
                Map.entry("volleyballLeagues", count("VolleyballLeague")),
                Map.entry("volleyballTeams", count("VolleyballTeam")),
                Map.entry("volleyballGames", indexableVolleyballGameCount()));
    }

    private long count(String entity) {
        return (Long) em.createQuery("select count(e) from " + entity + " e")
                .getSingleResult();
    }

    /**
     * SEO: sitemap'e yalnızca "içeriği olan" maçlar girer — skoru gelmiş
     * (oynanmış/canlı) fikstürler. Henüz oynanmamış/verisi hiç gelmemiş "thin"
     * maç sayfaları web tarafında noindex olduğu için sitemap'te de sayılmaz;
     * böylece Google'a boş/noindex URL sunmayız (crawl bütçesi + SC uyarısı).
     */
    private long indexableMatchCount() {
        return (Long) em.createQuery(
                        "select count(f) from Fixture f "
                                + "where f.homeGoals is not null and f.awayGoals is not null")
                .getSingleResult();
    }

    /**
     * SEO: API-Sports bazı oyuncuları "Data Not Available" yer tutucu adıyla
     * gönderir — bu kayıtların sayfası içeriksizdir (Google soft-404 sayar,
     * Search Console'da görüldü). Sitemap'e girmezler; sayfa/sayım filtresi
     * {@link #PLAYER_PLACEHOLDER_FILTER} ile ortak.
     */
    private static final String PLAYER_PLACEHOLDER_FILTER =
            " where e.name is not null and lower(e.name) <> 'data not available' ";

    private long indexablePlayerCount() {
        return (Long) em.createQuery(
                        "select count(e) from Player e" + PLAYER_PLACEHOLDER_FILTER)
                .getSingleResult();
    }

    /** Basketbol: futboldaki gibi yalnız skoru gelmiş (oynanmış/canlı) maçlar. */
    private long indexableBasketballGameCount() {
        return (Long) em.createQuery(
                        "select count(g) from BasketballGame g "
                                + "where g.homeTotal is not null and g.awayTotal is not null")
                .getSingleResult();
    }

    /** Voleybol: yalnız set skoru gelmiş (oynanmış/canlı) maçlar. */
    private long indexableVolleyballGameCount() {
        return (Long) em.createQuery(
                        "select count(g) from VolleyballGame g "
                                + "where g.homeTotal is not null and g.awayTotal is not null")
                .getSingleResult();
    }

    @Transactional(readOnly = true)
    public List<SitemapEntry> page(String type, int page, int size) {
        return switch (type) {
            case "teams" -> namedPage("Team", "/team/", "/takim/", false, page, size);
            case "leagues" -> namedPage("League", "/league/", "/lig/", true, page, size);
            case "players" -> playerPage(page, size);
            case "matches" -> matchesPage(page, size);
            // Basketbol — ayni slug kurallari ({ad}-{id} / home-vs-away-{id}),
            // detay resolver'lar sondaki id'yi cektigi icin isim drift'i sorun degil.
            case "basketball-teams" -> namedPage("BasketballTeam",
                    "/basketball/team/", "/basketbol/takim/", false, page, size);
            case "basketball-leagues" -> namedPage("BasketballLeague",
                    "/basketball/league/", "/basketbol/lig/", true, page, size);
            case "basketball-games" -> basketballGamesPage(page, size);
            // Voleybol — ayni slug kurallari; entity'ler name/nameTr/updatedAt tasir.
            case "volleyball-teams" -> namedPage("VolleyballTeam",
                    "/volleyball/team/", "/voleybol/takim/", false, page, size);
            case "volleyball-leagues" -> namedPage("VolleyballLeague",
                    "/volleyball/league/", "/voleybol/lig/", true, page, size);
            case "volleyball-games" -> volleyballGamesPage(page, size);
            default -> List.of();
        };
    }

    /** Team + League — nameTr var; TR slug Turkce isimden (yoksa EN'den). */
    private List<SitemapEntry> namedPage(
            String entity, String enPrefix, String trPrefix, boolean leagueStyle,
            int page, int size) {
        List<Object[]> rows = em.createQuery(
                        "select e.id, e.name, e.nameTr, e.updatedAt from " + entity
                                + " e order by e.id", Object[].class)
                .setFirstResult(Math.max(0, page) * size)
                .setMaxResults(size)
                .getResultList();
        List<SitemapEntry> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            Long id = (Long) r[0];
            String name = (String) r[1];
            String nameTr = (String) r[2];
            Instant updatedAt = (Instant) r[3];
            if (id == null || name == null || name.isBlank()) continue;
            String trName = (nameTr != null && !nameTr.isBlank()) ? nameTr : name;
            String enSlug = leagueStyle ? SlugUtil.leagueSlug(name, id) : SlugUtil.slugify(name) + "-" + id;
            String trSlug = leagueStyle ? SlugUtil.leagueSlug(trName, id) : SlugUtil.slugify(trName) + "-" + id;
            out.add(new SitemapEntry(enPrefix + enSlug, trPrefix + trSlug, updatedAt));
        }
        return out;
    }

    /** Oyuncu — nameTr yok; slug ayni, sadece dil oneki farkli. */
    private List<SitemapEntry> playerPage(int page, int size) {
        List<Object[]> rows = em.createQuery(
                        "select e.id, e.name, e.updatedAt from Player e"
                                + PLAYER_PLACEHOLDER_FILTER + "order by e.id",
                        Object[].class)
                .setFirstResult(Math.max(0, page) * size)
                .setMaxResults(size)
                .getResultList();
        List<SitemapEntry> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            Long id = (Long) r[0];
            String name = (String) r[1];
            Instant updatedAt = (Instant) r[2];
            if (id == null || name == null || name.isBlank()) continue;
            String slug = SlugUtil.slugify(name) + "-" + id;
            out.add(new SitemapEntry("/player/" + slug, "/oyuncu/" + slug, updatedAt));
        }
        return out;
    }

    /** Maclar — slug ev-deplasman-id; TR'de takim Turkce adlari kullanilir. */
    private List<SitemapEntry> matchesPage(int page, int size) {
        List<Object[]> rows = em.createQuery(
                        "select f.id, f.homeTeam.name, f.homeTeam.nameTr, "
                                + "f.awayTeam.name, f.awayTeam.nameTr, f.updatedAt "
                                + "from Fixture f "
                                // SEO: içeriksiz (skoru gelmemiş) maçlar sitemap'e girmez.
                                + "where f.homeGoals is not null and f.awayGoals is not null "
                                + "order by f.id", Object[].class)
                .setFirstResult(Math.max(0, page) * size)
                .setMaxResults(size)
                .getResultList();
        List<SitemapEntry> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            Long id = (Long) r[0];
            String home = (String) r[1];
            String homeTr = (String) r[2];
            String away = (String) r[3];
            String awayTr = (String) r[4];
            Instant updatedAt = (Instant) r[5];
            if (id == null || home == null || away == null) continue;
            String hTr = (homeTr != null && !homeTr.isBlank()) ? homeTr : home;
            String aTr = (awayTr != null && !awayTr.isBlank()) ? awayTr : away;
            out.add(new SitemapEntry(
                    "/match/" + SlugUtil.fixtureSlug(home, away, id),
                    "/mac/" + SlugUtil.fixtureSlug(hTr, aTr, id),
                    updatedAt));
        }
        return out;
    }

    /** Basketbol maclari — slug home-vs-away-{id}; TR'de Turkce takim adlari. */
    private List<SitemapEntry> basketballGamesPage(int page, int size) {
        List<Object[]> rows = em.createQuery(
                        "select g.id, g.homeTeam.name, g.homeTeam.nameTr, "
                                + "g.awayTeam.name, g.awayTeam.nameTr, g.lastSyncedAt "
                                + "from BasketballGame g "
                                // SEO: skoru gelmemis (iceriksiz) maclar girmez.
                                + "where g.homeTotal is not null and g.awayTotal is not null "
                                + "order by g.id", Object[].class)
                .setFirstResult(Math.max(0, page) * size)
                .setMaxResults(size)
                .getResultList();
        List<SitemapEntry> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            Long id = (Long) r[0];
            String home = (String) r[1];
            String homeTr = (String) r[2];
            String away = (String) r[3];
            String awayTr = (String) r[4];
            Instant lastmod = (Instant) r[5];
            if (id == null || home == null || away == null) continue;
            String hTr = (homeTr != null && !homeTr.isBlank()) ? homeTr : home;
            String aTr = (awayTr != null && !awayTr.isBlank()) ? awayTr : away;
            out.add(new SitemapEntry(
                    "/basketball/match/" + SlugUtil.gameSlug(home, away, id),
                    "/basketbol/mac/" + SlugUtil.gameSlug(hTr, aTr, id),
                    lastmod));
        }
        return out;
    }

    /** Voleybol maclari — slug home-vs-away-{id}; TR'de Turkce takim adlari. */
    private List<SitemapEntry> volleyballGamesPage(int page, int size) {
        List<Object[]> rows = em.createQuery(
                        "select g.id, g.homeTeam.name, g.homeTeam.nameTr, "
                                + "g.awayTeam.name, g.awayTeam.nameTr, g.lastSyncedAt "
                                + "from VolleyballGame g "
                                // SEO: set skoru gelmemis (iceriksiz) maclar girmez.
                                + "where g.homeTotal is not null and g.awayTotal is not null "
                                + "order by g.id", Object[].class)
                .setFirstResult(Math.max(0, page) * size)
                .setMaxResults(size)
                .getResultList();
        List<SitemapEntry> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            Long id = (Long) r[0];
            String home = (String) r[1];
            String homeTr = (String) r[2];
            String away = (String) r[3];
            String awayTr = (String) r[4];
            Instant lastmod = (Instant) r[5];
            if (id == null || home == null || away == null) continue;
            String hTr = (homeTr != null && !homeTr.isBlank()) ? homeTr : home;
            String aTr = (awayTr != null && !awayTr.isBlank()) ? awayTr : away;
            out.add(new SitemapEntry(
                    "/volleyball/match/" + SlugUtil.gameSlug(home, away, id),
                    "/voleybol/mac/" + SlugUtil.gameSlug(hTr, aTr, id),
                    lastmod));
        }
        return out;
    }
}
