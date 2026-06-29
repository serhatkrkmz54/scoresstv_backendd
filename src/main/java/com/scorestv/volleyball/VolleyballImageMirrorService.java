package com.scorestv.volleyball;

import com.scorestv.football.image.ImageMirrorService;
import com.scorestv.football.image.PlaceholderCandidate;
import com.scorestv.volleyball.domain.VolleyballLeague;
import com.scorestv.volleyball.domain.VolleyballLeagueRepository;
import com.scorestv.volleyball.domain.VolleyballTeam;
import com.scorestv.volleyball.domain.VolleyballTeamRepository;
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
 * Voleybol logo/bayrak aynalama — football'la AYNI altyapiyi ({@link
 * ImageMirrorService}) kullanir. Voleybolda oyuncu YOK; sadece takim/lig/bayrak.
 *
 * <p>{@code @Transactional} YOK — her {@code save} kendi isleminde commit'lenir.
 */
@Service
public class VolleyballImageMirrorService {

    private static final Logger log = LoggerFactory.getLogger(VolleyballImageMirrorService.class);

    private final ImageMirrorService mirror;
    private final VolleyballTeamRepository teamRepo;
    private final VolleyballLeagueRepository leagueRepo;

    public VolleyballImageMirrorService(ImageMirrorService mirror,
                                        VolleyballTeamRepository teamRepo,
                                        VolleyballLeagueRepository leagueRepo) {
        this.mirror = mirror;
        this.teamRepo = teamRepo;
        this.leagueRepo = leagueRepo;
    }

    public int mirrorAll() {
        int t = mirrorTeamLogos();
        int l = mirrorLeagueLogos();
        int f = mirrorLeagueFlags();
        log.info("Voleybol image mirror: {} takim, {} lig, {} bayrak", t, l, f);
        return t + l + f;
    }

    public int mirrorTeamLogos() {
        int n = 0;
        Set<Long> attempted = new HashSet<>();
        while (true) {
            List<VolleyballTeam> batch = teamRepo.findTop200ByLogoKeyIsNullAndLogoIsNotNull();
            batch.removeIf(x -> attempted.contains(x.getId()));
            if (batch.isEmpty()) break;
            for (VolleyballTeam team : batch) {
                attempted.add(team.getId());
                String key = mirror.mirrorExternal(team.getLogo(), "volleyball-teams", team.getId());
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
            List<VolleyballLeague> batch = leagueRepo.findTop200ByLogoKeyIsNullAndLogoIsNotNull();
            batch.removeIf(x -> attempted.contains(x.getId()));
            if (batch.isEmpty()) break;
            for (VolleyballLeague lg : batch) {
                attempted.add(lg.getId());
                String key = mirror.mirrorExternal(lg.getLogo(), "volleyball-leagues", lg.getId());
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
            List<VolleyballLeague> batch =
                    leagueRepo.findTop200ByCountryFlagKeyIsNullAndCountryFlagIsNotNull();
            batch.removeIf(x -> attempted.contains(x.getId()));
            if (batch.isEmpty()) break;
            for (VolleyballLeague lg : batch) {
                attempted.add(lg.getId());
                String key = mirror.mirrorExternal(lg.getCountryFlag(), "volleyball-flags", lg.getId());
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
     * Voleybol placeholder hash adaylarini KESFEDER — futbol/basketbolunkinden
     * FARKLI olabilir. Ornek takim+lig+bayrak URL'lerini {@link
     * ImageMirrorService#detectFromUrls} ile hash'leyip en sik tekrar edeni doner.
     */
    public List<PlaceholderCandidate> detectPlaceholders(int sample) {
        int budget = Math.max(1, sample);
        List<String> urls = new ArrayList<>();
        for (VolleyballTeam t : teamRepo.findAll(PageRequest.of(0, budget))) {
            if (t.getLogo() != null) urls.add(t.getLogo());
        }
        for (VolleyballLeague lg : leagueRepo.findAll(PageRequest.of(0, budget))) {
            if (lg.getLogo() != null) urls.add(lg.getLogo());
            if (lg.getCountryFlag() != null) urls.add(lg.getCountryFlag());
        }
        List<PlaceholderCandidate> out = mirror.detectFromUrls(urls);
        log.info("Voleybol placeholder kesfi: {} aday hash (sample={}).",
                out.size(), budget);
        return out;
    }

    /**
     * MEVCUT placeholder'lari temizler (takim/lig/bayrak): key'i dolu varligin
     * gorselini kaynaktan yeniden indirip hash kontrol eder; placeholder ise
     * MinIO nesnesini siler ve url+key'i null'lar. Asenkron — loglardan izle.
     */
    @Async
    public void purgePlaceholdersAsync() {
        if (!mirror.placeholderFilterEnabled()) {
            log.warn("Voleybol purge: IMAGE_PLACEHOLDER_SHA256 bos — atlandi.");
            return;
        }
        log.info("Voleybol placeholder temizligi basladi.");
        int removed = 0;

        int page = 0;
        while (true) {
            var slice = teamRepo.findAll(PageRequest.of(page, 200));
            if (slice.isEmpty()) break;
            for (VolleyballTeam t : slice) {
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
            for (VolleyballLeague lg : slice) {
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

        log.info("Voleybol placeholder temizligi bitti: {} kayit kaldirildi.", removed);
    }
}
