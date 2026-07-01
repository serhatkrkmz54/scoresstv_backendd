package com.scorestv.football.player;

import com.scorestv.common.ApiException;
import com.scorestv.common.SlugUtil;
import com.scorestv.football.FootballCacheNames;
import com.scorestv.football.FootballMessages;
import com.scorestv.football.domain.CountryRepository;
import com.scorestv.football.domain.League;
import com.scorestv.football.domain.LeagueRepository;
import com.scorestv.football.domain.Player;
import com.scorestv.football.domain.PlayerCareerTeam;
import com.scorestv.football.domain.PlayerCareerTeamRepository;
import com.scorestv.football.domain.PlayerRepository;
import com.scorestv.football.domain.PlayerSeasonStat;
import com.scorestv.football.domain.PlayerSeasonStatRepository;
import com.scorestv.football.domain.PlayerSidelined;
import com.scorestv.football.domain.PlayerSidelinedRepository;
import com.scorestv.football.domain.PlayerTrophy;
import com.scorestv.football.domain.PlayerTrophyRepository;
import com.scorestv.football.domain.Team;
import com.scorestv.football.domain.TeamRepository;
import com.scorestv.football.domain.TranslatableName;
import com.scorestv.football.domain.Transfer;
import com.scorestv.football.domain.TransferRepository;
import com.scorestv.football.seo.PlayerDetailSeoBuilder;
import com.scorestv.football.web.dto.PlayerDetailResponse;
import com.scorestv.football.web.dto.PlayerDetailResponse.BirthInfo;
import com.scorestv.football.web.dto.PlayerDetailResponse.CareerTeamView;
import com.scorestv.football.web.dto.PlayerDetailResponse.PlayerSeasonStatView;
import com.scorestv.football.web.dto.PlayerDetailResponse.SidelinedRow;
import com.scorestv.football.web.dto.PlayerDetailResponse.TeamRef;
import com.scorestv.football.web.dto.PlayerDetailResponse.TransferRow;
import com.scorestv.football.web.dto.PlayerDetailResponse.TrophyView;
import com.scorestv.football.web.dto.PlayerSeoResponse;
import com.scorestv.storage.MinioStorageService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Oyuncu detay sayfasinin yanitini ({@link PlayerDetailResponse}) uretir.
 * Lazy sync orkestrasyonu {@link PlayerDetailLazySync}'te; bu servis DB
 * read + transformasyon yapar.
 *
 * <p>Pattern: getById (cache yok) → lazy sync ensure + self proxy ile
 * loadCachedResponse (Cacheable + readOnly tx).
 */
@Service
public class PlayerDetailService {

    private final PlayerRepository playerRepository;
    private final PlayerCareerTeamRepository careerTeamRepository;
    private final PlayerTrophyRepository trophyRepository;
    private final PlayerSidelinedRepository sidelinedRepository;
    private final PlayerSeasonStatRepository statRepository;
    private final TransferRepository transferRepository;
    private final TeamRepository teamRepository;
    private final CountryRepository countryRepository;
    private final LeagueRepository leagueRepository;

    private final PlayerDetailLazySync lazySync;
    private final PlayerDetailSeoBuilder seoBuilder;
    private final FootballMessages messages;
    private final MinioStorageService storage;

    private final PlayerDetailService self;

    public PlayerDetailService(PlayerRepository playerRepository,
                               PlayerCareerTeamRepository careerTeamRepository,
                               PlayerTrophyRepository trophyRepository,
                               PlayerSidelinedRepository sidelinedRepository,
                               PlayerSeasonStatRepository statRepository,
                               TransferRepository transferRepository,
                               TeamRepository teamRepository,
                               CountryRepository countryRepository,
                               LeagueRepository leagueRepository,
                               PlayerDetailLazySync lazySync,
                               PlayerDetailSeoBuilder seoBuilder,
                               FootballMessages messages,
                               MinioStorageService storage,
                               @Lazy PlayerDetailService self) {
        this.playerRepository = playerRepository;
        this.careerTeamRepository = careerTeamRepository;
        this.trophyRepository = trophyRepository;
        this.sidelinedRepository = sidelinedRepository;
        this.statRepository = statRepository;
        this.transferRepository = transferRepository;
        this.teamRepository = teamRepository;
        this.countryRepository = countryRepository;
        this.leagueRepository = leagueRepository;
        this.lazySync = lazySync;
        this.seoBuilder = seoBuilder;
        this.messages = messages;
        this.storage = storage;
        this.self = self;
    }

