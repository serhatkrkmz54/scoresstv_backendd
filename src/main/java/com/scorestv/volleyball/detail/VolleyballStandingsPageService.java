package com.scorestv.volleyball.detail;

import com.scorestv.common.ApiException;
import com.scorestv.common.SlugUtil;
import com.scorestv.storage.MinioStorageService;
import com.scorestv.volleyball.VolleyballMessages;
import com.scorestv.volleyball.VolleyballSeasonNormalizer;
import com.scorestv.volleyball.VolleyballStandingsSyncService;
import com.scorestv.volleyball.domain.VolleyballLeague;
import com.scorestv.volleyball.domain.VolleyballLeagueRepository;
import com.scorestv.volleyball.domain.VolleyballStanding;
import com.scorestv.volleyball.domain.VolleyballStandingRepository;
import com.scorestv.volleyball.web.dto.VolleyballStandingsPageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Voleybol lig puan durumu sayfasi servisi — slug bazli. DB bosken lazy sync
 * tetikler. Basketbol standings page'in voleybol esi, LEANER.
 */
@Service
public class VolleyballStandingsPageService {

    private static final Logger log = LoggerFactory.getLogger(VolleyballStandingsPageService.class);

    private final VolleyballLeagueRepository leagueRepo;
    private final VolleyballStandingRepository standingRepo;
    private final VolleyballStandingsSyncService standingsSync;
    private final MinioStorageService storage;
    private final VolleyballMessages messages;

    public VolleyballStandingsPageService(VolleyballLeagueRepository leagueRepo,
                                          VolleyballStandingRepository standingRepo,
                                          VolleyballStandingsSyncService standingsSync,
                                          MinioStorageService storage,
                                          VolleyballMessages messages) {
        this.leagueRepo = leagueRepo;
        this.standingRepo = standingRepo;
        this.standingsSync = standingsSync;
        this.storage = storage;
        this.messages = messages;
    }

    @Transactional
    public VolleyballStandingsPageResponse getBySlug(String slug, String season, boolean turkish) {
        Long leagueId = SlugUtil.extractLeagueId(slug);
        if (leagueId == null) {
            VolleyballLeague bySlug = leagueRepo.findBySlug(slug).orElse(null);
            leagueId = bySlug != null ? bySlug.getId() : null;
        }
        if (leagueId == null) throw ApiException.notFound("Lig bulunamadi");
        return build(leagueId, season, turkish, false);
    }

    @Transactional
    public VolleyballStandingsPageResponse forceRefresh(String slug, String season, boolean turkish) {
        Long leagueId = SlugUtil.extractLeagueId(slug);
        if (leagueId == null) {
            VolleyballLeague bySlug = leagueRepo.findBySlug(slug).orElse(null);
            leagueId = bySlug != null ? bySlug.getId() : null;
        }
        if (leagueId == null) throw ApiException.notFound("Lig bulunamadi");
        return build(leagueId, season, turkish, true);
    }

    private VolleyballStandingsPageResponse build(Long leagueId, String season,
                                                  boolean turkish, boolean force) {
        VolleyballLeague league = leagueRepo.findById(leagueId)
                .orElseThrow(() -> ApiException.notFound("Lig bulunamadi"));

        // Sezon cozumu: verilmezse currentSeason; sonra normalize et.
        String resolved = (season == null || season.isBlank())
                ? league.getCurrentSeason() : season;
        if (resolved != null) {
            resolved = VolleyballSeasonNormalizer.normalize(resolved, league.getSeasonsJson());
        }

        List<String> availableSeasons = standingRepo.findDistinctSeasonsByLeagueId(leagueId);
        if (resolved == null && !availableSeasons.isEmpty()) {
            resolved = availableSeasons.get(0);
        }

        // Lazy sync: DB bos veya force.
        if (resolved != null) {
            long count = standingRepo.countByLeagueIdAndSeason(leagueId, resolved);
            if (count == 0 || force) {
                try {
                    standingsSync.sync(leagueId, resolved);
                } catch (Exception e) {
                    log.warn("Voleybol standings lazy sync hata league={} season={}: {}",
                            leagueId, resolved, e.toString());
                }
            }
        }

        List<VolleyballStanding> rows = resolved == null ? List.of()
                : standingRepo.findByLeagueAndSeason(leagueId, resolved);

        Map<String, List<VolleyballStandingsPageResponse.Row>> byGroup = new LinkedHashMap<>();
        Map<String, String> stageByGroup = new LinkedHashMap<>();
        for (VolleyballStanding s : rows) {
            String gname = s.getGroupName() == null ? "" : s.getGroupName();
            String teamName = turkish && s.getTeam().getNameTr() != null
                    && !s.getTeam().getNameTr().isBlank()
                    ? s.getTeam().getNameTr() : s.getTeam().getName();
            String logo = s.getTeam().getLogoKey() != null
                    ? storage.publicUrl(s.getTeam().getLogoKey())
                    : s.getTeam().getLogo();
            byGroup.computeIfAbsent(gname, k -> new ArrayList<>())
                    .add(new VolleyballStandingsPageResponse.Row(
                            s.getPosition(),
                            s.getTeam().getId(),
                            teamName,
                            logo,
                            SlugUtil.teamSlug(teamName, s.getTeam().getId()),
                            s.getGamesPlayed(),
                            s.getWon(),
                            s.getLost(),
                            s.getWonPercentage(),
                            s.getSetsFor(),
                            s.getSetsAgainst(),
                            (s.getSetsFor() != null && s.getSetsAgainst() != null)
                                    ? (s.getSetsFor() - s.getSetsAgainst()) : null,
                            s.getPoints(),
                            s.getForm(),
                            messages.standingDescription(s.getDescription(), turkish)));
            stageByGroup.putIfAbsent(gname, s.getStage());
        }

        List<VolleyballStandingsPageResponse.Group> groups = new ArrayList<>(byGroup.size());
        for (Map.Entry<String, List<VolleyballStandingsPageResponse.Row>> e : byGroup.entrySet()) {
            groups.add(new VolleyballStandingsPageResponse.Group(
                    messages.standingGroupName(e.getKey(), turkish),
                    stageByGroup.get(e.getKey()), e.getValue()));
        }

        String leagueName = turkish && league.getNameTr() != null
                && !league.getNameTr().isBlank() ? league.getNameTr() : league.getName();
        String leagueLogo = league.getLogoKey() != null
                ? storage.publicUrl(league.getLogoKey()) : league.getLogo();

        return new VolleyballStandingsPageResponse(
                leagueId, leagueName, leagueLogo, resolved, availableSeasons, groups);
    }
}
