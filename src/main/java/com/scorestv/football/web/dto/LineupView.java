package com.scorestv.football.web.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * Bir maçtaki tek bir takımın kadrosu (maç detay yanıtı için).
 *
 * <p>{@code announcedAt} — kadronun API'ye ilk düştüğü an. Frontend
 * {@code now() - announcedAt} ile "Kadro 2 saat önce açıklandı" gösterir.
 *
 * @param teamId       Hangi takımın kadrosu
 * @param formation    "4-3-3" / "4-3-1-2" gibi
 * @param coach        Teknik direktör; bilinmiyorsa null
 * @param colors       Takım renkleri (oyuncu + kaleci); bilinmiyorsa null
 * @param announcedAt  Kadronun ilk açıklandığı an
 * @param startXI      İlk 11 (saha sırasında: kaleci → defans → orta → forvet)
 * @param substitutes  Yedek oyuncular (API'den geldiği sırada)
 */
public record LineupView(
        Long teamId,
        String formation,
        Coach coach,
        TeamColors colors,
        Instant announcedAt,
        List<PlayerView> startXI,
        List<PlayerView> substitutes
) implements Serializable {

    public record Coach(Long id, String name, String photo) implements Serializable {
    }

    public record TeamColors(ColorSet player, ColorSet goalkeeper) implements Serializable {
    }

    /** Hex renk üçlüsü — frontend forma render için. */
    public record ColorSet(String primary, String number, String border) implements Serializable {
    }

    /**
     * @param id       API-Football player id
     * @param name     Oyuncu adı
     * @param number   Forma numarası
     * @param position "G" / "D" / "M" / "F"
     * @param grid     Saha üzerinde konum "X:Y"; yedekler için null
     */
    public record PlayerView(
            Long id,
            String name,
            Integer number,
            String position,
            String grid
    ) implements Serializable {
    }
}
