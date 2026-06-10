package com.scorestv.football.league;

import com.scorestv.common.SlugUtil;
import com.scorestv.football.FootballMessages;
import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.football.domain.Team;
import com.scorestv.football.domain.TranslatableName;
import com.scorestv.football.web.dto.BracketView;
import com.scorestv.football.web.dto.BracketView.BracketTeam;
import com.scorestv.football.web.dto.BracketView.Champion;
import com.scorestv.football.web.dto.BracketView.KnockoutRound;
import com.scorestv.football.web.dto.BracketView.KnockoutTie;
import com.scorestv.football.web.dto.BracketView.TieLeg;
import com.scorestv.storage.MinioStorageService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Kupa eleme bracket olusturucu — lig fixture'larindan knockout aşamalarını
 * cikarir, 2-leg ties'leri birlestirir, aggregate skor + kazanan tespiti yapar.
 *
 * <p>Algoritma:
 * <ol>
 *   <li>League type "Cup" mu? Degilse null doner (lig icin bracket gosterilmez)</li>
 *   <li>Tum fixture'lari cek, group/regular stage'leri at, knockout tur kalir</li>
 *   <li>Her tur icinde fixture'lari (homeId, awayId) sirali takim ciftine gore
 *       grupla — ayni cift iki kez goruluyorsa 2-leg tie</li>
 *   <li>Aggregate skor hesapla, kazanan tespit et (penalti dahil)</li>
 * </ol>
 */
@Service
public class BracketBuilder {

    private final FixtureRepository fixtureRepository;
    private final FootballMessages messages;
    private final MinioStorageService storage;

    public BracketBuilder(FixtureRepository fixtureRepository,
                          FootballMessages messages,
                          MinioStorageService storage) {
        this.fixtureRepository = fixtureRepository;
        this.messages = messages;
        this.storage = storage;
    }

    /**
     * Verilen lig + sezon icin bracket olustur. Lig "Cup" degilse veya
     * knockout fixture'lari yoksa null doner.
     */
    public BracketView build(String leagueType, Long leagueId, Integer season, boolean turkish) {
        if (!isCupLike(leagueType) || season == null) {
            return null;
        }
        List<Fixture> all = fixtureRepository.findByLeagueIdAndSeason(leagueId, season);
        if (all.isEmpty()) return null;

        // Knockout fixture'lari ve siralama
        Map<String, List<Fixture>> byRound = new LinkedHashMap<>();
        for (Fixture f : all) {
            String round = f.getRound();
            if (round == null || round.isBlank()) continue;
            if (!KnockoutClassifier.isKnockout(round)) continue;
            // UCL/UEL/UECL'nin yeni format "Knockout Round Play-offs" turu
            // klasik 2:1 bracket disiplinine uymaz (1:1) ve gercek eslesme
            // bilgisi flat fixture listesinden cikarilamaz. SofaScore'da da
            // bu tur bracket disinda — galipler zaten R16'da gorulur.
            if (KnockoutClassifier.isPreKnockoutPlayoff(round)) continue;
            // Normalize: "Round of 16 - 1st Leg" → "Round of 16"; 1st/2nd leg
            // tek tura toplanir, ic-ice tie 2 leg olur.
            String normalized = KnockoutClassifier.normalize(round);
            byRound.computeIfAbsent(normalized, k -> new ArrayList<>()).add(f);
        }
        if (byRound.isEmpty()) return null;

        // Tur sirasi — order'a gore artarak (Final en yuksek)
        List<Map.Entry<String, List<Fixture>>> sortedRounds = new ArrayList<>(byRound.entrySet());
        sortedRounds.sort(Comparator.comparingInt(e -> KnockoutClassifier.orderOf(e.getKey())));

        List<KnockoutRound> rounds = new ArrayList<>(sortedRounds.size());
        Champion champion = null;

        for (Map.Entry<String, List<Fixture>> entry : sortedRounds) {
            String roundName = entry.getKey();
            List<Fixture> fixtures = entry.getValue();
            List<KnockoutTie> ties = buildTies(fixtures, turkish);
            int order = KnockoutClassifier.orderOf(roundName);
            String nameText = messages.roundText(roundName, turkish);
            rounds.add(new KnockoutRound(
                    roundName, nameText, order, ties.size(), ties));

        }

        // Post-process: API "Final" turunda birden cok kickoff tarihi kumesi
        // varsa (TR Kupasi gibi — Aralik 2025 play-in maclari + Mayis 2026
        // gercek finali ayni "Final" altinda), kumelere ayir ve gercek
        // Final'i (en gec, 1-2 tie) ayikla.
        rounds = splitMisclassifiedFinalRounds(rounds, turkish);

        // Sampiyon belirle — Final turundaki en gec kickoff'lu tie kazanani.
        for (KnockoutRound r : rounds) {
            if (!KnockoutClassifier.isFinalRound(r.name()) || r.ties().isEmpty()) {
                continue;
            }
            KnockoutTie latestTie = r.ties().stream()
                    .filter(t -> t.legs() != null && !t.legs().isEmpty())
                    .max(Comparator.comparing(
                            t -> t.legs().get(t.legs().size() - 1).kickoff(),
                            Comparator.nullsFirst(Comparator.naturalOrder())))
                    .orElse(null);
            if (latestTie != null && latestTie.winnerTeamId() != null) {
                BracketTeam winnerTeam =
                        latestTie.home().id().equals(latestTie.winnerTeamId())
                                ? latestTie.home() : latestTie.away();
                champion = new Champion(
                        winnerTeam.id(), winnerTeam.name(),
                        winnerTeam.logo(), winnerTeam.slug());
            }
        }

        return new BracketView(rounds, champion);
    }

