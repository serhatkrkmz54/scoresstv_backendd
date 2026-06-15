package com.scorestv.rankings.web;

import com.scorestv.common.SlugUtil;
import com.scorestv.football.FootballCacheNames;
import com.scorestv.football.domain.Country;
import com.scorestv.football.domain.CountryRepository;
import com.scorestv.rankings.domain.FifaRanking;
import com.scorestv.rankings.domain.FifaRankingRepository;
import com.scorestv.rankings.domain.UefaClubRanking;
import com.scorestv.rankings.domain.UefaClubRankingRepository;
import com.scorestv.rankings.domain.UefaCountryRanking;
import com.scorestv.rankings.domain.UefaCountryRankingRepository;
import com.scorestv.rankings.web.dto.FifaRankingResponse;
import com.scorestv.rankings.web.dto.UefaClubRankingResponse;
import com.scorestv.rankings.web.dto.UefaCountryRankingResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Rankings sayfasi serving servisi — 3 endpoint icin DB okumasi + filter.
 *
 * <p>Cache: STATIC (24sa) — ranking veri gunde 1 kez tazelenir, yuksek TTL
 * uygun. STATIC cache adi referans verileri icin tahsis edilmis.
 */
@Service
public class RankingsService {

    private final FifaRankingRepository fifaRepository;
    private final UefaClubRankingRepository uefaClubRepository;
    private final UefaCountryRankingRepository uefaCountryRepository;
    private final CountryRepository countryRepository;

    public RankingsService(FifaRankingRepository fifaRepository,
                            UefaClubRankingRepository uefaClubRepository,
                            UefaCountryRankingRepository uefaCountryRepository,
                            CountryRepository countryRepository) {
        this.fifaRepository = fifaRepository;
        this.uefaClubRepository = uefaClubRepository;
        this.uefaCountryRepository = uefaCountryRepository;
        this.countryRepository = countryRepository;
    }

    // ============================================================
    // FIFA
    // ============================================================

    @Cacheable(value = FootballCacheNames.RANKINGS,
            key = "'fifa-' + (#p0 == null ? 'all' : #p0) "
                + "+ '-' + (#p1 == null ? '' : #p1) "
                + "+ '-' + (#p2 ? 'tr' : 'en')")
    @Transactional(readOnly = true)
    public FifaRankingResponse fifaRanking(String confederation, String search, boolean turkish) {
        List<FifaRanking> all = (confederation != null && !confederation.isBlank())
                ? fifaRepository.findByConfederationOrderByRankAsc(
                        confederation.trim().toUpperCase(Locale.ROOT))
                : fifaRepository.findAllByOrderByRankAsc();

        if (search != null && !search.isBlank()) {
            String q = search.trim().toLowerCase(Locale.ROOT);
            all = all.stream()
                    .filter(r -> {
                        String name = r.getTeamName() != null
                                ? r.getTeamName().toLowerCase(Locale.ROOT) : "";
                        String code = r.getCountryCode() != null
                                ? r.getCountryCode().toLowerCase(Locale.ROOT) : "";
                        return name.contains(q) || code.contains(q);
                    })
                    .toList();
        }

        Instant lastUpdated = all.stream()
                .map(FifaRanking::getLastSyncedAt)
                .filter(java.util.Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);

        // Country tablosundan flag URL + 2-harf ISO topla (case-insensitive
        // name lookup). FIFA teamName = ulke adi (England, Italy, Spain, ...)
        // → Country.name ile esle.
        Map<String, String> flagByName = new HashMap<>();
        Map<String, String> iso2ByName = new HashMap<>();
        Map<String, String> nameTrByName = new HashMap<>();
        // DB Country eslesmesi olan satirlar icin /ulke linki slug'i.
        Map<String, String> slugByName = new HashMap<>();
        for (Country c : countryRepository.findAll()) {
            if (c.getName() == null) continue;
            String key = c.getName().toLowerCase(Locale.ROOT);
            if (c.getFlagUrl() != null) flagByName.put(key, c.getFlagUrl());
            if (c.getCode() != null) iso2ByName.put(key, c.getCode());
            if (c.getId() != null) {
                slugByName.put(key, SlugUtil.slugify(c.getName()) + "-" + c.getId());
            }
            if (turkish && c.getNameTr() != null && !c.getNameTr().isBlank()) {
                nameTrByName.put(key, c.getNameTr());
            }
        }

        List<FifaRankingResponse.Row> rows = all.stream()
                .map(r -> {
                    String name = r.getTeamName();
                    String key = name != null
                            ? name.toLowerCase(Locale.ROOT) : null;

                    // 1) DB-first: Country.flagUrl varsa onu kullan
                    String flag = key != null ? flagByName.get(key) : null;

                    // 2) Country.code (ISO2) varsa flagcdn fallback
                    if (flag == null && key != null) {
                        String iso2 = iso2ByName.get(key);
                        if (iso2 != null && !iso2.isBlank()) {
                            flag = "https://flagcdn.com/w160/"
                                    + iso2.toLowerCase(Locale.ROOT) + ".png";
                        }
                    }

                    // 3) FIFA-spesifik 3-harf code'u ISO2'ye map'le (England,
                    // Scotland gibi FIFA-uydurma kodlar icin)
                    if (flag == null && r.getCountryCode() != null) {
                        String iso2 = _fifaCountryCodeToIso2(r.getCountryCode());
                        if (iso2 != null) {
                            flag = "https://flagcdn.com/w160/"
                                    + iso2.toLowerCase(Locale.ROOT) + ".png";
                        }
                    }

                    String displayName = turkish && key != null
                            ? nameTrByName.getOrDefault(key, r.getTeamName())
                            : r.getTeamName();
                    // DB Country eslesmesi varsa /ulke linki slug'i; yoksa null.
                    String countrySlug = key != null ? slugByName.get(key) : null;
                    return new FifaRankingResponse.Row(
                            r.getRank(),
                            r.getPrevRank(),
                            r.getMovement(),
                            r.getTeamId(),
                            displayName,
                            r.getCountryCode(),
                            r.getConfederation(),
                            r.getConfederationId(),
                            r.getTotalPoints(),
                            r.getPrevPoints(),
                            r.getRatedMatches(),
                            flag,
                            countrySlug);
                })
                .collect(Collectors.toList());

        return new FifaRankingResponse(rows.size(), lastUpdated, rows);
    }