    public PlayerDetailResponse getById(Long playerId, Integer requestedSeason, boolean turkish) {
        Integer currentSeason = resolvePlayerCurrentSeason(playerId);
        // Veri ZATEN dolu (profil + kariyer var) → tazelemeyi arka planda yap,
        // response'u BEKLETME. Ince/eksik (kariyer yok) veya yepyeni ise yine
        // senkron blokla — boylece kullaniciya hicbir zaman eksik sayfa gitmez.
        boolean rich = playerRepository.existsById(playerId)
                && careerTeamRepository.countByPlayerId(playerId) > 0;
        if (rich) {
            lazySync.ensureForAsync(playerId, requestedSeason, currentSeason);
        } else {
            lazySync.ensureFor(playerId, requestedSeason, currentSeason);
        }
        return self.loadCachedResponse(playerId, requestedSeason, turkish);
    }

    /** Cache'li okuma. {@code unless}: lazy sync henuz tamamlanmamissa
     *  cache'leme — career teams + season stats + transfers HEPSI bossa
     *  "thin" sayilir, bir sonraki istek fresh DB okur. */
    @Cacheable(value = FootballCacheNames.LIVE,
            key = "'player-' + #playerId + '-' + (#season == null ? 'cur' : #season) + '-' + (#turkish ? 'tr' : 'en')",
            unless = "#result == null || ("
                + "#result.careerTeams().isEmpty() "
                + "&& #result.seasonStats().isEmpty() "
                + "&& #result.transfers().isEmpty())")
    @Transactional(readOnly = true)
    public PlayerDetailResponse loadCachedResponse(Long playerId, Integer season, boolean turkish) {
        Player player = playerRepository.findById(playerId)
                .orElseThrow(() -> ApiException.notFound("Oyuncu bulunamadi."));

        // Sezon dropdown: career_teams'ten gelen tum yillar + DB stats'tan
        List<PlayerCareerTeam> careerTeams =
                careerTeamRepository.findByPlayerIdWithTeam(playerId);
        // TreeSet ters-siralayici null-guvenli DEGIL: career_teams.seasons veya
        // stat yillari icinde null bir yil olursa Integer.compareTo(null) NPE atar.
        // Bu yuzden null yillari ekleme oncesi ele.
        Set<Integer> seasonsUnion = new TreeSet<>(Comparator.reverseOrder());
        for (PlayerCareerTeam ct : careerTeams) {
            if (ct.getSeasons() == null) continue;
            for (Integer yr : ct.getSeasons()) {
                if (yr != null) seasonsUnion.add(yr);
            }
        }
        // İstatistiği OLAN sezonlar (yeni → eski). Pozisyon fallback'i bunu kullanır.
        List<Integer> statSeasonYears = statRepository.findSeasonYearsByPlayer(playerId);
        for (Integer yr : statSeasonYears) {
            if (yr != null) seasonsUnion.add(yr);
        }
        List<Integer> seasons = new ArrayList<>(seasonsUnion);

        Integer selectedSeason = season != null
                ? season
                : (seasons.isEmpty() ? null : seasons.get(0));

        // Mevcut takim: DB'de en son sezon stat'tan; yoksa careerTeams'ten
        TeamRef currentTeam = resolveCurrentTeam(playerId, careerTeams, turkish);

        // Profile
        String displayName = player.getName();
        String slug = SlugUtil.playerSlug(
                player.getFirstname(), player.getLastname(), displayName, player.getId());

        BirthInfo birth = null;
        if (player.getBirthDate() != null || player.getBirthPlace() != null
                || player.getBirthCountry() != null) {
            String countryText = resolveCountryText(player.getBirthCountry(), turkish);
            birth = new BirthInfo(
                    player.getBirthDate(), player.getBirthPlace(),
                    player.getBirthCountry(), countryText);
        }
        String nationalityText = resolveCountryText(player.getNationality(), turkish);
        String nationalityFlag = resolveCountryFlag(player.getNationality());
        String photo = player.getPhotoKey() != null
                ? storage.publicUrl(player.getPhotoKey())
                : player.getPhotoUrl();

        // Veri yukleme
        List<CareerTeamView> careerTeamViews = toCareerTeamViews(careerTeams, turkish);
        List<PlayerSeasonStatView> seasonStats = loadSeasonStats(playerId, selectedSeason, turkish);
        List<SidelinedRow> sidelined = loadSidelined(playerId, turkish);
        List<TransferRow> transfers = loadTransfers(playerId, turkish);
        List<TrophyView> trophies = loadTrophies(playerId, turkish);

        PlayerSeoResponse seo = seoBuilder.build(
                player, displayName, currentTeam, selectedSeason, turkish ? "tr" : "en");

        // Ana pozisyon — seçili sezonun ilk dolu games.position'ı (API-Football).
        String position = null;
        String positionText = null;
        for (PlayerSeasonStatView st : seasonStats) {
            if (st.position() != null && !st.position().isBlank()) {
                position = st.position();
                positionText = st.positionText();
                break;
            }
        }
        // Fallback: seçili sezonda pozisyon yoksa (ör. yeni sezon henüz
        // istatistiksiz — Haziran'da 2026 gibi), istatistiği OLAN en yeni
        // sezondan al. Hero pozisyon pill'i sezon seçiminden bağımsız dolu kalsın.
        if (position == null) {
            outer:
            for (Integer y : statSeasonYears) {  // yeni → eski
                for (PlayerSeasonStat s : statRepository.findByPlayerIdAndSeason(playerId, y)) {
                    String raw = extractPosition(s);
                    if (raw != null && !raw.isBlank()) {
                        position = raw;
                        positionText = messages.playerPosition(raw, turkish);
                        break outer;
                    }
                }
            }
        }

        return new PlayerDetailResponse(
                player.getId(),
                slug,
                displayName,
                player.getFirstname(),
                player.getLastname(),
                player.getAge(),
                player.getNationality(),
                nationalityText,
                nationalityFlag,
                photo,
                player.getHeight(),
                player.getWeight(),
                position,
                positionText,
                player.getInjured(),
                birth,
                currentTeam,
                selectedSeason,
                seasons,
                careerTeamViews,
                seasonStats,
                sidelined,
                transfers,
                trophies,
                seo);
    }