    /**
     * "Final" turunda 2'den cok tie varsa kickoff tarihi kumelerine boler.
     * En gec kume (1-2 tie) gercek Final olarak kalir; daha onceki buyuk kumeler
     * boyutuna gore "Round of 32" / "Round of 16" / "Knockout Round Play-offs"
     * olarak adlandirilir.
     *
     * <p>TR Kupasi senaryosu: Aralik 2025'te 19 play-in maci + Mayis 2026'da
     * 1 gercek final, hepsi API'de "Final" etiketli. Bu metod kume bazli ayirir.
     *
     * <p>Threshold: 14 gun aradan buyuk kickoff farki → ayri kume.
     */
    private List<KnockoutRound> splitMisclassifiedFinalRounds(
            List<KnockoutRound> rounds, boolean turkish) {
        List<KnockoutRound> result = new ArrayList<>(rounds.size());
        for (KnockoutRound round : rounds) {
            if (!KnockoutClassifier.isFinalRound(round.name())
                    || round.ties().size() <= 2) {
                // Normal Final (1 tie ya da 2-leg final ile birlestirilemeyen 2 tie)
                result.add(round);
                continue;
            }
            // Kickoff tarihi kumelerine ayir (>14 gun → ayri kume)
            List<List<KnockoutTie>> clusters = clusterByKickoffGap(
                    round.ties(), Duration.ofDays(14));
            if (clusters.size() <= 1) {
                result.add(round);  // Tek kume, oldugu gibi birak
                continue;
            }
            // Birden cok kume — yeniden adlandir
            for (int i = 0; i < clusters.size(); i++) {
                List<KnockoutTie> cluster = clusters.get(i);
                boolean isLastCluster = i == clusters.size() - 1;
                String synName;
                int synOrder;
                if (isLastCluster && cluster.size() <= 2) {
                    // En gec kume kucuk — gercek Final
                    synName = "Final";
                    synOrder = 100;
                } else {
                    // Yanlis etiketli kume — boyutuna gore yakistir
                    int n = cluster.size();
                    if (n == 16) {
                        synName = "Round of 32";
                        synOrder = 60;
                    } else if (n == 8) {
                        synName = "Round of 16";
                        synOrder = 70;
                    } else {
                        // Esitsiz sayilar (TR Kupasi 19 mac gibi) icin neutral
                        synName = "Knockout Round Play-offs";
                        synOrder = 30;
                    }
                }
                String synText = messages.roundText(synName, turkish);
                result.add(new KnockoutRound(
                        synName, synText, synOrder, cluster.size(), cluster));
            }
        }
        // Ayni isimli rounds varsa birlestir (split sonrasi olabilir)
        Map<String, List<KnockoutTie>> merged = new LinkedHashMap<>();
        Map<String, Integer> ordersByName = new LinkedHashMap<>();
        for (KnockoutRound r : result) {
            merged.computeIfAbsent(r.name(), k -> new ArrayList<>()).addAll(r.ties());
            ordersByName.putIfAbsent(r.name(), r.order());
        }
        List<KnockoutRound> finalList = new ArrayList<>(merged.size());
        for (Map.Entry<String, List<KnockoutTie>> e : merged.entrySet()) {
            String name = e.getKey();
            List<KnockoutTie> ties = e.getValue();
            int order = ordersByName.get(name);
            String nameText = messages.roundText(name, turkish);
            finalList.add(new KnockoutRound(name, nameText, order, ties.size(), ties));
        }
        finalList.sort(Comparator.comparingInt(KnockoutRound::order));
        return finalList;
    }