    /**
     * FIFA 3-harf code → ISO 3166-1 alpha-2. FIFA bazi kodlari kendine ozel
     * gonderir (ENG, SCO, WAL, NIR — UK alt-ulkeleri; SUI = Switzerland gibi).
     * En populer 80+ ulkeyi kapsiyoruz; eslemeyenler null doner ve flag yok.
     *
     * <p>Bilinmeyen ulkeler icin Country tablosundan Country.code lookup zaten
     * onceki adimda yapilir; bu sadece o da fail ederse devreye girer.
     */
    private static final Map<String, String> FIFA_TO_ISO2;
    static {
        Map<String, String> m = new HashMap<>();
        // UEFA
        m.put("ENG", "gb-eng"); m.put("SCO", "gb-sct");
        m.put("WAL", "gb-wls"); m.put("NIR", "gb-nir");
        m.put("ESP", "es"); m.put("FRA", "fr"); m.put("GER", "de");
        m.put("ITA", "it"); m.put("NED", "nl"); m.put("BEL", "be");
        m.put("POR", "pt"); m.put("TUR", "tr"); m.put("RUS", "ru");
        m.put("UKR", "ua"); m.put("POL", "pl"); m.put("CZE", "cz");
        m.put("SVK", "sk"); m.put("AUT", "at"); m.put("SUI", "ch");
        m.put("DEN", "dk"); m.put("SWE", "se"); m.put("NOR", "no");
        m.put("FIN", "fi"); m.put("ISL", "is"); m.put("IRL", "ie");
        m.put("GRE", "gr"); m.put("CRO", "hr"); m.put("SRB", "rs");
        m.put("ROU", "ro"); m.put("BUL", "bg"); m.put("HUN", "hu");
        m.put("SVN", "si"); m.put("BIH", "ba"); m.put("MKD", "mk");
        m.put("ALB", "al"); m.put("MNE", "me"); m.put("KOS", "xk");
        m.put("LUX", "lu"); m.put("MLT", "mt"); m.put("CYP", "cy");
        m.put("ISR", "il"); m.put("EST", "ee"); m.put("LAT", "lv");
        m.put("LTU", "lt"); m.put("MDA", "md"); m.put("BLR", "by");
        m.put("AND", "ad"); m.put("ARM", "am"); m.put("AZE", "az");
        m.put("GEO", "ge"); m.put("KAZ", "kz"); m.put("FRO", "fo");
        m.put("GIB", "gi"); m.put("LIE", "li"); m.put("MON", "mc");
        m.put("SMR", "sm"); m.put("VAT", "va");
        // CONMEBOL
        m.put("ARG", "ar"); m.put("BRA", "br"); m.put("URU", "uy");
        m.put("CHI", "cl"); m.put("COL", "co"); m.put("PER", "pe");
        m.put("ECU", "ec"); m.put("PAR", "py"); m.put("VEN", "ve");
        m.put("BOL", "bo");
        // CONCACAF
        m.put("USA", "us"); m.put("MEX", "mx"); m.put("CAN", "ca");
        m.put("CRC", "cr"); m.put("HON", "hn"); m.put("PAN", "pa");
        m.put("JAM", "jm"); m.put("SLV", "sv"); m.put("GUA", "gt");
        m.put("HAI", "ht"); m.put("CUB", "cu"); m.put("TRI", "tt");
        // AFC
        m.put("JPN", "jp"); m.put("KOR", "kr"); m.put("AUS", "au");
        m.put("IRN", "ir"); m.put("IRQ", "iq"); m.put("KSA", "sa");
        m.put("UAE", "ae"); m.put("QAT", "qa"); m.put("CHN", "cn");
        m.put("UZB", "uz"); m.put("JOR", "jo"); m.put("OMA", "om");
        m.put("BHR", "bh"); m.put("KUW", "kw"); m.put("SYR", "sy");
        m.put("LBN", "lb"); m.put("PLE", "ps"); m.put("YEM", "ye");
        m.put("IND", "in"); m.put("THA", "th"); m.put("VIE", "vn");
        m.put("INA", "id"); m.put("MAS", "my"); m.put("SIN", "sg");
        m.put("PHI", "ph"); m.put("MYA", "mm"); m.put("CAM", "kh");
        m.put("PRK", "kp"); m.put("HKG", "hk"); m.put("TPE", "tw");
        // CAF
        m.put("MAR", "ma"); m.put("EGY", "eg"); m.put("TUN", "tn");
        m.put("ALG", "dz"); m.put("LBY", "ly"); m.put("SEN", "sn");
        m.put("NGA", "ng"); m.put("GHA", "gh"); m.put("CIV", "ci");
        m.put("CMR", "cm"); m.put("RSA", "za"); m.put("MLI", "ml");
        m.put("BFA", "bf"); m.put("GUI", "gn"); m.put("CGO", "cg");
        m.put("COD", "cd"); m.put("ANG", "ao"); m.put("ZAM", "zm");
        m.put("KEN", "ke"); m.put("UGA", "ug"); m.put("ETH", "et");
        m.put("SUD", "sd"); m.put("RWA", "rw"); m.put("TAN", "tz");
        m.put("ZIM", "zw"); m.put("MOZ", "mz"); m.put("MAD", "mg");
        m.put("MTN", "mr"); m.put("GAM", "gm"); m.put("BEN", "bj");
        m.put("TOG", "tg"); m.put("CPV", "cv"); m.put("NAM", "na");
        m.put("MWI", "mw"); m.put("BDI", "bi"); m.put("GAB", "ga");
        m.put("EQG", "gq"); m.put("LES", "ls"); m.put("BOT", "bw");
        m.put("SWZ", "sz"); m.put("SLE", "sl"); m.put("LBR", "lr");
        m.put("CHA", "td"); m.put("CTA", "cf"); m.put("DJI", "dj");
        m.put("COM", "km"); m.put("ERI", "er"); m.put("STP", "st");
        m.put("SOM", "so"); m.put("SSD", "ss"); m.put("NIG", "ne");
        m.put("GNB", "gw"); m.put("SEY", "sc"); m.put("MRI", "mu");
        // OFC
        m.put("NZL", "nz"); m.put("FIJ", "fj"); m.put("PNG", "pg");
        m.put("SOL", "sb"); m.put("VAN", "vu"); m.put("TGA", "to");
        m.put("SAM", "ws"); m.put("TAH", "pf"); m.put("NCL", "nc");
        m.put("ASA", "as"); m.put("COK", "ck");
        FIFA_TO_ISO2 = Map.copyOf(m);
    }