    /**
     * Oyuncunun "current" sezonu. DB stat'larindaki en yeni yil ile kariyer
     * takimlarindaki en yeni KULUP sezonunun MAX'i — boylece oyuncu kapsanmayan
     * bir lige gectiginde (stat henuz cekilmemisken) sync dogru/guncel sezonu
     * (orn. Ronaldo 2025) ceker, eski yilda (2021) takilmaz.
     */
    private Integer resolvePlayerCurrentSeason(Long playerId) {
        Integer statMax = null;
        List<Integer> years = statRepository.findSeasonYearsByPlayer(playerId);
        if (!years.isEmpty()) statMax = years.getFirst();

        Integer careerMax = null;
        for (PlayerCareerTeam ct : careerTeamRepository.findByPlayerIdWithTeam(playerId)) {
            Team t = ct.getTeam();
            if (t == null || t.isNational() || ct.getSeasons() == null) continue;
            for (Integer y : ct.getSeasons()) {
                if (y != null && (careerMax == null || y > careerMax)) careerMax = y;
            }
        }

        if (statMax == null) return careerMax;
        if (careerMax == null) return statMax;
        return Math.max(statMax, careerMax);
    }

    private TeamRef resolveCurrentTeam(Long playerId, List<PlayerCareerTeam> careerTeams,
                                       boolean turkish) {
        // "Mevcut takim" widget'i KULUP takimini tercih eder. Milli takim
        // sadece oyuncunun hicbir kulup kaydi yoksa kullanilir.
        //
        // SINYALLER (en guncel olan kazanir):
        //  A) EN SON TRANSFER'in "in" takimi — kulup-kulup hareketinin kesin
        //     tarihi var. KIRALIKLAR icin kritik: oyuncu kiralik gittiginde
        //     (orn. O. Kökçü → Beşiktaş, 2025-07) ana kulubunun (Benfica)
        //     sezon istatistikleri hala "guncel kulup" gibi gorunur; transfer
        //     "in" takimi gercek guncel kulubu verir.
        //  B) DB SEZON ISTATISTIKLERI — en yeni sezondaki ilk KULUP takimi.
        //
        // KARAR: transfer yili >= en yeni stat yili ise transfer kazanir
        // (transfer/kiralik en taze sinyaldir). Stat daha yeni bir sezonsa
        // (transfer kaydedilmemis serbest gecis vb.) stat kazanir. Boylece
        // kiralik-donusu gibi durumlar da dogru cozulur.
        //
        // Ornek (Fabinho): 2026 sezonunda sadece Brezilya (milli) oynamis,
        // kulup en son 2025 Al-Ittihad. Milli takim degil, kulup gosterilir.

        // --- A) En son transfer (max non-null tarih) → "in" takimi
        Transfer latestTransfer = null;
        for (Transfer tr : transferRepository.findByPlayerIdOrderByTransferDateDesc(playerId)) {
            if (tr.getTransferDate() == null || tr.getInTeamId() == null) continue;
            if (latestTransfer == null
                    || tr.getTransferDate().isAfter(latestTransfer.getTransferDate())) {
                latestTransfer = tr;
            }
        }
        Integer transferYear = latestTransfer != null
                ? latestTransfer.getTransferDate().getYear() : null;

        // --- B) DB stats — kulup oncelikli, en yeni sezondaki ilk kulup
        List<Integer> seasonYears = statRepository.findSeasonYearsByPlayer(playerId);
        Team statClub = null;
        Integer statYear = null;
        Team fallbackNational = null;
        outer:
        for (Integer year : seasonYears) {
            List<PlayerSeasonStat> stats =
                    statRepository.findByPlayerIdAndSeason(playerId, year);
            for (PlayerSeasonStat s : stats) {
                Team t = s.getTeam();
                if (t == null) continue;
                if (!t.isNational()) {
                    statClub = t;
                    statYear = year;
                    break outer; // en yeni sezondaki ilk kulup
                }
                if (fallbackNational == null) {
                    fallbackNational = t; // en yeni milli takim — fallback
                }
            }
        }

        // --- Kariyer takimlari — kulup oncelikli en yeni sezon (stat lag'ini asar)
        Team latestClub = null;
        int latestClubYear = Integer.MIN_VALUE;
        Team latestAny = null;
        int latestAnyYear = Integer.MIN_VALUE;
        for (PlayerCareerTeam ct : careerTeams) {
            if (ct.getSeasons() == null) continue;
            Team t = ct.getTeam();
            if (t == null) continue;
            for (Integer y : ct.getSeasons()) {
                if (y == null) continue;
                if (y > latestAnyYear) {
                    latestAnyYear = y;
                    latestAny = t;
                }
                if (!t.isNational() && y > latestClubYear) {
                    latestClubYear = y;
                    latestClub = t;
                }
            }
        }

        // --- EN YENI KULUP: sezon-stat kulubu ile kariyer kulubu arasindan yili
        // buyuk olan. Sezon stat'lari bayatsa (oyuncu kapsanmayan lige gitti,
        // stat henuz cekilmedi) kariyer takimlari daha guncel kulubu verir —
        // Ronaldo → Al-Nassr boyle dogru cozulur.
        Team bestClub = statClub;
        Integer bestClubYear = statYear;
        if (latestClub != null
                && (bestClubYear == null || latestClubYear > bestClubYear)) {
            bestClub = latestClub;
            bestClubYear = latestClubYear;
        }

        // --- KARAR: transfer vs en yeni kulup, en guncel kazanir
        if (latestTransfer != null
                && (bestClubYear == null || transferYear >= bestClubYear)) {
            Team inTeam = teamRepository.findById(latestTransfer.getInTeamId()).orElse(null);
            if (inTeam != null) {
                return toTeamRef(inTeam, turkish);
            }
            // DB'de takim yoksa transfer snapshot'undan TeamRef uret
            String snapName = latestTransfer.getInTeamName() != null
                    ? latestTransfer.getInTeamName()
                    : ("Team#" + latestTransfer.getInTeamId());
            return new TeamRef(latestTransfer.getInTeamId(), snapName,
                    latestTransfer.getInTeamLogo(),
                    SlugUtil.teamSlug(snapName, latestTransfer.getInTeamId()));
        }
        if (bestClub != null) {
            return toTeamRef(bestClub, turkish);
        }

        // --- Hicbir kulup kaydi yok — milli takim fallback (stats > career_teams)
        if (fallbackNational != null) {
            return toTeamRef(fallbackNational, turkish);
        }
        return latestAny != null ? toTeamRef(latestAny, turkish) : null;
    }

