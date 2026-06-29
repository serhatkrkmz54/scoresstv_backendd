package com.scorestv.basketball;

import com.scorestv.basketball.domain.BasketballLeague;
import com.scorestv.basketball.domain.BasketballLeagueRepository;
import com.scorestv.basketball.domain.BasketballPlayer;
import com.scorestv.basketball.domain.BasketballPlayerRepository;
import com.scorestv.basketball.domain.BasketballTeam;
import com.scorestv.basketball.domain.BasketballTeamRepository;
import com.scorestv.football.image.ImageMirrorService;
import com.scorestv.football.image.PlaceholderCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Basketbol logo/bayrak aynalama — football'la AYNI altyapıyı ({@link
 * ImageMirrorService}) kullanır: API görselini indirir, placeholder'sa atar,
 * MinIO'ya koyar ve key'i entity'ye yazar. Serving CDN URL'ini key'den kurar.
 *
 * <p>{@code @Transactional} YOK — her {@code save} kendi işleminde commit'lenir;
 * yüzlerce görsel indirilirken uzun transaction/bağlantı tutulmaz.
 */
@Service
public class BasketballImageMirrorService {

    private static final Logger log = LoggerFactory.getLogger(BasketballImageMirrorService.class);

    private final ImageMirrorService mirror;
    private final BasketballTeamRepository teamRepo;
    private final BasketballLeagueRepository leagueRepo;
    private final BasketballPlayerRepository playerRepo;

    public BasketballImageMirrorService(ImageMirrorService mirror,
                                        BasketballTeamRepository teamRepo,
                                        BasketballLeagueRepository leagueRepo,
                                        BasketballPlayerRepository playerRepo) {
        this.mirror = mirror;
        this.teamRepo = teamRepo;
        this.leagueRepo = leagueRepo;
        this.playerRepo = playerRepo;
    }

    public int mirrorAll() {
        int t = mirrorTeamLogos();
        int l = mirrorLeagueLogos();
        int f = mirrorLeagueFlags();
        int p = mirrorPlayerPhotos();
        log.info("Basketbol image mirror: {} takım, {} lig, {} bayrak, {} oyuncu", t, l, f, p);
        return t + l + f + p;
    }

    public int mirrorTeamLogos() {
        int n = 0;
        Set<Long> attempted = new HashSet<>();
        while (true) {
            List<BasketballTeam> batch = teamRepo.findTop200ByLogoKeyIsNullAndLogoIsNotNull();
            batch.removeIf(x -> attempted.contains(x.getId()));
            if (batch.isEmpty()) break;
            for (BasketballTeam team : batch) {
                attempted.add(team.getId());
                String key = mirror.mirrorExternal(team.getLogo(), "basketball-teams", team.getId());
                if (mirror.isPlaceholderKey(key)) {
                    team.setLogo(null);
                    teamRepo.save(team);
                } else if (key != null) {
                    team.setLogoKey(key);
                    teamRepo.save(team);
                    n++;
                }
            }
        }
        return n;
    }

    public int mirrorLeagueLogos() {
        int n = 0;
        Set<Long> attempted = new HashSet<>();
        while (true) {
            List<BasketballLeague> batch = leagueRepo.findTop200ByLogoKeyIsNullAndLogoIsNotNull();
            batch.removeIf(x -> attempted.contains(x.getId()));
            if (batch.isEmpty()) break;
            for (BasketballLeague lg : batch) {
                attempted.add(lg.getId());
                String key = mirror.mirrorExternal(lg.getLogo(), "basketball-leagues", lg.getId());
                if (mirror.isPlaceholderKey(key)) {
                    lg.setLogo(null);
                    leagueRepo.save(lg);
                } else if (key != null) {
                    lg.setLogoKey(key);
                    leagueRepo.save(lg);
                    n++;
                }
            }
        }
        return n;
    }

    public int mirrorLeagueFlags() {
        int n = 0;
        Set<Long> attempted = new HashSet<>();
        while (true) {
            List<BasketballLeague> batch =
                    leagueRepo.findTop200ByCountryFlagKeyIsNullAndCountryFlagIsNotNull();
            batch.removeIf(x -> attempted.contains(x.getId()));
            if (batch.isEmpty()) break;
            for (BasketballLeague lg : batch) {
                attempted.add(lg.getId());
                String key = mirror.mirrorExternal(lg.getCountryFlag(), "basketball-flags", lg.getId());
                if (mirror.isPlaceholderKey(key)) {
                    lg.setCountryFlag(null);
                    leagueRepo.save(lg);
                } else if (key != null) {
                    lg.setCountryFlagKey(key);
                    leagueRepo.save(lg);
                    n++;
                }
            }
        }
        return n;
    }

