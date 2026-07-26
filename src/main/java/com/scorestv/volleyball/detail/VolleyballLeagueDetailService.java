package com.scorestv.volleyball.detail;

import com.scorestv.common.ApiException;
import com.scorestv.common.SlugUtil;
import com.scorestv.storage.MinioStorageService;
import com.scorestv.volleyball.VolleyballMessages;
import com.scorestv.volleyball.domain.VolleyballGame;
import com.scorestv.volleyball.domain.VolleyballGameRepository;
import com.scorestv.volleyball.domain.VolleyballLeague;
import com.scorestv.volleyball.domain.VolleyballLeagueRepository;
import com.scorestv.volleyball.domain.VolleyballTeam;
import com.scorestv.volleyball.seo.VolleyballLeagueDetailSeoBuilder;
import com.scorestv.volleyball.web.dto.VolleyballLeagueDetailResponse;
import com.scorestv.volleyball.web.dto.VolleyballLeagueDetailResponse.GameSummary;
import com.scorestv.volleyball.web.dto.VolleyballLeagueDetailResponse.TeamRef;
import com.scorestv.volleyball.web.dto.VolleyballLeagueSeoResponse;
import com.scorestv.volleyball.web.dto.VolleyballStandingsPageResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Voleybol lig detay sayfasi orkestrasyonu — web'in tek istekle sayfayi
 * doldurmasi icin standings + fiksturler + lig meta + SEO'yu birlestirir.
 * Basketbol {@code BasketballLeagueDetailService}'in LEANER esi.
 *
 * <p>Standings kismi {@link VolleyballStandingsPageService}'e delege edilir
 * (sezon cozumu + lazy sync dahil) — mantik tek yerde kalir.
 */
@Service
public class VolleyballLeagueDetailService {

    /** Lig detayinda son/yaklasan mac listesi limiti. */
    private static final int GAMES_LIMIT = 30;

    private final VolleyballLeagueRepository leagueRepo;
    private final VolleyballGameRepository gameRepo;
    private final VolleyballStandingsPageService standingsPage;
    private final VolleyballLeagueDetailSeoBuilder seoBuilder;
    private final MinioStorageService storage;
    private final VolleyballMessages messages;

    public VolleyballLeagueDetailService(VolleyballLeagueRepository leagueRepo,
                                         VolleyballGameRepository gameRepo,
                                         VolleyballStandingsPageService standingsPage,
                                         VolleyballLeagueDetailSeoBuilder seoBuilder,
                                         MinioStorageService storage,
                                         VolleyballMessages messages) {
        this.leagueRepo = leagueRepo;
        this.gameRepo = gameRepo;
        this.standingsPage = standingsPage;
        this.seoBuilder = seoBuilder;
        this.storage = storage;
        this.messages = messages;
    }

    @Transactional
    public VolleyballLeagueDetailResponse getBySlug(String slug, String season, boolean turkish) {
        return build(slug, season, turkish, false);
    }

    @Transactional
    public VolleyballLeagueDetailResponse forceRefresh(String slug, String season, boolean turkish) {
        return build(slug, season, turkish, true);
    }

    private VolleyballLeagueDetailResponse build(String slug, String season,
                                                 boolean turkish, boolean force) {
        // Standings servisi slug cozumu + sezon normalize + lazy sync yapar;
        // leagueId ve cozulmus sezonu onun cevabindan aliriz.
        VolleyballStandingsPageResponse standings = force
                ? standingsPage.forceRefresh(slug, season, turkish)
                : standingsPage.getBySlug(slug, season, turkish);

        VolleyballLeague league = leagueRepo.findById(standings.leagueId())
                .orElseThrow(() -> ApiException.notFound("Lig bulunamadi"));

        String selectedSeason = standings.season();
        List<GameSummary> recent = selectedSeason == null ? List.of()
                : mapGames(gameRepo.findRecentByLeagueSeason(
                        league.getId(), selectedSeason, PageRequest.of(0, GAMES_LIMIT)), turkish);
        List<GameSummary> upcoming = selectedSeason == null ? List.of()
                : mapGames(gameRepo.findUpcomingByLeagueSeason(
                        league.getId(), selectedSeason, PageRequest.of(0, GAMES_LIMIT)), turkish);

        String displayCountry = turkish && league.getCountryNameTr() != null
                && !league.getCountryNameTr().isBlank()
                ? league.getCountryNameTr() : league.getCountryName();
        var country = new VolleyballLeagueDetailResponse.Country(
                displayCountry,
                league.getCountryCode(),
                resolveImage(league.getCountryFlagKey(), league.getCountryFlag()));

        VolleyballLeagueSeoResponse seo =
                seoBuilder.build(league, selectedSeason, turkish ? "tr" : "en");

        // URL slug'ini lokalize adla yeniden uret — web canonical redirect'i
        // icin (DB'deki slug EN addan uretilmis olabilir).
        String localizedSlug = SlugUtil.leagueSlug(standings.leagueName(), league.getId());

        return new VolleyballLeagueDetailResponse(
                league.getId(),
                localizedSlug,
                standings.leagueName(),
                messages.leagueType(league.getType(), turkish),
                standings.leagueLogo(),
                country,
                league.getCurrentSeason(),
                selectedSeason,
                standings.availableSeasons(),
                standings.groups(),
                recent,
                upcoming,
                seo);
    }

    private List<GameSummary> mapGames(List<VolleyballGame> games, boolean turkish) {
        List<GameSummary> out = new ArrayList<>(games.size());
        for (VolleyballGame g : games) {
            TeamRef home = teamRef(g.getHomeTeam(), turkish);
            TeamRef away = teamRef(g.getAwayTeam(), turkish);
            String gameSlug = (home != null && away != null)
                    ? SlugUtil.gameSlug(home.name(), away.name(), g.getId())
                    : null;
            out.add(new GameSummary(
                    g.getId(),
                    gameSlug,
                    g.getStartAt(),
                    g.getStatusShort(),
                    messages.statusText(g.getStatusShort(), g.getStatusLong(), turkish),
                    home,
                    away,
                    g.getHomeTotal(),
                    g.getAwayTotal(),
                    g.getWeek()));
        }
        return out;
    }

    private TeamRef teamRef(VolleyballTeam t, boolean turkish) {
        if (t == null) return null;
        String name = turkish && t.getNameTr() != null && !t.getNameTr().isBlank()
                ? t.getNameTr() : t.getName();
        return new TeamRef(t.getId(), name, resolveImage(t.getLogoKey(), t.getLogo()));
    }

    /** MinIO key varsa CDN URL'i, yoksa ham URL. */
    private String resolveImage(String key, String fallback) {
        if (key != null && !key.isBlank()) return storage.publicUrl(key);
        return fallback;
    }
}
