package com.scorestv.football.queue;

/**
 * Sync queue job tipleri. Her tip {@link SyncJobExecutor}'da bir handler'a
 * dispatch edilir. Payload alanlari tip basina farkli — JSONB icinde.
 */
public enum SyncJobType {

    /** Bir takimin guncel kadrosunu sync. Payload: {teamId, season}. */
    TEAM_SQUAD_SYNC,

    /** Bir takimin transferlerini sync. Payload: {teamId}. */
    TEAM_TRANSFERS_SYNC,

    /** Bir takimin koc verisini sync. Payload: {teamId}. */
    TEAM_COACH_SYNC,

    /** Bir takimin tum player stats'larini sync. Payload: {teamId, season}. */
    TEAM_PLAYER_STATS_SYNC,

    /** Bir oyuncunun profile + sezon stats. Payload: {playerId, season}. */
    PLAYER_PROFILE_SYNC,

    /** Bir oyuncunun kariyer takimlari. Payload: {playerId}. */
    PLAYER_CAREER_TEAMS_SYNC,

    /** Bir oyuncunun kupalari. Payload: {playerId}. */
    PLAYER_TROPHIES_SYNC,

    /** Bir oyuncunun transferleri. Payload: {playerId}. */
    PLAYER_TRANSFERS_SYNC,

    /** Bir oyuncunun sakatlik kayitlari. Payload: {playerId}. */
    PLAYER_SIDELINED_SYNC,

    /**
     * Bir lig icin tum oyuncularin sezonluk dump'i (paginated).
     * Payload: {leagueId, season, page}. Worker page'i artirip yeni
     * job kuyruga atar.
     */
    LEAGUE_PLAYERS_DUMP,

    /** Bir lig'in standings'i. Payload: {leagueId, season}. */
    LEAGUE_STANDINGS_SYNC,

    /** Bir lig'in top scorers/assists/cards. Payload: {leagueId, season, category}. */
    LEAGUE_TOP_PLAYERS_SYNC,

    /**
     * Bir lig+sezon icin resmi takim kadrosu. {@code /teams?league=X&season=Y}
     * cagrisi yapar; sonuc {@code team_league_seasons} junction tablosuna
     * yazilir. Onboarding'de favori takim secimi bu junction'dan beslenir.
     * Payload: {leagueId, season}.
     */
    LEAGUE_TEAMS_SYNC
}
