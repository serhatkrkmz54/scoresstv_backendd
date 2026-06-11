package com.scorestv.bilyoner;

import java.io.Serializable;
import java.util.List;

/**
 * Bir maça ait bahis oranları (Bilyoner). Mobile match-detail yanıtında döner.
 *
 * <p>{@code markets}: gösterilecek market listesi (Maç Sonucu, Karşılıklı Gol,
 * 2.5 Alt/Üst ...). Her market'in seçenekleri ({@code outcomes}) label + oran.
 * {@code clickUrl}: "Bilyoner'de oyna" butonunun açacağı affiliate linki.
 */
public record MatchOdds(
        String provider,
        String clickUrl,
        List<Market> markets
) implements Serializable {

    public record Market(String name, List<Outcome> outcomes) implements Serializable {}

    public record Outcome(String label, String odd) implements Serializable {}
}
