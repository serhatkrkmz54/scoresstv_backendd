package com.scorestv.football.web.dto;

import java.io.Serializable;
import java.util.List;

/**
 * Standings sayfasi hub yaniti — kullaniciya tum ligleri ulke bazinda
 * picker olarak sunmak icin. Standings detay sayfasina link verir.
 *
 * <p>Filter parametreleri: {@code ?country=}, {@code ?search=} ile daraltma.
 *
 * <p>TUM ligler doner (standings verisi olmasa bile). hasStandings bayragi
 * ile frontend "verisi var" / "verisi yok" gosterimini ayirir. Kullanici
 * verisi yok bir lige tikladiginda LeagueDetailLazySync devreye girer ve
 * arka planda standings'i ceker.
 */
public record LeagueHubResponse(
        /** Toplam dahil edilen lig sayisi (tum gruplar toplami). */
        int totalLeagues,
        /** Ulke bazinda gruplanmis ligler, alfabetik. */
        List<CountryGroup> countries
) implements Serializable {

    public record CountryGroup(
            /** Dile gore ulke adi (TR: "Türkiye", EN: "Turkey"). */
            String name,
            String code,
            String flag,
            List<LeagueRef> leagues
    ) implements Serializable {}

    public record LeagueRef(
            Long id,
            /** Dile gore ad (TR: name_tr, yoksa name). */
            String name,
            /** Lig detay slug — frontend /lig/ veya /league/ ile birlestirir. */
            String slug,
            String logo,
            /** Dile gore tip ("Lig"/"Kupa" veya "League"/"Cup"). */
            String type,
            /**
             * Raw league type — frontend bracket/standings ayrimi icin.
             * Genelde "League" (lig) veya "Cup" (kupa) doner.
             */
            String rawType,
            /** Mevcut sezon — UI default. */
            Integer currentSeason,
            /** Coverage bayragi — backend periyodik tazeleme yapiyor mu. */
            boolean covered,
            /**
             * Standings verisi DB'de mi — frontend gri/aktif ayrimi icin.
             * Verisi olmayan lige tiklanirsa lazy sync devreye girer.
             */
            boolean hasStandings
    ) implements Serializable {}
}