    private TeamRef toTeamRef(Team t, boolean turkish) {
        if (t == null) return null;
        String name = displayName(t, turkish);
        return new TeamRef(
                t.getId(), name,
                t.getLogoKey() != null ? storage.publicUrl(t.getLogoKey()) : null,
                SlugUtil.teamSlug(name, t.getId()));
    }

    private List<CareerTeamView> toCareerTeamViews(List<PlayerCareerTeam> rows, boolean turkish) {
        if (rows == null || rows.isEmpty()) return List.of();
        // En yeni sezona sahip takim once gelecek sekilde sirala
        rows.sort((a, b) -> {
            int aMax = maxSeason(a.getSeasons());
            int bMax = maxSeason(b.getSeasons());
            return Integer.compare(bMax, aMax);
        });
        List<CareerTeamView> out = new ArrayList<>(rows.size());
        for (PlayerCareerTeam ct : rows) {
            List<Integer> sortedSeasons = ct.getSeasons() == null ? List.of()
                    : ct.getSeasons().stream()
                            .filter(java.util.Objects::nonNull)
                            .sorted(Comparator.reverseOrder())
                            .toList();
            out.add(new CareerTeamView(toTeamRef(ct.getTeam(), turkish),
                    ct.getTeam() != null && ct.getTeam().isNational(), sortedSeasons));
        }
        return out;
    }

