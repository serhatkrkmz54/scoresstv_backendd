-- V37 — Takim ↔ lig+sezon kalici uyelik junction tablosu.
--
-- AMAÇ
-- Bir takimin hangi lig+sezonda yer aldigini KESIN ve TAM olarak bilmek.
-- Mevcut akista takim listesini fixtures tablosundan DISTINCT cekmek
-- yetersiz: sezon baslamadan fixtures bos, partial fixtures eksik takim
-- gosterir vs. API-Football /teams?league=X&season=Y endpoint'i resmi
-- kadroyu doner — bu sonuc bu junction'a yazilir.
--
-- DOLUMU
-- TeamSyncService.syncLeague() cagrildiginda upserter junction'a da yazar.
-- Boylece "lig X sezon Y'deki takimlar" hizli ve tam bir sorgu olur.
--
-- ESKI VERILER
-- Bu tablo bostan baslayacak; LeagueTeamsService fallback chain icinde
-- standings ve fixtures kaynaklarina dusulur, ardisik istekler arkaplanda
-- /teams sync tetikler ve junction kademeli dolar.

CREATE TABLE team_league_seasons (
    team_id BIGINT NOT NULL REFERENCES teams(id) ON DELETE CASCADE,
    league_id BIGINT NOT NULL REFERENCES leagues(id) ON DELETE CASCADE,
    season INT NOT NULL,
    /**
     * /teams?league=X&season=Y sonucunun bu junction'a yazildigi an.
     * Cron ile yeniden senkron yapan akislar bu kolona bakip
     * "X gunden eski mi?" diye debounce yapabilir.
     */
    synced_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (team_id, league_id, season)
);

-- Hizli sorgu: "lig X sezon Y'deki tum takimlar"
CREATE INDEX idx_team_league_seasons_league_season
    ON team_league_seasons (league_id, season);

-- Ters sorgu: "takim X'in oynadigi lig+sezon listesi" (gelecekteki team
-- detay sayfasinda kullanilabilir, simdilik kullanilmiyor)
CREATE INDEX idx_team_league_seasons_team_season
    ON team_league_seasons (team_id, season);
