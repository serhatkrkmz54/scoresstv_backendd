-- V50 — Basketbol takım ↔ lig+sezon kalıcı üyelik junction tablosu.
-- Futbol V37'nin basketbol karşılığı.
--
-- AMAÇ
-- Bir basketbol takımının hangi lig+sezonda yer aldığını KESİN ve TAM olarak
-- bilmek. Mevcut akışta takım listesi basketball_games tablosundan DISTINCT
-- çekiliyor: sezon başlamadan games boş → takım listesi boş; partial games
-- bazı takımları kaçırır. API-Basketball /teams?league=X&season=Y endpoint'i
-- resmi kadroyu döner → bu junction'a yazılır.
--
-- DOLUMU
-- BasketballTeamSyncService.syncTeamsForLeague() çağrıldığında upserter
-- junction'a da yazar. Cron + startup runner günde bir kez covered ligler
-- için tetikler.
--
-- ESKİ VERİLER
-- Bu tablo boştan başlayacak; BasketballLeagueTeamsService fallback chain
-- içinde games kaynağına düşer, ardışık istekler arka planda /teams sync
-- tetikler ve junction kademeli dolar.

CREATE TABLE basketball_team_league_seasons (
    team_id BIGINT NOT NULL
        REFERENCES basketball_teams(id) ON DELETE CASCADE,
    league_id BIGINT NOT NULL
        REFERENCES basketball_leagues(id) ON DELETE CASCADE,
    /**
     * Basketbol sezonu — VARCHAR çünkü API "2024-2025" formatında döner
     * (futbolda integer year kullanılır, basketbolda iki yıl span).
     */
    season VARCHAR(20) NOT NULL,
    /**
     * /teams?league=X&season=Y sonucunun bu junction'a yazıldığı an.
     * Cron ile yeniden senkron yapan akışlar bu kolona bakıp
     * "X günden eski mi?" diye debounce yapabilir.
     */
    synced_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (team_id, league_id, season)
);

-- Hızlı sorgu: "lig X sezon Y'deki tüm takımlar"
CREATE INDEX idx_basketball_tls_league_season
    ON basketball_team_league_seasons (league_id, season);

-- Ters sorgu: "takım X'in oynadığı lig+sezon listesi" (gelecekteki team
-- detay sayfasında kullanılabilir, şimdilik kullanılmıyor)
CREATE INDEX idx_basketball_tls_team_season
    ON basketball_team_league_seasons (team_id, season);