    private static int maxSeason(List<Integer> seasons) {
        if (seasons == null || seasons.isEmpty()) return Integer.MIN_VALUE;
        int max = Integer.MIN_VALUE;
        for (Integer s : seasons) if (s != null && s > max) max = s;
        return max;
    }

    /** PlayerSeasonStat JSONB'sinden {@code games.position}'ı çıkarır (yoksa null). */
    private static String extractPosition(PlayerSeasonStat s) {
        if (s.getStatsJson() != null
                && s.getStatsJson().get("games") instanceof Map<?, ?> gamesMap) {
            Object pos = gamesMap.get("position");
            if (pos instanceof String posStr && !posStr.isBlank()) return posStr;
        }
        return null;
    }

    private List<PlayerSeasonStatView> loadSeasonStats(Long playerId, Integer season,
                                                       boolean turkish) {
        if (season == null) return List.of();
        List<PlayerSeasonStat> rows =
                statRepository.findByPlayerIdAndSeason(playerId, season);
        if (rows.isEmpty()) return List.of();
        List<PlayerSeasonStatView> out = new ArrayList<>(rows.size());
        for (PlayerSeasonStat s : rows) {
            Team team = s.getTeam();
            League lg = s.getLeague();
            String teamName = displayName(team, turkish);
            String leagueName = displayName(lg, turkish);
            String rawPosition = extractPosition(s);
            out.add(new PlayerSeasonStatView(
                    team.getId(), teamName,
                    team.getLogoKey() != null ? storage.publicUrl(team.getLogoKey()) : null,
                    SlugUtil.teamSlug(teamName, team.getId()),
                    lg.getId(), leagueName,
                    lg.getLogoKey() != null ? storage.publicUrl(lg.getLogoKey()) : null,
                    SlugUtil.leagueSlug(leagueName, lg.getId()),
                    rawPosition,
                    rawPosition == null ? null : messages.playerPosition(rawPosition, turkish),
                    s.getStatsJson()));
        }
        return out;
    }

    private List<SidelinedRow> loadSidelined(Long playerId, boolean turkish) {
        List<PlayerSidelined> rows =
                sidelinedRepository.findByPlayerIdOrderByStartDateDesc(playerId);
        List<SidelinedRow> out = new ArrayList<>(rows.size());
        for (PlayerSidelined s : rows) {
            out.add(new SidelinedRow(
                    s.getType(), messages.injuryReason(s.getType(), turkish),
                    s.getStartDate(), s.getEndDate()));
        }
        return out;
    }

