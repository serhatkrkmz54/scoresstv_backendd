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
 * id + name + updatedAt projeksiyonu (50k+ oyuncu icin ucuz), sayfali.
 *
 * <p>URL formatlari frontend ile birebir:
 * <ul>
 *   <li>takim:  {@code /team/{slug}-{id}}</li>
 *   <li>oyuncu: {@code /player/{slug}-{id}}</li>
 *   <li>lig:    {@code /league/{slug}-{id}}</li>
 * </ul>
 */
@Service
public class SitemapService {

    @PersistenceContext
    private EntityManager em;

    /** Sayfa sayisini hesaplamak icin entity sayilari. */
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

    /** type: teams | players | leagues | matches — path + lastmod listesi, sayfali. */
    @Transactional(readOnly = true)
    public List<SitemapEntry> page(String type, int page, int size) {
        if ("matches".equals(type)) {
            return matchesPage(page, size);
        }
        String entity;
        String prefix;
        boolean leagueStyle;
        switch (type) {
            case "teams" -> { entity = "Team"; prefix = "/team/"; leagueStyle = false; }
            case "players" -> { entity = "Player"; prefix = "/player/"; leagueStyle = false; }
            case "leagues" -> { entity = "League"; prefix = "/league/"; leagueStyle = true; }
            default -> { return List.of(); }
        }

        List<Object[]> rows = em.createQuery(
                        "select e.id, e.name, e.updatedAt from " + entity
                                + " e order by e.id", Object[].class)
                .setFirstResult(Math.max(0, page) * size)
                .setMaxResults(size)
                .getResultList();

        List<SitemapEntry> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            Long id = (Long) r[0];
            String name = (String) r[1];
            Instant updatedAt = (Instant) r[2];
            if (id == null || name == null || name.isBlank()) continue;
            String slug = leagueStyle
                    ? SlugUtil.leagueSlug(name, id)
                    : SlugUtil.slugify(name) + "-" + id;
            out.add(new SitemapEntry(prefix + slug, updatedAt));
        }
        return out;
    }

    /** Maclar — slug ev-deplasman-id (SlugUtil.fixtureSlug ile birebir). */
    private List<SitemapEntry> matchesPage(int page, int size) {
        List<Object[]> rows = em.createQuery(
                        "select f.id, f.homeTeam.name, f.awayTeam.name, f.updatedAt "
                                + "from Fixture f order by f.id", Object[].class)
                .setFirstResult(Math.max(0, page) * size)
                .setMaxResults(size)
                .getResultList();
        List<SitemapEntry> out = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            Long id = (Long) r[0];
            String home = (String) r[1];
            String away = (String) r[2];
            Instant updatedAt = (Instant) r[3];
            if (id == null || home == null || away == null) continue;
            out.add(new SitemapEntry("/match/" + SlugUtil.fixtureSlug(home, away, id), updatedAt));
        }
        return out;
    }
}
