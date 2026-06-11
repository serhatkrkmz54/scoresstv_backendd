package com.scorestv.basketball;

import com.scorestv.basketball.domain.BasketballLeague;
import com.scorestv.basketball.domain.BasketballLeagueRepository;
import com.scorestv.basketball.domain.BasketballTeam;
import com.scorestv.basketball.domain.BasketballTeamRepository;
import com.scorestv.football.image.ImageMirrorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

    public BasketballImageMirrorService(ImageMirrorService mirror,
                                        BasketballTeamRepository teamRepo,
                                        BasketballLeagueRepository leagueRepo) {
        this.mirror = mirror;
        this.teamRepo = teamRepo;
        this.leagueRepo = leagueRepo;
    }

    public int mirrorAll() {
        int t = mirrorTeamLogos();
        int l = mirrorLeagueLogos();
        int f = mirrorLeagueFlags();
        log.info("Basketbol image mirror: {} takım, {} lig, {} bayrak", t, l, f);
        return t + l + f;
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
}
