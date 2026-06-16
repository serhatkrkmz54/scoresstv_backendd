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
        return Map.of(
                "teams", count("Team"),
                "players", count("Player"),
                "leagues", count("League"),
                "matches", count("Fixture"));
    }

    private long count(String entity) {
        return (Long) em.createQuery("select count(e) from " + entity + " e")
                .getSingleResult();
    }

    @Transactional(readOnly = true)
    public List<SitemapEntry> page(String type, int page, int size) {
        return switch (type) {
            case "teams" -> namedPage("Team", "/team/", "/takim/", false, page, size);
            case "leagues" -> namedPage("League", "/league/", "/lig/", true, page, size);
            case "players" -> playerPage(page, size);
            case "matches" -> matchesPage(page, size);
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
                        "select e.id, e.name, e.updatedAt from Player e order by e.id",
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
                                + "from Fixture f order by f.id", Object[].class)
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
}