    /**
     * Tie listesini kickoff tarihine gore kumeler. Pesinden gelen iki tie
     * arasinda gap > threshold ise yeni kume baslat.
     */
    private static List<List<KnockoutTie>> clusterByKickoffGap(
            List<KnockoutTie> ties, Duration threshold) {
        List<KnockoutTie> sorted = ties.stream()
                .filter(t -> firstKickoff(t.legs()) != null)
                .sorted(Comparator.comparing(t -> firstKickoff(t.legs())))
                .toList();
        if (sorted.isEmpty()) return List.of();
        List<List<KnockoutTie>> clusters = new ArrayList<>();
        List<KnockoutTie> current = new ArrayList<>();
        current.add(sorted.get(0));
        for (int i = 1; i < sorted.size(); i++) {
            Instant prev = firstKickoff(sorted.get(i - 1).legs());
            Instant curr = firstKickoff(sorted.get(i).legs());
            if (Duration.between(prev, curr).compareTo(threshold) > 0) {
                clusters.add(current);
                current = new ArrayList<>();
            }
            current.add(sorted.get(i));
        }
        clusters.add(current);
        return clusters;
    }

    /**
     * Bir turdaki fixture'lardan tie listesi olustur. Iki fixture'in takim
     * cifti ayni ise (ev sahibi/deplasman ters de olabilir) — 2-leg tie.
     */
    private List<KnockoutTie> buildTies(List<Fixture> fixtures, boolean turkish) {
        // Tie key: takim id'lerinin sirali pair'i (min-max). Ayni cift max 2 leg.
        Map<String, List<Fixture>> byPair = new LinkedHashMap<>();
        for (Fixture f : fixtures) {
            if (f.getHomeTeam() == null || f.getAwayTeam() == null) continue;
            long a = f.getHomeTeam().getId();
            long b = f.getAwayTeam().getId();
            String key = (Math.min(a, b)) + "-" + (Math.max(a, b));
            byPair.computeIfAbsent(key, k -> new ArrayList<>()).add(f);
        }

        List<KnockoutTie> ties = new ArrayList<>(byPair.size());
        for (Map.Entry<String, List<Fixture>> e : byPair.entrySet()) {
            List<Fixture> legs = e.getValue();
            legs.sort(Comparator.comparing(Fixture::getKickoffAt,
                    Comparator.nullsLast(Comparator.naturalOrder())));
            ties.add(toTie(e.getKey(), legs, turkish));
        }
        // Kickoff sirasi: en erken oynanan tie ilk
        ties.sort(Comparator.comparing(
                t -> firstKickoff(t.legs()),
                Comparator.nullsLast(Comparator.naturalOrder())));
        return ties;
    }