    private static String _fifaCountryCodeToIso2(String fifaCode) {
        if (fifaCode == null) return null;
        return FIFA_TO_ISO2.get(fifaCode.toUpperCase(Locale.ROOT));
    }

    // ============================================================
    // UEFA Club
    // ============================================================

    @Cacheable(value = FootballCacheNames.RANKINGS,
            key = "'uefa-club-' + (#p0 == null ? 'cur' : #p0) "
                + "+ '-' + (#p1 == null ? 'all' : #p1) "
                + "+ '-' + (#p2 == null ? '' : #p2) "
                + "+ '-' + (#p3 ? 'tr' : 'en')")
    @Transactional(readOnly = true)
    public UefaClubRankingResponse uefaClubRanking(
            Integer season, String country, String search, boolean turkish) {
        Integer effectiveSeason = season != null
                ? season
                : uefaClubRepository.findLatestTargetSeasonYear();
        if (effectiveSeason == null) {
            return new UefaClubRankingResponse(0, null, null, List.of());
        }

        List<UefaClubRanking> all = (country != null && !country.isBlank())
                ? uefaClubRepository.findByTargetSeasonYearAndCountryCodeOrderByRankAsc(
                        effectiveSeason, country.trim().toUpperCase(Locale.ROOT))
                : uefaClubRepository.findByTargetSeasonYearOrderByRankAsc(effectiveSeason);

        if (search != null && !search.isBlank()) {
            String q = search.trim().toLowerCase(Locale.ROOT);
            all = all.stream()
                    .filter(r -> {
                        String name = r.getClubName() != null
                                ? r.getClubName().toLowerCase(Locale.ROOT) : "";
                        String off = r.getClubOfficialName() != null
                                ? r.getClubOfficialName().toLowerCase(Locale.ROOT) : "";
                        return name.contains(q) || off.contains(q);
                    })
                    .toList();
        }

        Instant lastUpdated = all.stream()
                .map(UefaClubRanking::getLastSyncedAt)
                .filter(java.util.Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);

        Map<String, String> clubCountryNameTr = new HashMap<>();
        if (turkish) {
            for (Country c : countryRepository.findAll()) {
                if (c.getName() == null || c.getNameTr() == null || c.getNameTr().isBlank()) continue;
                clubCountryNameTr.put(c.getName().toLowerCase(Locale.ROOT), c.getNameTr());
            }
        }
        List<UefaClubRankingResponse.Row> rows = all.stream()
                .map(r -> {
                    String cName = r.getCountryName();
                    if (turkish && cName != null) {
                        String tr = clubCountryNameTr.get(cName.toLowerCase(Locale.ROOT));
                        if (tr != null) cName = tr;
                    }
                    return new UefaClubRankingResponse.Row(
                            r.getRank(),
                            r.getClubId(),
                            r.getClubName(),
                            r.getClubShortName(),
                            r.getClubOfficialName(),
                            r.getTeamCode(),
                            r.getLogoUrl(),
                            r.getBigLogoUrl(),
                            r.getMediumLogoUrl(),
                            r.getCountryCode(),
                            cName,
                            r.getTotalPoints(),
                            r.getTrend(),
                            r.getNumberOfMatches(),
                            r.getNumberOfTeams(),
                            r.getBaseSeasonYear(),
                            r.getSeasonRankingsJson());
                })
                .collect(Collectors.toList());

        return new UefaClubRankingResponse(
                rows.size(), effectiveSeason, lastUpdated, rows);
    }

