-- V38 — Cup haric tum ligleri covered=true isaretle.
--
-- AMAÇ
-- Mobile favori takim secimi (ve diger lig-bazli ozellikler) tum League
-- tipi liglerde TAM takim listesi ile calissin. Manuel ADMIN secimine
-- bagli kalan eski model olceklenmiyordu — yeni bir ulkenin ligini eklemek
-- icin tek tek covered=true isaretlemek gerekiyordu.
--
-- ETKİ
-- AutoEnqueueScheduler.dailyCoveredTeamsRoster() bu sayede her gece tum
-- League ligleri icin /teams sync enqueue eder; junction (team_league_seasons)
-- kademeli dolar. Tum bunlar SyncQueueWorker uzerinden kota-aware islenir.
--
-- CUP NEDEN DAHIL DEGIL
-- Kupalarda takim kadrosu eleme usulu (sezon basinda sabit degil); /teams
-- API'sinden gelse bile mobile favori takim secimi icin anlamli degil
-- (mobile zaten Cup'lari MobileLeagueController'da filtreliyor). Web tarafi
-- Cup standings/detayini lazy sync ile alir — covered olmadan da calisir.
--
-- GERI ALMA
-- Gerekirse: UPDATE leagues SET covered = false WHERE type = 'League';
-- veya admin PUT /coverage/all?covered=false

UPDATE leagues
SET covered = true
WHERE type != 'Cup' OR type IS NULL;