    private KnockoutTie toTie(String tieId, List<Fixture> legs, boolean turkish) {
        // Home/away referans takimi: 1. leg'in ev sahibi (yoksa ilk fixture)
        Fixture first = legs.get(0);
        Team homeRef = first.getHomeTeam();
        Team awayRef = first.getAwayTeam();

        // Leg'leri DTO'ya cevir
        List<TieLeg> legDtos = new ArrayList<>(legs.size());
        for (int i = 0; i < legs.size(); i++) {
            Fixture f = legs.get(i);
            String legLabel = legs.size() == 2
                    ? (i == 0 ? "1st Leg" : "2nd Leg")
                    : null;
            String slug = SlugUtil.fixtureSlug(
                    f.getHomeTeam().getName(),
                    f.getAwayTeam().getName(), f.getId());
            legDtos.add(new TieLeg(
                    f.getId(),
                    slug,
                    f.getKickoffAt(),
                    f.getStatusShort(),
                    messages.statusText(f.getStatusShort(), f.getStatusLong(), turkish),
                    f.getHomeGoals(),
                    f.getAwayGoals(),
                    f.getHomeTeam().getId(),
                    f.getAwayTeam().getId(),
                    legLabel));
        }

        // Aggregate hesapla — homeRef/awayRef perspektifinden
        int aggH = 0, aggA = 0;
        boolean anyPlayed = false;
        boolean inProgress = false;
        for (Fixture f : legs) {
            if (f.getHomeGoals() == null || f.getAwayGoals() == null) {
                // Mac henuz oynanmadi
                if (isLiveOrUpcoming(f.getStatusShort())) inProgress = true;
                continue;
            }
            anyPlayed = true;
            if (isLiveOrUpcoming(f.getStatusShort())) inProgress = true;
            // Bu leg'in home/away'i homeRef/awayRef ile ayni mi?
            if (f.getHomeTeam().getId().equals(homeRef.getId())) {
                aggH += f.getHomeGoals();
                aggA += f.getAwayGoals();
            } else {
                aggH += f.getAwayGoals();
                aggA += f.getHomeGoals();
            }
        }

        // Penalti — sadece son leg'in penalty skoru ge=erli (tie penaltisina gitti)
        Integer penH = null, penA = null;
        Fixture lastLeg = legs.get(legs.size() - 1);
        if (lastLeg.getScorePenHome() != null && lastLeg.getScorePenAway() != null) {
            if (lastLeg.getHomeTeam().getId().equals(homeRef.getId())) {
                penH = lastLeg.getScorePenHome();
                penA = lastLeg.getScorePenAway();
            } else {
                penH = lastLeg.getScorePenAway();
                penA = lastLeg.getScorePenHome();
            }
        }

        // Kazanan — sadece tie tamamen oynanmissa
        Long winnerId = null;
        boolean allFinal = legs.stream().allMatch(f -> isFinalStatus(f.getStatusShort()));
        if (anyPlayed && allFinal) {
            if (aggH > aggA) winnerId = homeRef.getId();
            else if (aggA > aggH) winnerId = awayRef.getId();
            else if (penH != null && penA != null) {
                if (penH > penA) winnerId = homeRef.getId();
                else if (penA > penH) winnerId = awayRef.getId();
            }
        }

        Integer aggregateHome = anyPlayed ? aggH : null;
        Integer aggregateAway = anyPlayed ? aggA : null;

        return new KnockoutTie(
                tieId,
                legs.size(),
                toBracketTeam(homeRef, turkish),
                toBracketTeam(awayRef, turkish),
                legDtos,
                aggregateHome,
                aggregateAway,
                penH,
                penA,
                winnerId,
                inProgress);
    }

    private BracketTeam toBracketTeam(Team t, boolean turkish) {
        if (t == null) return null;
        String name = displayName(t, turkish);
        return new BracketTeam(
                t.getId(),
                name,
                t.getLogoKey() != null ? storage.publicUrl(t.getLogoKey()) : null,
                SlugUtil.teamSlug(name, t.getId()));
    }

    private static String displayName(TranslatableName entity, boolean turkish) {
        if (turkish) {
            String tr = entity.getNameTr();
            if (tr != null && !tr.isBlank()) return tr;
        }
        return entity.getName();
    }

    private static java.time.Instant firstKickoff(List<TieLeg> legs) {
        if (legs == null || legs.isEmpty()) return null;
        return legs.get(0).kickoff();
    }

    private static boolean isCupLike(String type) {
        if (type == null) return false;
        String t = type.toLowerCase(Locale.ROOT);
        return t.contains("cup");
    }

    /** Live veya yaklasan: NS, TBD, 1H, 2H, HT, ET, BT, P, LIVE, INT, SUSP, PST. */
    private static boolean isLiveOrUpcoming(String status) {
        if (status == null) return true;
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "NS", "TBD", "1H", "2H", "HT", "ET", "BT", "P", "LIVE", "INT", "SUSP", "PST" -> true;
            default -> false;
        };
    }

    /** Final statuler: FT, AET, PEN, AWD, WO, CANC, ABD. */
    private static boolean isFinalStatus(String status) {
        if (status == null) return false;
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "FT", "AET", "PEN", "AWD", "WO" -> true;
            default -> false;
        };
    }
}
