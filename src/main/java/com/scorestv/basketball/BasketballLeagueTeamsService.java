package com.scorestv.basketball;

import com.scorestv.basketball.domain.BasketballLeague;
import com.scorestv.basketball.domain.BasketballLeagueRepository;
import com.scorestv.basketball.domain.BasketballTeam;
import com.scorestv.basketball.domain.BasketballTeamLeagueSeasonRepository;
import com.scorestv.basketball.domain.BasketballTeamRepository;
import com.scorestv.basketball.web.dto.BasketballLeagueTeamView;
import com.scorestv.storage.MinioStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bir basketbol liginin takım listesini hafif DTO olarak döner. Mobile
 * onboarding'in accordion'unda lig açılınca o ligde oynayan takımları
 * gösterir; kullanıcı favori takımı buradan seçer.
 *
 * <p><b>2-katmanlı kaynak hiyerarşisi</b> (futbol {@code LeagueTeamsService}
 * paterniyle aynı):
 * <ol>
 *   <li><b>Junction tablo</b> ({@code basketball_team_league_seasons}) —
 *       {@code /teams?league=X&season=Y} API sonucundan kalıcı kayıt; TAM
 *       kadro, sezon başında maç yokken bile dolu</li>
 *   <li><b>Games fallback</b> ({@code basketball_games} DISTINCT takımlar)
 *       — junction boşsa kullanılır; partial olabilir ama yine de bir şey
 *       gösterir</li>
 * </ol>
 *
 * <p>Junction boşken ARKA PLANDA {@link BasketballTeamSyncService#syncIfMissing}
 * tetiklenir; bir sonraki çağrıda dolu liste gelir.
 */
@Service
public class BasketballLeagueTeamsService {

    private static final Logger log = LoggerFactory.getLogger(BasketballLeagueTeamsService.class);

    private final BasketballLeagueRepository leagueRepository;
    private final BasketballTeamRepository teamRepository;
    private final BasketballTeamLeagueSeasonRepository junctionRepository;
    private final BasketballTeamSyncService teamSyncService;
    private final MinioStorageService storage;

    public BasketballLeagueTeamsService(BasketballLeagueRepository leagueRepository,
                                        BasketballTeamRepository teamRepository,
                                        BasketballTeamLeagueSeasonRepository junctionRepository,
                                        BasketballTeamSyncService teamSyncService,
                                        MinioStorageService storage) {
        this.leagueRepository = leagueRepository;
        this.teamRepository = teamRepository;
        this.junctionRepository = junctionRepository;
        this.teamSyncService = teamSyncService;
        this.storage = storage;
    }

    /**
     * Bir basketbol liginde oynayan takımları döner. Junction tablo (varsa)
     * birinci kaynak; yoksa games üzerinden türetir + arka planda /teams sync
     * tetikler.
     *
     * <p>{@code season} verilmezse ligin {@code currentSeason}'u kullanılır.
     */
    @Transactional
    public List<BasketballLeagueTeamView> getTeams(Long leagueId, String season, boolean turkish) {
        String resolvedSeason = season;
        if (resolvedSeason == null || resolvedSeason.isBlank()) {
            BasketballLeague league = leagueRepository.findById(leagueId).orElse(null);
            resolvedSeason = league != null ? league.getCurrentSeason() : null;
        }

        // 1) Junction katmanı — kanonik kaynak.
        if (resolvedSeason != null && !resolvedSeason.isBlank()) {
            List<Long> ids = junctionRepository.findTeamIdsByLeagueAndSeason(
                    leagueId, resolvedSeason);
            if (!ids.isEmpty()) {
                Map<Long, BasketballTeam> byId = new HashMap<>();
                for (BasketballTeam t : teamRepository.findAllById(ids)) {
                    byId.put(t.getId(), t);
                }
                return ids.stream()
                        .map(byId::get)
                        .filter(t -> t != null)
                        .map(t -> toView(t, turkish))
                        .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                        .toList();
            }
            // 2) Junction boş — arka planda dolduralım (caller'a hemen fallback dön).
            // Senkron çağrı: hızlı (1 API call); ana thread'de 1-2 sn duraklayabilir.
            // Onboarding'de kabul edilebilir bir dur (kullanıcı bir kez yaşar);
            // alternatif @Async background — caller boş liste döner ve refresh
            // gerekir. Burada senkronu seçtik: ilk açışta hemen dolu cevap olsun.
            try {
                int n = teamSyncService.syncIfMissing(leagueId, resolvedSeason);
                if (n > 0) {
                    // Junction artık dolu — junction'dan tekrar oku.
                    List<Long> freshIds = junctionRepository.findTeamIdsByLeagueAndSeason(
                            leagueId, resolvedSeason);
                    Map<Long, BasketballTeam> byId = new HashMap<>();
                    for (BasketballTeam t : teamRepository.findAllById(freshIds)) {
                        byId.put(t.getId(), t);
                    }
                    return freshIds.stream()
                            .map(byId::get)
                            .filter(t -> t != null)
                            .map(t -> toView(t, turkish))
                            .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                            .toList();
                }
            } catch (Exception e) {
                log.warn("Basketbol getTeams junction lazy sync hata league={} season={}: {}",
                        leagueId, resolvedSeason, e.toString());
            }
        }

        // 3) Son çare: games fallback (partial olabilir ama hiç yoktan iyidir).
        List<BasketballTeam> teams = teamRepository.findTeamsInLeague(leagueId, resolvedSeason);
        return teams.stream().map(t -> toView(t, turkish)).toList();
    }

    private BasketballLeagueTeamView toView(BasketballTeam t, boolean turkish) {
        String name = (turkish && t.getNameTr() != null && !t.getNameTr().isBlank())
                ? t.getNameTr() : t.getName();
        String nameTr = (t.getNameTr() != null && !t.getNameTr().isBlank())
                ? t.getNameTr() : t.getName();
        String logo = t.getLogoKey() != null
                ? storage.publicUrl(t.getLogoKey())
                : t.getLogo();
        return new BasketballLeagueTeamView(
                t.getId(),
                name,
                nameTr,
                shortCode(name),
                logo);
    }

    /** İlk kelimenin ilk 3 harfi (uppercase) — basit monogram. */
    private static String shortCode(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        String first = parts[0];
        int take = Math.min(first.length(), 3);
        return first.substring(0, take).toUpperCase();
    }
}