    // ============================================================
    // UEFA Country
    // ============================================================

    @Cacheable(value = FootballCacheNames.RANKINGS,
            key = "'uefa-country-' + (#p0 == null ? 'cur' : #p0) "
                + "+ '-' + (#p1 == null ? '' : #p1) "
                + "+ '-' + (#p2 ? 'tr' : 'en')")
    @Transactional(readOnly = true)
    public UefaCountryRankingResponse uefaCountryRanking(Integer season, String search, boolean turkish) {
        Integer effectiveSeason = season != null
                ? season
                : uefaCountryRepository.findLatestTargetSeasonYear();
        if (effectiveSeason == null) {
            return new UefaCountryRankingResponse(0, null, null, List.of());
        }

        List<UefaCountryRanking> all =
                uefaCountryRepository.findByTargetSeasonYearOrderByRankAsc(effectiveSeason);

        if (search != null && !search.isBlank()) {
            String q = search.trim().toLowerCase(Locale.ROOT);
            all = all.stream()
                    .filter(r -> {
                        String name = r.getCountryName() != null
                                ? r.getCountryName().toLowerCase(Locale.ROOT) : "";
                        String code = r.getCountryCode() != null
                                ? r.getCountryCode().toLowerCase(Locale.ROOT) : "";
                        return name.contains(q) || code.contains(q);
                    })
                    .toList();
        }

        Instant lastUpdated = all.stream()
                .map(UefaCountryRanking::getLastSyncedAt)
                .filter(java.util.Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);

        Map<String, String> countryNameTr = new HashMap<>();
        if (turkish) {
            for (Country c : countryRepository.findAll()) {
                if (c.getName() == null || c.getNameTr() == null || c.getNameTr().isBlank()) continue;
                countryNameTr.put(c.getName().toLowerCase(Locale.ROOT), c.getNameTr());
            }
        }
        List<UefaCountryRankingResponse.Row> rows = all.stream()
                .map(r -> {
                    String cName = r.getCountryName();
                    if (turkish && cName != null) {
                        String tr = countryNameTr.get(cName.toLowerCase(Locale.ROOT));
                        if (tr != null) cName = tr;
                    }
                    return new UefaCountryRankingResponse.Row(
                            r.getRank(),
                            r.getCountryUefaId(),
                            cName,
                            r.getCountryCode(),
                            r.getLogoUrl(),
                            r.getBigLogoUrl(),
                            r.getMediumLogoUrl(),
                            r.getTotalPoints(),
                            r.getTrend(),
                            r.getNumberOfMatches(),
                            r.getNumberOfTeams(),
                            r.getSeasonRankingsJson());
                })
                .collect(Collectors.toList());

        return new UefaCountryRankingResponse(
                rows.size(), effectiveSeason, lastUpdated, rows);
    }
}
