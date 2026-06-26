package com.scorestv.football.web.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.Set;

/**
 * Anasayfa fikstür listesindeki tek bir maçın özeti.
 *
 * <p>{@code Serializable} — yanıt Redis cache'inde (JDK serializer) tutulur.
 */
public record FixtureSummary(
        Long id,
        /** Maç detayı URL'si için slug: "{ev}-vs-{deplasman}-{id}". */
        String slug,
        /**
         * Bu macin hangi turnuvada oldugu (ev sahibi ligi/kupasi/UCL/UEL ...).
         * Takim sayfasinda farkli turnuvalardaki maclari ayirt etmek icin
         * gereklidir (ayni sezonda "Premier League Hafta 17" vs "FA Cup
         * Ceyrek Final" vs "Champions League Group Stage"). Null gelebilir
         * (eski cache'lerden migrasyon icin defansif).
         */
        LeagueRef leagueRef,
        /** Tur / aşama (örn. "Hafta 17", "Çeyrek Final"); dile göre çevrilmiş. */
        String round,
        Instant kickoff,
        /**
         * Bu maçın API'den en son senkronlandığı an. Frontend canlı dakikayı
         * pürüzsüz saydırmak için referans olarak kullanır.
         */
        Instant lastSyncedAt,
        Status status,
        Team homeTeam,
        Team awayTeam,
        Score score,
        Venue venue,
        /**
         * Ev/deplasman takımının bu maçta gördüğü KIRMIZI KART sayısı (0+).
         * Anasayfa canlı tab'ında takım adının yanında rozet olarak gösterilir.
         * Eski cache'lerden migrasyon için null gelebilir (frontend 0 sayar).
         */
        Integer homeRedCards,
        Integer awayRedCards
) implements Serializable {

    /**
     * Maca ait turnuva ozeti — frontend'in hangi yarismanin maci oldugunu
     * gostermesi ve detay sayfasina linklemesi icin.
     *
     * @param slug Lig detay sayfasi slug'i ({@code super-lig-203}). Frontend
     *             {@code /lig/} (TR) veya {@code /league/} (EN) prefix'ini ekler.
     * @param type "League" / "Cup" — dile cevrilmis ("Lig" / "Kupa").
     */
    public record LeagueRef(
            Long id,
            String name,
            String type,
            String logo,
            String slug
    ) implements Serializable {
    }

    /**
     * Maç durumu: kısa kod (NS/1H/HT/FT...), uzun metin, canlı dakika, uzatma.
     *
     * <p>{@code elapsed}/{@code extra} yalnız maç oynanırken anlamlıdır.
     * API maç bittikten sonra (FT/AET/PEN) bile bu alanları döndürmeye devam
     * eder; UI'da "90+1" gibi yanıltıcı göstergeler çıkmasın diye
     * {@link #of(String, String, Integer, Integer)} factory'si IN_PLAY
     * dışı statülerde bu iki alanı null'a indirir.
     */
    public record Status(
            String shortCode,
            String longText,
            Integer elapsed,
            Integer extra
    ) implements Serializable {

        /**
         * "Oynanıyor" sayılan statüler — elapsed/extra göstergesi yalnız bu
         * setteki kodlarda gösterilir. API-Football "In Play" kategorisi.
         */
        private static final Set<String> IN_PLAY_STATUSES = Set.of(
                "1H", "HT", "2H", "ET", "BT", "P", "SUSP", "INT", "LIVE");

        /**
         * Status üretici — {@code shortCode} IN_PLAY değilse elapsed/extra
         * null'a düşürülür (FT/NS/PST/CANC vb. için yanıltıcı gösterimi engeller).
         */
        public static Status of(String shortCode, String longText,
                                Integer elapsed, Integer extra) {
            boolean inPlay = shortCode != null && IN_PLAY_STATUSES.contains(shortCode);
            return new Status(
                    shortCode,
                    longText,
                    inPlay ? elapsed : null,
                    inPlay ? extra : null);
        }
    }

    /**
     * Takım özeti.
     *
     * @param slug Takım detay sayfası URL slug'ı ({@code besiktas-549}).
     *             Frontend prefix'i dile göre ekler: {@code /team/} veya {@code /takim/}.
     */
    public record Team(Long id, String name, String logo, String slug) implements Serializable {
    }

    /**
     * Anlık (canlı veya nihai) skor + periyot dökümleri.
     *
     * <p>{@code home}/{@code away} her zaman güncel toplam (canlıda dakikalık,
     * bittiğinde nihai). {@code halftime}/{@code extraTime}/{@code penalty}
     * sırasıyla İY, uzatma sonrası ve penaltı serisi skorları — yoksa
     * null. Frontend ScoreBreakdown şeridini bu alanlarla dolurur.
     */
    public record Score(
            Integer home, Integer away,
            Period halftime, Period extraTime, Period penalty
    ) implements Serializable {

        /** Backward-compatible eski 2'li constructor — sadece anlık skor verilince çağrılır. */
        public Score(Integer home, Integer away) {
            this(home, away, null, null, null);
        }

        /** Bir periyodun ev/deplasman skoru. */
        public record Period(Integer home, Integer away) implements Serializable {
        }
    }

    /** Stadyum özeti (bilinmiyorsa null). */
    public record Venue(String name, String city) implements Serializable {
    }
}