    /**
     * Oyuncu fotograflarini MinIO'ya aynalar. Profile sync ({@link
     * BasketballPlayerUpserter#upsertFromProfile}) {@code photo} URL'ini
     * yazar, {@code photoKey} bos kalir; bu metod ardindan calisip key'i
     * doldurur. Placeholder/cozulemeyen URL'ler null'a cevrilir.
     *
     * <p>Daily image mirror job tarafindan covered ligler senkronu sonrasi
     * cagrilir.
     */
    public int mirrorPlayerPhotos() {
        int n = 0;
        Set<Long> attempted = new HashSet<>();
        while (true) {
            List<BasketballPlayer> batch =
                    playerRepo.findTop200ByPhotoKeyIsNullAndPhotoIsNotNull();
            batch.removeIf(x -> attempted.contains(x.getId()));
            if (batch.isEmpty()) break;
            for (BasketballPlayer p : batch) {
                attempted.add(p.getId());
                String key = mirror.mirrorExternal(p.getPhoto(), "basketball-players", p.getId());
                if (mirror.isPlaceholderKey(key)) {
                    p.setPhoto(null);
                    playerRepo.save(p);
                } else if (key != null) {
                    p.setPhotoKey(key);
                    playerRepo.save(p);
                    n++;
                }
            }
        }
        return n;
    }

    /**
     * Basketbol placeholder hash adaylarini KESFEDER — futbolunkinden FARKLI
     * olabilir (api-sports spor basina ayri placeholder verebilir). Ornek
     * takim+lig+bayrak+oyuncu URL'lerini {@link ImageMirrorService#detectFromUrls}
     * ile hash'leyip en sik tekrar edeni doner.
     */
    public List<PlaceholderCandidate> detectPlaceholders(int sample) {
        int budget = Math.max(1, sample);
        List<String> urls = new ArrayList<>();
        for (BasketballTeam t : teamRepo.findAll(PageRequest.of(0, budget))) {
            if (t.getLogo() != null) urls.add(t.getLogo());
        }
        for (BasketballLeague lg : leagueRepo.findAll(PageRequest.of(0, budget))) {
            if (lg.getLogo() != null) urls.add(lg.getLogo());
            if (lg.getCountryFlag() != null) urls.add(lg.getCountryFlag());
        }
        for (BasketballPlayer p : playerRepo.findAll(PageRequest.of(0, budget))) {
            if (p.getPhoto() != null) urls.add(p.getPhoto());
        }
        List<PlaceholderCandidate> out = mirror.detectFromUrls(urls);
        log.info("Basketbol placeholder kesfi: {} aday hash (sample={}).",
                out.size(), budget);
        return out;
    }

    /**
     * MEVCUT placeholder'lari temizler (takim/lig/bayrak/oyuncu): key'i dolu
     * varligin gorselini kaynaktan yeniden indirip hash kontrol eder; placeholder
     * ise MinIO nesnesini siler ve url+key'i null'lar (mobil fallback gosterir).
     * Asenkron — admin endpoint tetikler, ilerleme loglardan izlenir.
     */
    @Async
    public void purgePlaceholdersAsync() {
        if (!mirror.placeholderFilterEnabled()) {
            log.warn("Basketbol purge: IMAGE_PLACEHOLDER_SHA256 bos — atlandi.");
            return;
        }
        log.info("Basketbol placeholder temizligi basladi.");
        int removed = 0;

        int page = 0;
        while (true) {
            var slice = teamRepo.findAll(PageRequest.of(page, 200));
            if (slice.isEmpty()) break;
            for (BasketballTeam t : slice) {
                if (t.getLogoKey() != null && t.getLogo() != null
                        && mirror.isPlaceholderAtUrl(t.getLogo())) {
                    mirror.deleteObject(t.getLogoKey());
                    t.setLogoKey(null);
                    t.setLogo(null);
                    teamRepo.save(t);
                    removed++;
                }
            }
            if (!slice.hasNext()) break;
            page++;
        }

        page = 0;
        while (true) {
            var slice = leagueRepo.findAll(PageRequest.of(page, 200));
            if (slice.isEmpty()) break;
            for (BasketballLeague lg : slice) {
                boolean changed = false;
                if (lg.getLogoKey() != null && lg.getLogo() != null
                        && mirror.isPlaceholderAtUrl(lg.getLogo())) {
                    mirror.deleteObject(lg.getLogoKey());
                    lg.setLogoKey(null);
                    lg.setLogo(null);
                    changed = true;
                    removed++;
                }
                if (lg.getCountryFlagKey() != null && lg.getCountryFlag() != null
                        && mirror.isPlaceholderAtUrl(lg.getCountryFlag())) {
                    mirror.deleteObject(lg.getCountryFlagKey());
                    lg.setCountryFlagKey(null);
                    lg.setCountryFlag(null);
                    changed = true;
                    removed++;
                }
                if (changed) leagueRepo.save(lg);
            }
            if (!slice.hasNext()) break;
            page++;
        }

        page = 0;
        while (true) {
            var slice = playerRepo.findAll(PageRequest.of(page, 200));
            if (slice.isEmpty()) break;
            for (BasketballPlayer p : slice) {
                if (p.getPhotoKey() != null && p.getPhoto() != null
                        && mirror.isPlaceholderAtUrl(p.getPhoto())) {
                    mirror.deleteObject(p.getPhotoKey());
                    p.setPhotoKey(null);
                    p.setPhoto(null);
                    playerRepo.save(p);
                    removed++;
                }
            }
            if (!slice.hasNext()) break;
            page++;
        }

        log.info("Basketbol placeholder temizligi bitti: {} kayit kaldirildi.", removed);
    }
}
