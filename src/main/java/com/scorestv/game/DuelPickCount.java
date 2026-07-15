package com.scorestv.game;

/** Düello başına taraf (A/B) tahmin sayısı — canlı dağılım için. */
public interface DuelPickCount {
    Long getDuelId();
    String getPick();
    Long getCnt();
}
