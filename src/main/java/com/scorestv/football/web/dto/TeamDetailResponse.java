package com.scorestv.football.web.dto;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Takim detay sayfasi yaniti — "tam paket" kapsam.
 *
 * <p>Tek bir cagri ile su veriler doner:
 * <ul>
 *   <li>Takimin temel bilgileri + stadyumu + ulkesi</li>
 *   <li>Sezon dropdown'u (mevcut + son 3 sezon eager, oncesi lazy)</li>
 *   <li>Secili sezonun istatistikleri (oynadigi tum ligler icin)</li>
 *   <li>Secili sezon kadrosu (squad)</li>
 *   <li>Son oynanan {@link #recentFixtures()} ve gelecek {@link #upcomingFixtures()}</li>
 *   <li>Takimin secili sezonda oldugu liglerdeki puan durumundaki yeri</li>
 *   <li>Son transferler (giris + cikis)</li>
 *   <li>Mevcut kocun bilgileri + bu takimdaki donemi + kupalari</li>
 *   <li>Kadrodaki AKTIF sakat/cezali oyuncular</li>
 *   <li>SEO metadata</li>
 * </ul>
 *
 * <p>Frontend sezon degistirdiginde {@code ?season=} ile yeniden cagirir.
 * {@code selectedSeason} cagrida bulunulan sezonu doner — frontend dropdown
 * vurgusu icin kullanir.
 */
public record TeamDetailResponse(
        Long id,
        /** SEO slug — "besiktas-549" (TR ve EN ayni — id sondaki anchor). */
        String slug,
        /** Dile gore takim adi (TR ise name_tr, yoksa name). */
        String name,
        /** Takimin ham (ingilizce) adi — SEO/og:title icin yararli. */
        String nameRaw,
        String logo,
        Integer founded,
        boolean national,
        String code,
        /** Milli takımın FIFA sırası; kulüp/eşleşme yoksa null. */
        Integer fifaRank,
        CountryInfo country,
        VenueInfo venue,
        /**
         * Cagrida secilmis sezon (?season= param ya da mevcut sezon).
         * Frontend dropdown vurgusu icin kullanir.
         */
        Integer selectedSeason,
        /** Tum sezonlar, yeni → eski. UI dropdown'da listelenir. */
        List<SeasonOption> seasons,
        /**
         * Secili sezonda takimin oynadigi tum liglerdeki istatistikler.
         * Bos liste = sezon icin istatistik henuz olusmamis.
         */
        List<StatisticsByLeague> statistics,
        /** Secili sezon kadrosu (kaleci, defans, orta saha, forvet grupli). */
        List<SquadGroup> squad,
        /** Son N oynanan mac (yeni → eski). */
        List<FixtureSummary> recentFixtures,
        /** Gelecek N mac (yakin → uzak). */
        List<FixtureSummary> upcomingFixtures,
        /**
         * Secili sezonda takimin yer aldigi liglerdeki puan durumu siralamasi.
         * Lig basina tek satir (takim satiri); siralama bilgisi rank + zone.
         */
        List<StandingsPosition> standingsPositions,
        /** Son transferler — hem giris hem cikis, kronolojik yeni → eski. */
        List<TransferRow> transfers,
        /** Mevcut bas antrenor — career'inde bu takim icin end=null + en yeni start. */
        CoachInfo currentCoach,
        /** Kadrodaki AKTIF sakat/cezali oyuncular (end_date >= today veya null). */
        List<SidelinedRow> sidelined,
        /**
         * Oyuncu sezonluk aggregated istatistikleri. Bir oyuncu ayni sezonda
         * birden fazla turnuvada oynayabilir (Lig + Kupa + CL); her turnuva
         * ayri satir doner. Frontend playerId + leagueId ile gruplayip
         * gosterir. {@code stats} JSONB passthrough — games/goals/assists/
         * minutes/rating/passes/shots/tackles/dribbles/cards/penalty alanlari.
         */
        List<PlayerSeasonStatView> playerStats,
        TeamSeoResponse seo
) implements Serializable {

    public record CountryInfo(
            String name,
            String code,
            String flag
    ) implements Serializable {}

    public record VenueInfo(
            Long id,
            String name,
            String city,
            String address,
            Integer capacity,
            String surface,
            String image
    ) implements Serializable {}

    /** Sezon dropdown opsiyonu — yil + opsiyonel coverage hint. */
    public record SeasonOption(
            Integer year,
            LocalDate startDate,
            LocalDate endDate,
            boolean current
    ) implements Serializable {}

    /**
     * Bir lig icin sezon istatistikleri. {@code stats} JSONB passthrough —
     * API-Football {@code /teams/statistics} yanitinin response[] elementinin
     * birebir aynisi (form/fixtures/goals/biggest/clean_sheet/penalty/lineups/cards).
     * Frontend bunu zenginlestirilmis component'lerde gosterir.
     */
    public record StatisticsByLeague(
            Long leagueId,
            String leagueName,
            String leagueLogo,
            String leagueSlug,
            /** Ham JSON passthrough — API'nin response objesi (form/fixtures/goals/...). */
            Map<String, Object> stats
    ) implements Serializable {}

    /**
     * Kadro pozisyona gore gruplu. {@code position} ham deger ("Goalkeeper",
     * "Defender", "Midfielder", "Attacker"); {@code positionText} dile cevrilmis.
     */
    public record SquadGroup(
            String position,
            String positionText,
            List<SquadPlayer> players
    ) implements Serializable {}

    public record SquadPlayer(
            Long playerId,
            String name,
            Integer age,
            Integer number,
            String position,
            String photo
    ) implements Serializable {}

    /**
     * Takimin bir ligdeki konumu (siralama satiri). UI "X. lig icinde Y. sirada"
     * gibi gosterim icin. Lig basina tek satir.
     */
    public record StandingsPosition(
            Long leagueId,
            String leagueName,
            String leagueLogo,
            String leagueSlug,
            String groupName,
            String groupNameText,
            /** Takimin lig icindeki sirasi. */
            Integer rank,
            Integer points,
            Integer goalsDiff,
            Integer played,
            Integer win,
            Integer draw,
            Integer lose,
            String form,
            String description,
            String descriptionText
    ) implements Serializable {}

    /**
     * Tek bir transfer satiri. Direction "in" (gelen) ya da "out" (giden).
     * Aktarilan oyuncu + karsi takim + tarih + tip.
     */
    public record TransferRow(
            LocalDate date,
            /** "in" veya "out". */
            String direction,
            /** Ham tip — "Transfer" / "Loan" / "Free" / "N/A" / "€ 1.1M" vb. */
            String type,
            /**
             * Dile cevrilmis tip ("Kiralık" / "Bonservissiz" / "Transfer" /
             * "Bilinmiyor"). Para birimi degerleri evrenseldir, dokunulmaz —
             * {@code typeText} icin ayni deger doner ("€ 1.1M").
             */
            String typeText,
            Long playerId,
            String playerName,
            /** Karsi takim — direction=in icin gonderen, direction=out icin alan. */
            Long counterpartyTeamId,
            String counterpartyTeamName,
            String counterpartyTeamLogo,
            String counterpartyTeamSlug
    ) implements Serializable {}

    /**
     * Mevcut bas antrenor — career'inde bu takim icin end=null entry'ye
     * sahip + en yeni start_date kuralina gore secilir. Birden cok coach
     * teknik kadroda yer alsa bile yalniz biri "bas antrenor" sayilir.
     */
    public record CoachInfo(
            Long coachId,
            String name,
            String firstName,
            String lastName,
            Integer age,
            String nationality,
            String photo,
            BirthInfo birth,
            /** Bu takimdaki donem(ler)i (start → end). Birden fazla giris/cikis olabilir. */
            List<CareerEntry> careerWithTeam,
            /** Kocun TUM kariyer kupalari. */
            List<TrophyEntry> trophies
    ) implements Serializable {}

    public record BirthInfo(
            LocalDate date,
            String place,
            String country
    ) implements Serializable {}

    public record CareerEntry(
            LocalDate start,
            LocalDate end
    ) implements Serializable {}

    public record TrophyEntry(
            /** Ham lig/turnuva adi ("Bundesliga", "DFB Pokal", "UEFA Champions League"). */
            String league,
            /**
             * Dile cevrilmis lig adi — countries/leagues tablomuzda match varsa
             * name_tr'den; yoksa kaynak metin doner. Ornek: "Bundesliga" →
             * "Bundesliga" (cogu lig adi TR'de ayni), "Cup" → "Türkiye Kupasi".
             */
            String leagueText,
            /** Ham ulke adi ("Turkey", "Germany"). */
            String country,
            /**
             * Dile cevrilmis ulke adi — countries.name_tr'den ("Turkey" →
             * "Türkiye", "Germany" → "Almanya"). Match yoksa kaynak metin.
             */
            String countryText,
            String season,
            /** "Winner" / "2nd Place" / vb. Ham deger. */
            String place,
            /** Dile cevrilmis ("Sampiyon" / "Ikinci"). */
            String placeText
    ) implements Serializable {}

    /**
     * Tek bir oyuncunun sezonluk istatistik satiri (bir turnuvada). Ayni
     * oyuncu ayni sezonda birden fazla turnuvada oynayabilir — her birinin
     * ayri satiri vardir; frontend playerId ile gruplayabilir.
     */
    public record PlayerSeasonStatView(
            Long playerId,
            String playerName,
            String playerPhoto,
            Long leagueId,
            String leagueName,
            String leagueLogo,
            String leagueSlug,
            /** Pozisyon — ham ("Attacker") + dile cevrilmis ("Forvet"). */
            String position,
            String positionText,
            /**
             * Ham JSONB passthrough. API'nin statistics[] elementinin tam
             * icerigi: {@code games} {appearences, lineups, minutes, rating,
             * captain, number}, {@code goals} {total, assists, conceded,
             * saves}, {@code shots} {total, on}, {@code passes} {total, key,
             * accuracy}, {@code tackles} {total, blocks, interceptions},
             * {@code duels} {total, won}, {@code dribbles} {attempts, success},
             * {@code fouls} {drawn, committed}, {@code cards} {yellow,
             * yellowred, red}, {@code penalty} {won, commited, scored, missed,
             * saved}, {@code substitutes} {in, out, bench}.
             */
            Map<String, Object> stats
    ) implements Serializable {}

    /** Tek bir aktif sakatlik/cezalik kaydi (kadrodaki oyuncu icin). */
    public record SidelinedRow(
            Long playerId,
            String playerName,
            String playerPhoto,
            /** "Hip/Thigh Injury" / "Suspended" — ham. */
            String type,
            /** Dile cevrilmis sakatlik tipi. */
            String typeText,
            LocalDate start,
            LocalDate end
    ) implements Serializable {}
}
