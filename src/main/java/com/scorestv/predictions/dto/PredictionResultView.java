package com.scorestv.predictions.dto;

/**
 * Bir maçın sonuç tahmini dağılımı.
 *
 * @param home       ev sahibi kazanır oyu
 * @param draw       beraberlik oyu
 * @param away       deplasman kazanır oyu
 * @param total      toplam oy
 * @param myChoice   bu oylayanın seçimi ("HOME"/"DRAW"/"AWAY") veya null
 * @param votingOpen oylama açık mı (kickoff'tan önce true)
 */
public record PredictionResultView(
        int home,
        int draw,
        int away,
        int total,
        String myChoice,
        boolean votingOpen
) {}
