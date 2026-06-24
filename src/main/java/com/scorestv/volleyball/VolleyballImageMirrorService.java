package com.scorestv.volleyball;

import com.scorestv.football.image.ImageMirrorService;
import com.scorestv.volleyball.domain.VolleyballLeague;
import com.scorestv.volleyball.domain.VolleyballLeagueRepository;
import com.scorestv.volleyball.domain.VolleyballTeam;
import com.scorestv.volleyball.domain.VolleyballTeamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
}
