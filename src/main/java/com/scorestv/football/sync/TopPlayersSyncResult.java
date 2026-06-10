package com.scorestv.football.sync;

import com.scorestv.football.domain.LeagueTopPlayer.Category;

/** Top players sync isleminin kisa sonucu. */
public record TopPlayersSyncResult(
        Long leagueId,
        Integer season,
        Category category,
        int written
) {
}
