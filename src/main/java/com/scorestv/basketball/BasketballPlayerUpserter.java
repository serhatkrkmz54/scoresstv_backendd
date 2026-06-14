package com.scorestv.basketball;

import com.scorestv.basketball.domain.BasketballLeague;
import com.scorestv.basketball.domain.BasketballPlayer;
import com.scorestv.basketball.domain.BasketballPlayerRepository;
import com.scorestv.basketball.domain.BasketballPlayerSeasonStat;
import com.scorestv.basketball.domain.BasketballPlayerSeasonStatRepository;
import com.scorestv.basketball.domain.BasketballTeam;
import com.scorestv.basketball.domain.BasketballTeamRepository;
import com.scorestv.common.SlugUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * Oyuncu master tablo (basketball_players) upsert helper'i.
 *
 * <p>Iki upsert yolu:
 * <ul>
 *   <li>{@link #ensure} — game stats sync sirasinda iskelet upsert
 *       (sadece id + name + team).
 *   <li>{@link #upsertFromProfile} — {@code /players?id=X&season=Y} cevabindan
 *       FULL profile upsert. Foto + ulke + dogum + boy/kilo + jersey +
 *       pozisyon + sezonluk istatistikler ({@code BasketballPlayerSeasonStat}'a
 *       yazilir).
 * </ul>
 */
@Component
public class BasketballPlayerUpserter {

    private static final Logger log = LoggerFactory.getLogger(BasketballPlayerUpserter.class);

    private final BasketballPlayerRepository playerRepo;
    private final BasketballPlayerSeasonStatRepository statRepo;
    private final BasketballTeamRepository teamRepo;

    public BasketballPlayerUpserter(BasketballPlayerRepository playerRepo,
                                    BasketballPlayerSeasonStatRepository statRepo,
                                    BasketballTeamRepository teamRepo) {
        this.playerRepo = playerRepo;
        this.statRepo = statRepo;
        this.teamRepo = teamRepo;
    }

    /**
     * Iskelet upsert (game stats sync). Yoksa olustur, varsa ad/takim guncelle.
     */
    public BasketballPlayer ensure(Long id, String name, BasketballTeam team) {
        if (id == null) return null;
        BasketballPlayer p = playerRepo.findById(id).orElseGet(BasketballPlayer::new);
        p.setId(id);
        if (name != null && !name.isBlank()) {
            p.setName(name);
        } else if (p.getName() == null || p.getName().isBlank()) {
            p.setName("Player #" + id);
        }
        if (team != null) {
            p.setTeam(team);
        }
        return playerRepo.save(p);
    }

    /**
     * Full profile upsert. {@code /players?id=X&season=Y} cevabindan gelen
     * DTO'yu master tabloya yazar; ayrica {@code leagues[league.id]} LeagueStat
     * varsa onu {@code BasketballPlayerSeasonStat}'a yazar.
     *
     * <p>Manual alanlar (slug yoksa uretilir, varsa korunur). Foto sadece API'de
     * yeni URL varsa overwrite; mevcut {@code photoKey} (MinIO) korunur ki
     * mirror servisi pipeline'i bozmasin.
     *
     * @param dto    API DTO
     * @param league sezon stat'i hangi lig icin (null verirse stat yazilmaz)
     * @param season sezon string (orn. "2024-2025")
     * @return upsert edilmis player; bos id'de null
     */
    @Transactional
    public BasketballPlayer upsertFromProfile(BkPlayerDto dto,
                                              BasketballLeague league,
                                              String season) {
        if (dto == null || dto.id() == null) return null;

        BasketballPlayer p = playerRepo.findById(dto.id()).orElseGet(BasketballPlayer::new);
        boolean isNew = p.getId() == null;
        p.setId(dto.id());

        // Ad alanlari — first/last varsa "First Last", yoksa mevcut isim korunur
        if (dto.firstname() != null && !dto.firstname().isBlank()) {
            p.setFirstName(dto.firstname());
        }
        if (dto.lastname() != null && !dto.lastname().isBlank()) {
            p.setLastName(dto.lastname());
        }
        // Tam ad — first+last varsa onlardan; yoksa mevcut name korunur
        if (p.getFirstName() != null && p.getLastName() != null) {
            p.setName((p.getFirstName() + " " + p.getLastName()).trim());
        } else if (p.getName() == null || p.getName().isBlank()) {
            p.setName("Player #" + dto.id());
        }

        // Slug — yoksa uret (mevcut slug korunur ki SEO bozulmasin)
        if (p.getSlug() == null || p.getSlug().isBlank()) {
            p.setSlug(SlugUtil.playerSlug(
                    p.getFirstName(), p.getLastName(), p.getName(), p.getId()));
        }

        // Dogum
        if (dto.birth() != null) {
            LocalDate birthDate = parseDate(dto.birth().date());
            if (birthDate != null) p.setBirthDate(birthDate);
            if (dto.birth().country() != null) p.setBirthCountry(dto.birth().country());
        }

        // Ulke (uyrugu) ve fiziksel
        if (dto.country() != null) p.setNationality(dto.country());

        Integer heightCm = parseHeightCm(dto.height());
        if (heightCm != null) p.setHeightCm(heightCm);
        Integer weightKg = parseWeightKg(dto.weight());
        if (weightKg != null) p.setWeightKg(weightKg);

        if (dto.college() != null) p.setCollege(dto.college());

        // Jersey + pozisyon + takim (leagues map'inden — eldeki lig icin)
        BkPlayerDto.LeagueStat stat = pickLeagueStat(dto.leagues(), league);
        if (stat != null) {
            if (stat.jersey() != null) p.setJerseyNumber(stat.jersey());
            if (stat.position() != null) p.setPosition(stat.position());
            if (stat.team() != null && stat.team().id() != null) {
                BasketballTeam team = teamRepo.findById(stat.team().id()).orElse(null);
                if (team != null) p.setTeam(team);
            }
        }

        p.setLastProfileSyncedAt(Instant.now());
        BasketballPlayer saved = playerRepo.save(p);

        // Sezonluk istatistik (varsa)
        if (stat != null && league != null && season != null) {
            upsertSeasonStat(saved, league, season, stat);
        }

        if (isNew) {
            log.info("BasketballPlayer YENI profil eklendi id={} name={} slug={}",
                    saved.getId(), saved.getName(), saved.getSlug());
        } else {
            log.debug("BasketballPlayer profil tazelendi id={}", saved.getId());
        }
        return saved;
    }

    // ================================================================
    // Helpers
    // ================================================================

    /**
     * {@code leagues} map'inden (key=lig id string) verilen lig icin stat'i
     * cek. Lig null veya map'te yok ise ilk degeri al — bazi oyuncularda
     * tek lig vardir.
     */
    private static BkPlayerDto.LeagueStat pickLeagueStat(
            Map<String, BkPlayerDto.LeagueStat> leagues, BasketballLeague league) {
        if (leagues == null || leagues.isEmpty()) return null;
        if (league != null) {
            BkPlayerDto.LeagueStat exact = leagues.get(String.valueOf(league.getId()));
            if (exact != null) return exact;
        }
        // Fallback: ilk geleni al (tek lig)
        return leagues.values().iterator().next();
    }

    private static LocalDate parseDate(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return LocalDate.parse(iso);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Boy alani API'den iki sekilde gelebilir:
     * <ul>
     *   <li>String: "1.91" (metre cinsinden) veya "6'3"" (foot/inch)
     *   <li>Object: {@code {meters: "1.91"}} veya {@code {feets: "6'3\""}}
     * </ul>
     * Metre→cm donusturulur. Foot/inch parse edilmez (defansif null doner).
     */
    private static Integer parseHeightCm(Object raw) {
        String meters = extractMeasure(raw, "meters");
        if (meters == null) return null;
        try {
            double m = Double.parseDouble(meters.replace(",", ".").trim());
            return (int) Math.round(m * 100.0);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Kilo alani API'den iki sekilde gelebilir:
     * <ul>
     *   <li>String: "84"
     *   <li>Object: {@code {kilograms: "84"}} veya {@code {pounds: "185"}}
     * </ul>
     * Kilogram aliyor; pound destegi yok (defansif null doner).
     */
    private static Integer parseWeightKg(Object raw) {
        String kg = extractMeasure(raw, "kilograms");
        if (kg == null) return null;
        try {
            return (int) Math.round(Double.parseDouble(kg.replace(",", ".").trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Object→String defansif olcum cikarici. Raw direkt string ise donulur;
     * Map ise {@code preferKey} aranir.
     */
    @SuppressWarnings("unchecked")
    private static String extractMeasure(Object raw, String preferKey) {
        if (raw == null) return null;
        if (raw instanceof String s) return s.isBlank() ? null : s;
        if (raw instanceof Map<?, ?> m) {
            Object v = ((Map<String, Object>) m).get(preferKey);
            return v == null ? null : v.toString();
        }
        return raw.toString();
    }

    /**
     * LeagueStat'tan {@code BasketballPlayerSeasonStat}'i upsert et — unique
     * (player, league, season) constraint var, mevcut satir varsa update.
     */
    private void upsertSeasonStat(BasketballPlayer player,
                                  BasketballLeague league,
                                  String season,
                                  BkPlayerDto.LeagueStat stat) {
        BasketballPlayerSeasonStat e = statRepo
                .findByPlayerIdAndLeagueIdAndSeason(
                        player.getId(), league.getId(), season)
                .orElseGet(BasketballPlayerSeasonStat::new);

        e.setPlayer(player);
        e.setLeague(league);
        e.setSeason(season);
        if (stat.team() != null && stat.team().id() != null) {
            BasketballTeam team = teamRepo.findById(stat.team().id()).orElse(null);
            if (team != null) e.setTeam(team);
        }

        // Maclar
        if (stat.games() != null) {
            e.setGamesPlayed(stat.games().appearences());
            // minutes_per_game — API "30:24" formatinda; sayisal cevirme
            // gerekirse ileride parser eklenir, simdilik null kalir.
        }

        // Skorlama
        if (stat.points() != null) {
            e.setPointsPerGame(parseDecimal(stat.points().average()));
        }
        applyShot(stat.field_goals(),
                e::setFieldGoalsMade,
                e::setFieldGoalsAttempts,
                e::setFieldGoalsPct);
        applyShot(stat.threepoint_goals(),
                e::setThreepointMade,
                e::setThreepointAttempts,
                e::setThreepointPct);
        applyShot(stat.freethrows_goals(),
                e::setFreethrowsMade,
                e::setFreethrowsAttempts,
                e::setFreethrowsPct);

        // Ribaund
        if (stat.rebounds() != null) {
            e.setReboundsTotal(parseDecimal(stat.rebounds().average()));
        }
        // AST/STL/BLK/TO/PF — average
        if (stat.assists() != null) e.setAssistsPerGame(parseDecimal(stat.assists().average()));
        if (stat.steals() != null) e.setStealsPerGame(parseDecimal(stat.steals().average()));
        if (stat.blocks() != null) e.setBlocksPerGame(parseDecimal(stat.blocks().average()));
        if (stat.turnovers() != null) e.setTurnoversPerGame(parseDecimal(stat.turnovers().average()));
        if (stat.fouls() != null) e.setFoulsPerGame(parseDecimal(stat.fouls().average()));

        statRepo.save(e);
    }

    private static void applyShot(BkPlayerDto.ShotStat src,
                                   java.util.function.Consumer<BigDecimal> setMade,
                                   java.util.function.Consumer<BigDecimal> setAttempts,
                                   java.util.function.Consumer<BigDecimal> setPct) {
        if (src == null) return;
        if (src.average() != null) {
            BigDecimal v = parseDecimal(src.average());
            if (v != null) setMade.accept(v);
        }
        if (src.attempts() != null) {
            setAttempts.accept(BigDecimal.valueOf(src.attempts()));
        }
        if (src.percentage() != null) {
            BigDecimal v = parseDecimal(src.percentage());
            if (v != null) setPct.accept(v);
        }
    }

    private static BigDecimal parseDecimal(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return new BigDecimal(raw.replace(",", ".").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
