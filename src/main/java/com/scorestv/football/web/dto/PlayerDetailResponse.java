package com.scorestv.football.web.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Oyuncu detay sayfasi yaniti — tam paket.
 *
 * <p>Tek bir cagri ile su veriler doner:
 * <ul>
 *   <li>Profil (ad, foto, yas, milliyet, dogum, boy/kilo)</li>
 *   <li>Mevcut takim (DB'deki en son sezonun takimi)</li>
 *   <li>Sezon dropdown'u — kariyer takimlari'ndan + DB stats'tan union</li>
 *   <li>Secili sezonun istatistikleri — turnuva basina ayri satir
 *       (Sahin Konyaspor + Türkiye gibi ayni sezonda birden cok takim varsa
 *       hepsi listelenir)</li>
 *   <li>Kariyer takimlari (Sahin: Basaksehir 6 sezon + Konyaspor 8 sezon + ...)</li>
 *   <li>Sakatlık/cezalik gecmisi (tam kariyer)</li>
 *   <li>Transfer gecmisi (tam kariyer)</li>
 *   <li>Kupalar (player+coach merge — API ne donduruyorsa)</li>
 *   <li>SEO metadata</li>
 * </ul>
 */
public record PlayerDetailResponse(
        Long id,
        /** SEO slug — "isim-soyisim-id" (orn. "omer-ali-sahiner-50018"). */
        String slug,
        String name,
        String firstName,
        String lastName,
        Integer age,
        String nationality,
        /** Dile cevrilmis milliyet ("Türkiye" / "Turkey"). */
        String nationalityText,
        String photo,
        /** API "190 cm" gibi ham string. */
        String height,
        String weight,
        Boolean injured,
        BirthInfo birth,
        /**
         * Mevcut takim — DB'deki en son sezonun takimi. Birden cok ligde
         * oynuyorsa ilkini secer (kulup takimi tercih). Null olabilir
         * (emekli/transfer arasi).
         */
        TeamRef currentTeam,
        /** Cagrida secilmis sezon (?season= param ya da default current). */
        Integer selectedSeason,
        /** Tum sezonlar — careerTeams + DB stat'lardan union, yeni → eski. */
        List<Integer> seasons,
        /** Oyuncunun oynadigi tum takimlar + her takimda hangi yillarda. */
        List<CareerTeamView> careerTeams,
        /**
         * Secili sezonun istatistikleri — turnuva basina ayri satir (CL/Lig/
         * Kupa). Player team page'inde olan {@link PlayerSeasonStatView} ile
         * benzer yapida — burada da kategorize edilmis JSONB passthrough.
         */
        List<PlayerSeasonStatView> seasonStats,
        /** Sakatlık/cezalık gecmisi — tum kariyer, yeni → eski. */
        List<SidelinedRow> sidelined,
        /** Transfer kariyeri — tum hareketler, yeni → eski. */
        List<TransferRow> transfers,
        /** Kupalar — player+coach merged (ayni person id'de API zaten birlesik doner). */
        List<TrophyView> trophies,
        PlayerSeoResponse seo
) implements Serializable {

    /**
     * Oyuncu profil widget'inde takim referansi (mevcut takim, careerTeams,
     * seasonStats icindeki team). Slug ile frontend takim sayfasina linkler.
     */
    public record TeamRef(
            Long id,
            String name,
            String logo,
            String slug
    ) implements Serializable {}

    public record BirthInfo(
            LocalDate date,
            String place,
            String country,
            /** Dile cevrilmis ulke ("Türkiye" / "Turkey"). */
            String countryText
    ) implements Serializable {}

    /**
     * Bir oyuncunun bir takimda oynadigi tum sezonlar. Sezonlar dizi olarak
     * gelir; UI "Basaksehir: 2025, 2024, 2023, 2022, 2021, 2020" gibi gosterir.
     */
    public record CareerTeamView(
            TeamRef team,
            /** Sezon yili dizisi, yeni → eski. */
            List<Integer> seasons
    ) implements Serializable {}

    /**
     * Tek bir turnuvadaki sezonluk aggregated istatistik (player season stat).
     * Same shape as the team page version — JSONB passthrough.
     */
    public record PlayerSeasonStatView(
            Long teamId,
            String teamName,
            String teamLogo,
            String teamSlug,
            Long leagueId,
            String leagueName,
            String leagueLogo,
            String leagueSlug,
            /** Pozisyon — ham ("Attacker") + cevrilmis ("Forvet"). */
            String position,
            String positionText,
            /**
             * Ham JSONB passthrough — games/goals/shots/passes/tackles/duels/
             * dribbles/fouls/cards/penalty/substitutes alanlari.
             */
            Map<String, Object> stats
    ) implements Serializable {}

    /**
     * Tek bir sakatlik/cezalik kaydi (tarih dahil). Player sayfasinda tum
     * kariyer geçmisi gosterilir (team sayfasindaki gibi "aktif" filtreleme yok).
     */
    public record SidelinedRow(
            String type,
            String typeText,
            LocalDate start,
            LocalDate end
    ) implements Serializable {}

    /**
     * Tek bir transfer satiri. Direction "in" ya da "out" — oyuncunun bir
     * takimdan diger takima gecmesi. Karsi takim her iki yonu temsil edebilir.
     */
    public record TransferRow(
            LocalDate date,
            /** Ham tip — "Transfer" / "Loan" / "Free" / "€ 1.1M". */
            String type,
            /** Dile cevrilmis tip ("Kiralık" / "Bonservissiz" / "Transfer"). */
            String typeText,
            /** Hangi takimdan ayrildi. */
            TeamRef fromTeam,
            /** Hangi takima gitti. */
            TeamRef toTeam
    ) implements Serializable {}

    /**
     * Bir kupa kaydi (player+coach merge — Şahin gibi player→coach gecmis kisilerde
     * API hepsini birlikte doner).
     */
    public record TrophyView(
            String league,
            String leagueText,
            String country,
            String countryText,
            String season,
            /** Ham — "Winner" / "2nd Place". */
            String place,
            /** Cevrilmis — "Sampiyon" / "Ikinci". */
            String placeText
    ) implements Serializable {}
}
