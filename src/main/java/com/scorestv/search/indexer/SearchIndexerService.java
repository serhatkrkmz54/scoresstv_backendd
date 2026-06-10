package com.scorestv.search.indexer;

import com.scorestv.common.SlugUtil;
import com.scorestv.football.domain.Country;
import com.scorestv.football.domain.CountryRepository;
import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.football.domain.League;
import com.scorestv.football.domain.LeagueRepository;
import com.scorestv.football.domain.Player;
import com.scorestv.football.domain.PlayerRepository;
import com.scorestv.football.domain.Team;
import com.scorestv.football.domain.TeamRepository;
import com.scorestv.search.index.CountryDoc;
import com.scorestv.search.index.CountryDocRepository;
import com.scorestv.search.index.FixtureDoc;
import com.scorestv.search.index.FixtureDocRepository;
import com.scorestv.search.index.LeagueDoc;
import com.scorestv.search.index.LeagueDocRepository;
import com.scorestv.search.index.PlayerDoc;
import com.scorestv.search.index.PlayerDocRepository;
import com.scorestv.search.index.TeamDoc;
import com.scorestv.search.index.TeamDocRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Entity → Doc cevirisi + bulk ES'e yazma.
 *
 * <p><b>Reindex stratejisi:</b> sayfa-sayfa (1000'er kayit) DB'den okur,
 * doc'a cevirir, ES'e bulk yazar. Hafiza guvenli; milyonlarca kayitla bile
 * sorunsuz calisir.
 *
 * <p><b>Incremental update:</b> entity Upserter'larindan cagrilan
 * <code>indexOne(entity)</code> metodlari ile tekil dokuman ES'e yazilir.
 * Upserter'a hook eklemek 2. fazda (saglikli baslangic icin once full reindex).
 *
 * <p><b>scorestv.elasticsearch.enabled=false</b> ise bu bean hic yuklenmez —
 * uygulama ES'siz calismaya devam eder. Bu durumda admin reindex endpoint'i
 * 404 doner (denetim icin uygun).
 */
@Service
@ConditionalOnProperty(prefix = "scorestv.elasticsearch", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SearchIndexerService {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexerService.class);
    private static final int CHUNK = 1000;

    private final TeamRepository teamRepo;
    private final LeagueRepository leagueRepo;
    private final PlayerRepository playerRepo;
    private final CountryRepository countryRepo;
    private final FixtureRepository fixtureRepo;

    private final TeamDocRepository teamDocs;
    private final LeagueDocRepository leagueDocs;
    private final PlayerDocRepository playerDocs;
    private final CountryDocRepository countryDocs;
    private final FixtureDocRepository fixtureDocs;

    @Autowired
    private ElasticsearchOperations esOps;

    public SearchIndexerService(
            TeamRepository teamRepo,
            LeagueRepository leagueRepo,
            PlayerRepository playerRepo,
            CountryRepository countryRepo,
            FixtureRepository fixtureRepo,
            TeamDocRepository teamDocs,
            LeagueDocRepository leagueDocs,
            PlayerDocRepository playerDocs,
            CountryDocRepository countryDocs,
            FixtureDocRepository fixtureDocs) {
        this.teamRepo = teamRepo;
        this.leagueRepo = leagueRepo;
        this.playerRepo = playerRepo;
        this.countryRepo = countryRepo;
        this.fixtureRepo = fixtureRepo;
        this.teamDocs = teamDocs;
        this.leagueDocs = leagueDocs;
        this.playerDocs = playerDocs;
        this.countryDocs = countryDocs;
        this.fixtureDocs = fixtureDocs;
    }

    // ============================================================
    // PUBLIC API — admin endpoint'ten cagrilir
    // ============================================================

    @Async
    public void reindexAllAsync() {
        log.info("ES reindex (ASYNC) basliyor — tum tipler");
        try {
            reindexCountries();
            reindexLeagues();
            reindexTeams();
            reindexPlayers();
            reindexFixtures();
            log.info("ES reindex (ASYNC) tamamlandi");
        } catch (Exception e) {
            log.error("ES reindex hata: {}", e.getMessage(), e);
        }
    }

    /**
     * Tum indeksleri DROP + RECREATE + REINDEX eder. Mapping degisikliklerinin
     * (analyzer, field type, vb.) sahaya yansimasi icin gerekli — ES mevcut
     * indeksin mapping'ini in-place degistirmeyi desteklemez.
     *
     * <p>KULLANIM: analyzer veya field type degisiminden sonra admin endpoint
     * uzerinden cagir. Tum index'ler bos olur ve sifirdan yazilir; arama
     * suresi ~30-60sn kullanilamaz hale gelir.
     */
    @Async
    public void rebuildAllAsync() {
        log.info("ES rebuild (DROP+CREATE+REINDEX) basliyor — tum tipler");
        countryTrByName = null; // taze ulke cevirilerini yeniden yukle
        try {
            dropAndRecreate(com.scorestv.search.index.CountryDoc.class);
            dropAndRecreate(com.scorestv.search.index.LeagueDoc.class);
            dropAndRecreate(com.scorestv.search.index.TeamDoc.class);
            dropAndRecreate(com.scorestv.search.index.PlayerDoc.class);
            dropAndRecreate(com.scorestv.search.index.FixtureDoc.class);
            reindexCountries();
            reindexLeagues();
            reindexTeams();
            reindexPlayers();
            reindexFixtures();
            log.info("ES rebuild tamamlandi");
        } catch (Exception e) {
            log.error("ES rebuild hata: {}", e.getMessage(), e);
        }
    }

    /**
     * Bir index'i sil + bos halde yeniden olustur + mapping uygula. Yeni
     * settings (analyzer/tokenizer/filter) ve field mapping (analyzer per
     * field) bu aksiyonla yansir.
     */
    private void dropAndRecreate(Class<?> docClass) {
        try {
            IndexOperations ops = esOps.indexOps(docClass);
            if (ops.exists()) {
                ops.delete();
                log.info("ES index silindi: {}", docClass.getSimpleName());
            }
            ops.create();
            ops.putMapping(ops.createMapping(docClass));
            log.info("ES index olusturuldu+mapping uygulandi: {}", docClass.getSimpleName());
        } catch (Exception e) {
            log.warn("ES dropAndRecreate hata ({}): {}",
                    docClass.getSimpleName(), e.getMessage());
        }
    }

    /** Index yoksa olustur, varsa dokun. Reindex'ten once cagrilir. */
    private void ensureIndex(Class<?> docClass) {
        try {
            IndexOperations ops = esOps.indexOps(docClass);
            if (!ops.exists()) {
                ops.create();
                ops.putMapping(ops.createMapping(docClass));
                log.info("ES index olusturuldu: {}", docClass.getSimpleName());
            }
        } catch (Exception e) {
            log.warn("ES ensureIndex hata ({}): {}",
                    docClass.getSimpleName(), e.getMessage());
        }
    }

    public long reindexCountries() {
        log.info("ES reindex: countries baslatildi");
        ensureIndex(com.scorestv.search.index.CountryDoc.class);
        long total = 0;
        int page = 0;
        while (true) {
            var slice = countryRepo.findAll(PageRequest.of(page, CHUNK));
            if (slice.isEmpty()) break;
            List<CountryDoc> batch = new ArrayList<>(slice.getSize());
            for (Country c : slice) {
                batch.add(toDoc(c));
            }
            countryDocs.saveAll(batch);
            total += slice.getNumberOfElements();
            if (!slice.hasNext()) break;
            page++;
        }
        log.info("ES reindex: countries tamamlandi, toplam={}", total);
        return total;
    }

    public long reindexLeagues() {
        log.info("ES reindex: leagues baslatildi");
        ensureIndex(com.scorestv.search.index.LeagueDoc.class);
        long total = 0;
        int page = 0;
        while (true) {
            var slice = leagueRepo.findAll(PageRequest.of(page, CHUNK));
            if (slice.isEmpty()) break;
            List<LeagueDoc> batch = new ArrayList<>(slice.getSize());
            for (League l : slice) {
                batch.add(toDoc(l));
            }
            leagueDocs.saveAll(batch);
            total += slice.getNumberOfElements();
            if (!slice.hasNext()) break;
            page++;
        }
        log.info("ES reindex: leagues tamamlandi, toplam={}", total);
        return total;
    }

    public long reindexTeams() {
        log.info("ES reindex: teams baslatildi");
        ensureIndex(com.scorestv.search.index.TeamDoc.class);
        long total = 0;
        int page = 0;
        while (true) {
            var slice = teamRepo.findAll(PageRequest.of(page, CHUNK));
            if (slice.isEmpty()) break;
            List<TeamDoc> batch = new ArrayList<>(slice.getSize());
            for (Team t : slice) {
                batch.add(toDoc(t));
            }
            teamDocs.saveAll(batch);
            total += slice.getNumberOfElements();
            if (!slice.hasNext()) break;
            page++;
        }
        log.info("ES reindex: teams tamamlandi, toplam={}", total);
        return total;
    }

    public long reindexPlayers() {
        log.info("ES reindex: players baslatildi");
        ensureIndex(com.scorestv.search.index.PlayerDoc.class);
        long total = 0;
        int page = 0;
        while (true) {
            var slice = playerRepo.findAll(PageRequest.of(page, CHUNK));
            if (slice.isEmpty()) break;
            List<PlayerDoc> batch = new ArrayList<>(slice.getSize());
            for (Player p : slice) {
                batch.add(toDoc(p));
            }
            playerDocs.saveAll(batch);
            total += slice.getNumberOfElements();
            if (!slice.hasNext()) break;
            page++;
        }
        log.info("ES reindex: players tamamlandi, toplam={}", total);
        return total;
    }

    public long reindexFixtures() {
        log.info("ES reindex: fixtures baslatildi");
        ensureIndex(com.scorestv.search.index.FixtureDoc.class);
        long total = 0;
        int page = 0;
        while (true) {
            var slice = fixtureRepo.findAll(PageRequest.of(page, CHUNK));
            if (slice.isEmpty()) break;
            List<FixtureDoc> batch = new ArrayList<>(slice.getSize());
            for (Fixture f : slice) {
                batch.add(toDoc(f));
            }
            fixtureDocs.saveAll(batch);
            total += slice.getNumberOfElements();
            if (!slice.hasNext()) break;
            page++;
        }
        log.info("ES reindex: fixtures tamamlandi, toplam={}", total);
        return total;
    }

    // ============================================================
    // INCREMENTAL — entity tek tek (Upserter'lar buraya cagiracak)
    // ============================================================

    public void indexTeam(Team t) {
        if (t == null) return;
        try { teamDocs.save(toDoc(t)); }
        catch (Exception e) { log.warn("ES indexTeam fail id={}: {}", t.getId(), e.getMessage()); }
    }

    public void indexLeague(League l) {
        if (l == null) return;
        try { leagueDocs.save(toDoc(l)); }
        catch (Exception e) { log.warn("ES indexLeague fail id={}: {}", l.getId(), e.getMessage()); }
    }

    public void indexPlayer(Player p) {
        if (p == null) return;
        try { playerDocs.save(toDoc(p)); }
        catch (Exception e) { log.warn("ES indexPlayer fail id={}: {}", p.getId(), e.getMessage()); }
    }

    public void indexCountry(Country c) {
        if (c == null) return;
        try { countryDocs.save(toDoc(c)); }
        catch (Exception e) { log.warn("ES indexCountry fail id={}: {}", c.getId(), e.getMessage()); }
    }

    public void indexFixture(Fixture f) {
        if (f == null) return;
        try { fixtureDocs.save(toDoc(f)); }
        catch (Exception e) { log.warn("ES indexFixture fail id={}: {}", f.getId(), e.getMessage()); }
    }

    // ============================================================
    // MAPPERS — entity → doc
    // ============================================================

    private TeamDoc toDoc(Team t) {
        var d = new TeamDoc();
        d.setId(t.getId());
        d.setName(t.getName());
        d.setNameTr(t.getNameTr());
        d.setSlug(SlugUtil.teamSlug(t.getName(), t.getId()));
        d.setCode(t.getCode());
        d.setCountry(t.getCountry());
        d.setCountryTr(countryTrFor(t.getCountry()));
        d.setLogoUrl(t.getLogoUrl());
        d.setNational(t.isNational());
        return d;
    }

    private LeagueDoc toDoc(League l) {
        var d = new LeagueDoc();
        d.setId(l.getId());
        d.setName(l.getName());
        d.setNameTr(l.getNameTr());
        d.setSlug(SlugUtil.leagueSlug(l.getName(), l.getId()));
        d.setCountry(l.getCountryName());
        d.setCountryTr(countryTrFor(l.getCountryName()));
        d.setType(l.getType());
        d.setLogoUrl(l.getLogoUrl());
        d.setFlagUrl(l.getCountryFlagUrl());
        d.setCovered(l.isCovered());
        return d;
    }

    /// Ulke adi (Ingilizce, Team.country / League.countryName) -> Turkce ad
    /// (Country.nameTr). Country tablosu lazy yuklenip cache'lenir; tek
    /// reindex sirasinda her dokuman icin DB'ye gidilmez. rebuildAllAsync
    /// basinda cache sifirlanir (taze ceviriler yansisin).
    private volatile Map<String, String> countryTrByName;

    private String countryTrFor(String englishName) {
        if (englishName == null || englishName.isBlank()) return null;
        Map<String, String> map = countryTrByName;
        if (map == null) {
            map = new HashMap<>();
            for (Country c : countryRepo.findAll()) {
                if (c.getName() != null
                        && c.getNameTr() != null
                        && !c.getNameTr().isBlank()) {
                    map.put(c.getName(), c.getNameTr());
                }
            }
            countryTrByName = map;
        }
        return map.get(englishName);
    }

    private PlayerDoc toDoc(Player p) {
        var d = new PlayerDoc();
        d.setId(p.getId());
        // displayName = "Arda Guler" (firstname+lastname varsa); yoksa kisa "A. Guler".
        // Master Player tablosunda firstname/lastname dolu olan kayitlarda
        // arama "arda" yazinca yakalar (autocomplete_index edge_ngram).
        d.setName(p.getDisplayName());
        d.setFirstName(p.getFirstname());
        d.setLastName(p.getLastname());
        d.setSlug(SlugUtil.playerSlug(p.getFirstname(), p.getLastname(),
                p.getName(), p.getId()));
        d.setNationality(p.getNationality());
        d.setAge(p.getAge());
        d.setPhotoUrl(p.getPhotoUrl());
        // Position + teamId/teamName Player entity'sinde tutulmuyor (squad table
        // ayri); ileride zenginlestirilebilir. Suanlik bos birakilir.
        return d;
    }

    private CountryDoc toDoc(Country c) {
        var d = new CountryDoc();
        d.setId(c.getId());
        d.setName(c.getName());
        d.setNameTr(c.getNameTr());
        d.setCode(c.getCode());
        d.setSlug(SlugUtil.slugify(c.getName()));
        d.setFlagUrl(c.getFlagUrl());
        return d;
    }

    private FixtureDoc toDoc(Fixture f) {
        var d = new FixtureDoc();
        d.setId(f.getId());
        Team home = f.getHomeTeam();
        Team away = f.getAwayTeam();
        if (home != null) {
            d.setHomeTeamId(home.getId());
            d.setHomeTeamName(home.getName());
            // TR adi varsa indexle — dil bilincli arama icin
            if (home.getNameTr() != null && !home.getNameTr().isBlank()) {
                d.setHomeTeamNameTr(home.getNameTr());
            }
        }
        if (away != null) {
            d.setAwayTeamId(away.getId());
            d.setAwayTeamName(away.getName());
            if (away.getNameTr() != null && !away.getNameTr().isBlank()) {
                d.setAwayTeamNameTr(away.getNameTr());
            }
        }
        String hn = home != null ? home.getName() : "";
        String an = away != null ? away.getName() : "";
        d.setMatchup(hn + " - " + an);
        // TR matchup — her iki takimin TR adi (yoksa orijinal) ile birlestir.
        String hnTr = (home != null && home.getNameTr() != null
                && !home.getNameTr().isBlank()) ? home.getNameTr() : hn;
        String anTr = (away != null && away.getNameTr() != null
                && !away.getNameTr().isBlank()) ? away.getNameTr() : an;
        if (!hnTr.equals(hn) || !anTr.equals(an)) {
            d.setMatchupTr(hnTr + " - " + anTr);
        }
        d.setSlug(SlugUtil.fixtureSlug(hn, an, f.getId()));
        if (f.getLeague() != null) {
            d.setLeagueId(f.getLeague().getId());
            d.setLeagueName(f.getLeague().getName());
            if (f.getLeague().getNameTr() != null
                    && !f.getLeague().getNameTr().isBlank()) {
                d.setLeagueNameTr(f.getLeague().getNameTr());
            }
        }
        d.setSeason(f.getSeason());
        d.setKickoff(f.getKickoffAt() != null ? f.getKickoffAt() : null);
        d.setStatusLong(f.getStatusLong());
        d.setStatusShort(f.getStatusShort());
        return d;
    }
}
