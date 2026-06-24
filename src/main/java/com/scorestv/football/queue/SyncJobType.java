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
    LEAGUE_TEAMS_SYNC,

    // ============================================================
    // Basketbol job tipleri (futbol enum'larindan tamamen ayri executor
    // dispatch icin BASKETBALL_ prefix).
    // ============================================================

    /**
     * Bir basketbol ligi icin full info + sezonlar sync.
     * {@code /leagues?id=X}. Payload: {@code {leagueId}}.
     */
    BASKETBALL_LEAGUE_INFO_SYNC,

    /**
     * Bir basketbol ligi + sezon icin top players (3 kategori) + master
     * tablo + sezon stat sync. {@code /players?league=X&season=Y} sayfali
     * cagri. Payload: {@code {leagueId, season}}.
     */
    BASKETBALL_LEAGUE_TOP_PLAYERS_SYNC,

    /**
     * Bir basketbol oyuncusu icin profil + sezon istatistik sync.
     * {@code /players?id=X&season=Y}. Payload: {@code {playerId, leagueId,
     * season}} (leagueId opsiyonel).
     */
    BASKETBALL_PLAYER_PROFILE_SYNC,

    /**
     * Bir basketbol takimi icin tam profil sync.
     * {@code /teams?id=X}. Payload: {@code {teamId}}.
     */
    BASKETBALL_TEAM_PROFILE_SYNC,

    /**
     * Bir basketbol takimi + lig + sezon icin sezon istatistikleri sync.
     * {@code /teams/statistics?team=X&league=Y&season=Z}.
     * Payload: {@code {teamId, leagueId, season}}.
     */
    BASKETBALL_TEAM_STATS_SYNC,

    // ============================================================
    // Voleybol job tipleri (VOLLEYBALL_ prefix ile ayri executor dispatch).
    // Voleybol API LEANER — oyuncu/top-players job yok.
    // ============================================================

    /**
     * Bir voleybol ligi icin full info + sezonlar sync.
     * {@code /leagues?id=X}. Payload: {@code {leagueId}}.
     */
    VOLLEYBALL_LEAGUE_INFO_SYNC,

    /**
     * Bir voleybol takimi icin tam profil sync.
     * {@code /teams?id=X}. Payload: {@code {teamId}}.
     */
    VOLLEYBALL_TEAM_PROFILE_SYNC,

    /**
     * Bir voleybol takimi + lig + sezon icin sezon istatistikleri sync.
     * {@code /teams/statistics?team=X&league=Y&season=Z}.
     * Payload: {@code {teamId, leagueId, season}}.
     */
    VOLLEYBALL_TEAM_STATS_SYNC,

    /**
     * Bir voleybol ligi + sezon icin standings sync.
     * {@code /standings?league=X&season=Y}. Payload: {@code {leagueId, season}}.
     */
    VOLLEYBALL_STANDINGS_SYNC
}