    private List<TransferRow> loadTransfers(Long playerId, boolean turkish) {
        List<Transfer> rows =
                transferRepository.findByPlayerIdOrderByTransferDateDesc(playerId);
        if (rows.isEmpty()) return List.of();

        // Lookup teams for slug/logo
        Set<Long> teamIds = new HashSet<>();
        for (Transfer t : rows) {
            if (t.getInTeamId() != null) teamIds.add(t.getInTeamId());
            if (t.getOutTeamId() != null) teamIds.add(t.getOutTeamId());
        }
        Map<Long, Team> teams = new HashMap<>();
        for (Team t : teamRepository.findAllById(teamIds)) teams.put(t.getId(), t);

        List<TransferRow> out = new ArrayList<>(rows.size());
        for (Transfer t : rows) {
            TeamRef from = buildTeamRef(t.getOutTeamId(), t.getOutTeamName(),
                    t.getOutTeamLogo(), teams, turkish);
            TeamRef to = buildTeamRef(t.getInTeamId(), t.getInTeamName(),
                    t.getInTeamLogo(), teams, turkish);
            out.add(new TransferRow(
                    t.getTransferDate(),
                    t.getTransferType(),
                    messages.transferType(t.getTransferType(), turkish),
                    from, to));
        }
        return out;
    }

    /** TeamRef: DB'de takim varsa name_tr + slug; yoksa transfer snapshot. */
    private TeamRef buildTeamRef(Long id, String snapshotName, String snapshotLogo,
                                  Map<Long, Team> teams, boolean turkish) {
        if (id == null) return null;
        Team t = teams.get(id);
        if (t != null) {
            String name = displayName(t, turkish);
            return new TeamRef(
                    t.getId(), name,
                    t.getLogoKey() != null ? storage.publicUrl(t.getLogoKey()) : null,
                    SlugUtil.teamSlug(name, t.getId()));
        }
        String name = snapshotName != null ? snapshotName : ("Team#" + id);
        return new TeamRef(id, name, snapshotLogo, SlugUtil.teamSlug(name, id));
    }

    private List<TrophyView> loadTrophies(Long playerId, boolean turkish) {
        List<PlayerTrophy> rows = trophyRepository.findByPlayerIdOrderBySeason(playerId);
        List<TrophyView> out = new ArrayList<>(rows.size());
        for (PlayerTrophy t : rows) {
            out.add(new TrophyView(
                    t.getLeague(),
                    resolveLeagueText(t.getLeague(), t.getCountry(), turkish),
                    t.getCountry(),
                    resolveCountryText(t.getCountry(), turkish),
                    t.getSeason(),
                    t.getPlace(),
                    messages.trophyPlace(t.getPlace(), turkish)));
        }
        return out;
    }

    private String resolveLeagueText(String leagueName, String countryName, boolean turkish) {
        if (leagueName == null || leagueName.isBlank()) return leagueName;
        if (!turkish) return leagueName;
        if (countryName == null) return leagueName;
        return leagueRepository.findFirstByNameIgnoreCaseAndCountryNameIgnoreCase(
                        leagueName, countryName)
                .map(lg -> lg.getNameTr() != null && !lg.getNameTr().isBlank()
                        ? lg.getNameTr() : lg.getName())
                .orElse(leagueName);
    }

    private String resolveCountryText(String countryName, boolean turkish) {
        if (countryName == null || countryName.isBlank()) return countryName;
        if (!turkish) return countryName;
        return countryRepository.findByName(countryName)
                .map(c -> c.getNameTr() != null && !c.getNameTr().isBlank()
                        ? c.getNameTr() : c.getName())
                .orElse(countryName);
    }

    /** Uyruk ulke adindan bayrak URL'i cozer (Country.flagKey → mirror
     *  publicUrl, yoksa ham flagUrl). Bulunamazsa null. */
    private String resolveCountryFlag(String countryName) {
        if (countryName == null || countryName.isBlank()) return null;
        return countryRepository.findByName(countryName)
                .map(c -> c.getFlagKey() != null && !c.getFlagKey().isBlank()
                        ? storage.publicUrl(c.getFlagKey())
                        : c.getFlagUrl())
                .orElse(null);
    }

    private static String displayName(TranslatableName entity, boolean turkish) {
        if (turkish) {
            String tr = entity.getNameTr();
            if (tr != null && !tr.isBlank()) {
                return tr;
            }
        }
        return entity.getName();
    }
}
