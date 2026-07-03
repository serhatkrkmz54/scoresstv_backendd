package com.scorestv.football.web.dto;

import java.io.Serializable;
import java.util.List;

/**
 * "Canlı Maç Programı / Hangi Kanalda" hub sayfası yanıtı — bir günün futbol
 * maçları, lige göre gruplu ve her lig için varsayılan TV kanal(lar)ı ile.
 *
 * <p>Web'deki haftalık {@code /canli-mac-programi} SEO hub'ı bu yanıta bağlanır;
 * gün gün ({@code date}) çekilir. Slug'lar maç detay/canonical ile birebir
 * uyumludur (link kırılmaz).
 *
 * @param date    sorgulanan tarih (ISO, yyyy-MM-dd)
 * @param leagues lig lig gruplanmış maçlar (kanalı olan ligler üstte)
 */
public record TvGuideResponse(
        String date,
        List<League> leagues
) implements Serializable {

    /**
     * Bir lig ve o günkü maçları + o lig için (grup başına bir kez çözülen)
     * varsayılan TV kanal adları.
     *
     * @param name     lig adı (dile göre)
     * @param slug     lig sayfası slug'ı ("super-lig-203")
     * @param logoUrl  lig logosu (CDN); aynalanmadıysa null
     * @param country  lig ülkesi (dile göre)
     * @param channels varsayılan kanal adları (ilk 3, tekrarsız); boş olabilir
     * @param matches  lig içi kickoff ASC sıralı maçlar
     */
    public record League(
            String name,
            String slug,
            String logoUrl,
            String country,
            List<String> channels,
            List<Match> matches
    ) implements Serializable {
    }

    /**
     * Tek bir maç özeti — hub satırı için gereken minimal alanlar.
     *
     * @param slug       maç detay slug'ı (İngilizce adlarla; canonical ile aynı)
     * @param kickoff    başlangıç anı (ISO Instant.toString())
     * @param status     API durum kodu ("NS", "1H", "FT" ...)
     * @param statusText durum uzun metni (dile göre)
     * @param homeName   ev sahibi adı (dile göre)
     * @param homeSlug   ev sahibi takım slug'ı
     * @param homeLogo   ev sahibi logosu (CDN); null olabilir
     * @param homeScore  ev sahibi gol (yoksa null)
     * @param awayName   deplasman adı (dile göre)
     * @param awaySlug   deplasman takım slug'ı
     * @param awayLogo   deplasman logosu (CDN); null olabilir
     * @param awayScore  deplasman gol (yoksa null)
     */
    public record Match(
            String slug,
            String kickoff,
            String status,
            String statusText,
            String homeName,
            String homeSlug,
            String homeLogo,
            Integer homeScore,
            String awayName,
            String awaySlug,
            String awayLogo,
            Integer awayScore
    ) implements Serializable {
    }
}
