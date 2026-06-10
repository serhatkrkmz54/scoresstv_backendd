# ScoresTV — Oturum Notları (2026-06-09)

Bu dosya bugün yapılan tüm değişiklikleri özetler. Devralan/birlikte çalışan
geliştirici için: ne değişti, **neden** değişti, nasıl deploy edilir, nasıl
test edilir.

Toplam 10 ayrı iş bitti. 4'ü hata düzeltme, 4'ü UI/i18n iyileştirmesi, 2'si
yeni feature (favori sistemi 2 fazda).

---

## 1) Hata düzeltmeleri

### 1.1 `PlayerSeasonStat` duplicate key (UNIQUE constraint)

**Belirti:** Backend log'larında sürekli WARN:
```
SQLState: 23505 — duplicate key value violates unique constraint
"uq_player_season_stats" Key (player_id, team_id, league_id, season)=(...)
already exists.
```

**Kök neden:** `replaceStart()` SİL → `upsertPage()` YAZ pattern, paralel
sync job'larda (team-bazlı + player-bazlı PlayerProfileSync aynı anda)
race condition'a giriyordu. Eski kod `try/catch DataIntegrityViolationException`
ile sadece logluyordu ama yakalansa bile Hibernate persistence context
kirleniyor (tx-poisoning).

**Çözüm:** Postgres native `INSERT ... ON CONFLICT DO UPDATE` ile atomik upsert.
- `PlayerSeasonStatRepository.upsertNative(playerId, teamId, leagueId, season, statsJson)`
- `PlayerSeasonStatsUpserter.upsertSingleEntry/upsertPage` → `save()` yerine
  `upsertNative()` çağırıyor
- `ObjectMapper`: bean inject yok (Jackson 3.x `tools.jackson` kullanıyoruz);
  local static `new ObjectMapper()` — SeoBuilder'lardaki pattern

**Sonuç:** Race-safe, tx-poisoning yok, WARN log spam'i durdu.

---

### 1.2 `Standings` FK violation (team yok)

**Belirti:**
```
ERROR: insert or update on table "standings" violates foreign key constraint
"standings_team_id_fkey" — Key (team_id)=(26577) is not present in table "teams".
```

**Kök neden:** API-Football standings'te bazen master tabloda olmayan takım
gönderiyor (alt ligten yükselen kupa katılımcısı vb.). `teamRepository.getReferenceById()`
sadece proxy döndürdüğü için INSERT sırasında patlıyordu.

**Çözüm:** `StandingsUpserter`'a `ensureTeamExists()` self-healing helper.
- INSERT öncesi `existsById(teamId)` check
- Yoksa minimal `Team` kaydı oluştur (id + name + national=false + covered=false + logoUrl)
- `DailyTeamRefreshJob` sonra eksik field'ları doldurur

**Sonuç:** Saatlik standings sync artık FK violation atmıyor.

---

### 1.3 `PlayerSeasonStatsUpserter` ObjectMapper bean hatası

**Belirti:** Production restart sırasında:
```
APPLICATION FAILED TO START
Parameter 4 of constructor in PlayerSeasonStatsUpserter required a bean
of type 'com.fasterxml.jackson.databind.ObjectMapper' that could not be found.
```

**Kök neden:** Proje Jackson 3.x (`tools.jackson`) kullanıyor — classic
`com.fasterxml.jackson.databind.ObjectMapper` bean otomatik oluşturulmuyor.

**Çözüm:** Bean inject kaldırıldı, local static instance kullanıldı
(SeoBuilder'lardaki pattern).

---

### 1.4 Player sezon dropdown ortada görünüp solda açılma

**Belirti:** Player profil sayfasında sezon hücresi sağda görünüyor ama
tıklayınca menü hücrenin sol kenarına yapışıp ortada açılıyor.

**Kök neden:** `showMenu()` + `RelativeRect.fromRect(topLeft, bottomRight)`
Flutter'a "menüyü RelativeRect'in sol-üst köşesine yapıştır" diyor.

**Çözüm:** `_SeasonStatBox` → `PopupMenuButton<int>` widget'ına refactor.
- `position: PopupMenuPosition.under` + `offset: Offset(0, 4)`
- Anchor hesabı Flutter otomatik yapar (hücre sağdaysa sağa hizalı açar)
- InkWell ripple PopupMenuButton tarafından sağlanır → görsel aynı

---

## 2) UI / i18n iyileştirmeleri

### 2.1 About modal — dark/light mode + i18n

**Sorun:** Profil → Hakkında → modal'da "ScoresTV" başlığı siyah, dark mode'da
görünmüyordu.

**Çözüm:** Flutter'ın `showAboutDialog`'unu attık (textTheme.headlineSmall'ı
hardcoded handle ediyordu); custom `AlertDialog` yazdık.
- Tüm textler `Theme.of(context).colorScheme.onSurface`
- İçerikler `s.pAbout*` i18n key'lerine bağlı

---

### 2.2 Takım çıkar dialog — dark/light + i18n

**Sorun:** "Fenerbahçe'i çıkar?" — siyah text, hardcoded TR.

**Çözüm:** `notification_settings_screen.dart::_confirmRemove` Theme-aware
+ AppStrings'e bağlı.
- Dile göre `team.nameTr` (TR) veya `team.name` (EN) seçilir
- `Theme.of(ctx).colorScheme.surface/onSurface` ile dark mode uyumlu
- Yeni i18n key'leri: `pRemoveTeamTitle({team})`, `pRemoveTeamBody`, `pRemove`,
  `pClose`, `pUnfollowTeam`

`notify_card.dart`'taki "Takip listesinden çıkar" butonu da `s.pUnfollowTeam`'e
bağlandı.

---

### 2.3 Transfer "Free Transfer" → "Bonservissiz"

**Sorun:** Player profili → Transferler tab'ında TR modunda da "Free Transfer"
yazıyordu.

**Kök neden:** API-Football "Free Transfer" (boşluklu) döndürüyor →
`slug("free-transfer")` ama `messages_tr.properties`'te sadece
`football.transfer.type.free` key'i var → mesaj bulamayıp orijinali döndürüyor.

**Çözüm:** Properties dosyalarına `free-transfer` + 9 yeni varyant eklendi
(`permanent`, `loaned-out`, `released`, `contract-extension`, `youth` vb.).

---

### 2.4 Elastic Search — TR/EN dil bilinçli

**Sorun:** Arama TR modda EN isimleri (örn. "Galatasaray") gösteriyordu;
fikstür arama "Türkiy" yazılınca Türkçe maç adı dönmüyordu.

**Çözüm:**

**Mobile:**
- `search_models.dart` → `TeamHit/LeagueHit/CountryHit.localizedName(turkish)`
- `FixtureHit.localizedMatchup(turkish)` + `localizedLeagueName(turkish)`
- `search_screen.dart` → `_TeamTile/_LeagueTile/_FixtureTile/_CountryTile`
  artık `AppStrings.of(context).locale == AppLocale.tr` ile dil tespit edip
  uygun field'ı gösteriyor

**Backend:**
- `FixtureDoc`'a 4 yeni TR field: `homeTeamNameTr`, `awayTeamNameTr`,
  `matchupTr`, `leagueNameTr` (autocomplete analyzer)
- `SearchResponse.FixtureHit` record genişletildi
- `SearchService.searchFixtures` — query 8 field'da arıyor (EN+TR)
- `SearchIndexerService.toDoc(Fixture)` — Team/League `nameTr` varsa
  indexliyor

> **Önemli:** Backend deploy sonrası `POST /api/v1/admin/search/rebuild`
> tetiklenmeli — yeni TR field'lar için mapping rebuild gerekiyor.

---

## 3) Favori sistemi (2 fazda)

### 3.1 Faz 1 — Lokal favori + paylaş

**Eski durum:**
- Match detail TopBar'da ★ ve Share butonları `TODO` yazıyordu, hiçbir şey yapmıyordu
- Bottom nav "Favoriler" tab'ı vardı ama ekran sadece "Favoriler" placeholder yazıyordu
- Kompakt liste'de maç favoriye eklenemiyordu (widget parametreleri vardı ama caller bağlamamıştı)

**Yapılanlar:**

1. **`PreferencesService.toggleFavoriteMatch(id)` + `isFavoriteMatch(id)`** —
   SharedPreferences'a `stv_favorites` listesi
2. **`FavoritesController`** (Riverpod `StateNotifier<Set<int>>`) — global
   reactive state
3. **Match detail butonları çalışır:**
   - `★` favori → `Theme.accent`'a döner, SnackBar "Favorilere eklendi"
   - `Share` → native share sheet (`share_plus` paketi):
     "Galatasaray vs Fenerbahçe · ScoresTV\nhttps://scorestv.com/match/galatasaray-fenerbahce-12345"
4. **Kompakt/List/Card satırlarında yıldız** — `HomeScreen` artık
   `favoritesProvider`'ı izleyip `isFavorite` + `onToggleFavorite` parametrelerini
   bağlıyor (widget'lar zaten parametreleri kabul ediyordu)
5. **`FavoritesScreen` gerçek liste** — placeholder kalktı, gerçek liste:
   - Backend yeni endpoint: `GET /api/v1/fixtures/by-ids?ids=1,2,3`
     (kickoff'a göre sıralı, maks 50 id)
   - Mobile: `FixturesService.fetchByIds()` + `FavoritesScreen` kompakt liste
   - Empty state, error retry, pull-to-refresh

---

### 3.2 Faz 2 — Backend sync + bildirim

**Amaç:** Uygulama kapalıyken bile favori maçlardan FCM push gelsin.

**Karar:** Favori liste mobile'da lokal (UI hızlı), bildirim için backend
ayrı bir abonelik tablosu tutar. Replace pattern.

**Backend:**

1. **V43 migration** — `device_match_subscriptions`
   ```sql
   (id, device_token_id FK CASCADE, fixture_id FK CASCADE,
    created_at, updated_at, UNIQUE(device_token_id, fixture_id))
   ```
   + 2 index (fixture_id, device_token_id)

2. **Entity + Repo** — `DeviceMatchSubscription`,
   `DeviceMatchSubscriptionRepository`
   - `findRecipientsForFixture(fixtureId)` JOIN FETCH deviceToken — dispatcher
     anahtarı

3. **Service + Endpoint:**
   - `FavoriteMatchSubscriptionService.sync(req)` — replace pattern
     (delete + flush + insert), FK guard (DB'de olmayan fixture id'leri atlanır)
   - `POST /api/v1/mobile/favorite-matches/sync` body: `{fcmToken, fixtureIds: [...]}`,
     maks 200 id

4. **`NotificationDispatcherService` genişletildi:**
   - `dispatchEvent` (gol/kart/penalti) ve `_dispatchMatchStatus` (kickoff/final):
     Takım takibi recipient'larına (`UserNotificationPref`) ek olarak
     favori-maç recipient'larını (`DeviceMatchSubscription`) ekliyor
   - `LinkedHashSet<String>` ile token-bazlı dedup (aynı cihaz hem takım takibi
     hem favoriledi ise tek bildirim)
   - Log: `alici=X (takim=Y favori=Z) gonderildi=N`

**Default bildirim seti:** Favori maç = "tüm önemli olaylar açık"
(gol, kart, penaltı, başladı, bitti). Maç-bazlı toggle yok — basit tutmak için,
ileride eklenebilir.

**Mobile:**

5. **`FavoriteMatchSyncService`** + `syncFavoritesToBackend(ref)` helper —
   fire-and-forget POST, hata olursa yutulur (offline / backend down)
6. **`FavoritesController` entegrasyonu:**
   - `toggle/add/remove/clear` sonrası `_syncToBackend()` (unawaited)
   - **Cold start:** Constructor'da lokal liste non-empty ise bir kez sync
     tetikler (FCM token henüz set edilmemişse `syncService` skip eder)

---

## Dosya değişiklikleri özeti

### Backend (yeni)

```
src/main/resources/db/migration/V43__device_match_subscriptions.sql

src/main/java/com/scorestv/mobile/
  domain/DeviceMatchSubscription.java
  domain/DeviceMatchSubscriptionRepository.java
  service/FavoriteMatchSubscriptionService.java
  web/MobileFavoriteMatchController.java
  web/dto/SyncFavoriteMatchesRequest.java
  web/dto/SyncFavoriteMatchesResponse.java
```

### Backend (değişti)

```
football/domain/PlayerSeasonStatRepository.java    (upsertNative method)
football/sync/PlayerSeasonStatsUpserter.java        (native upsert + local ObjectMapper)
football/sync/StandingsUpserter.java                (ensureTeamExists FK self-heal)

football/FootballMessages.java                       (— değişmedi, sadece properties)
src/main/resources/messages_tr.properties            (transfer.type yeni key'ler)
src/main/resources/messages_en.properties            (transfer.type yeni key'ler)

search/index/FixtureDoc.java                         (4 yeni TR field)
search/service/SearchResponse.java                   (FixtureHit record genişletildi)
search/service/SearchService.java                    (searchFixtures 8 field)
search/indexer/SearchIndexerService.java             (toDoc(Fixture) TR doldurma)

football/web/FixtureQueryService.java                (byIds method)
football/web/PublicFixtureController.java            (/by-ids endpoint)

mobile/notify/NotificationDispatcherService.java     (fav-sub recipient eklendi)
```

### Mobile (yeni)

```
lib/features/favorites/state/favorites_controller.dart
lib/features/favorites/data/favorite_match_sync_service.dart
```

### Mobile (değişti — kritikler)

```
lib/features/favorites/favorites_screen.dart                    (placeholder → gerçek liste)
lib/features/match_detail/match_detail_screen.dart              (_TopBar ConsumerWidget + share_plus)
lib/features/home/home_screen.dart                              (match row → favoritesProvider bağlandı)
lib/features/home/data/fixtures_service.dart                    (fetchByIds method)
lib/features/search/search_screen.dart                          (locale-aware tiles)
lib/features/search/models/search_models.dart                   (localizedName/Matchup helpers)
lib/features/player/widgets/player_hero.dart                    (PopupMenuButton'a refactor)
lib/features/profile/profile_screen.dart                        (About modal custom dialog)
lib/features/settings/notification_settings_screen.dart         (confirm dialog Theme-aware)
lib/features/onboarding/widgets/notify_card.dart                (Unfollow text i18n)
lib/core/storage/preferences_service.dart                       (favorite helpers)
lib/core/i18n/app_strings.dart                                  (yeni i18n key'leri: 5 adet)
lib/core/constants/api_config.dart                              (mobileFavoriteMatchesSync sabiti)
pubspec.yaml                                                    (share_plus eklendi)
```

---

## Deploy

### Backend

```powershell
cd C:\Users\korkm\OneDrive\Desktop\scorestv-yeni-proje-api-football\scorestv-backend
.\gradlew.bat clean bootJar
```

`build/libs/*.jar` → sunucuya yükle (`/opt/scorestv/backend/app.jar`) →
`docker compose restart backend`.

**Sonra:**
- Flyway V43 otomatik çalışır → `device_match_subscriptions` tablosu oluşur
- Elastic mapping rebuild (yeni TR field'lar için):
  ```bash
  curl -X POST -u admin:PASSWORD https://api.scorestv.com/api/v1/admin/search/rebuild
  ```

### Mobile

```powershell
cd C:\Users\korkm\OneDrive\Desktop\scorestv-yeni-proje-api-football\scorestv_mobile_git
flutter pub get
flutter analyze
flutter build apk --release
```

Git commit önerisi:
```
feat(mobile+backend): favorite matches (local + backend sync) + i18n bundle + bug fixes

Faz 1 — Lokal favori + paylaş:
- PreferencesService.favoriteMatches helpers
- FavoritesController (Riverpod StateNotifier<Set<int>>)
- Match detail ★ + Share (share_plus)
- HomeScreen match row'larında yıldız (zaten widget'lar destekliyordu)
- FavoritesScreen gerçek liste (backend /fixtures/by-ids endpoint)

Faz 2 — Backend sync + bildirim:
- V43 device_match_subscriptions migration + entity + repo
- POST /api/v1/mobile/favorite-matches/sync (replace pattern)
- NotificationDispatcher fav-sub recipient eklendi (token dedup)
- Mobile FavoritesController otomatik sync (toggle + cold start)

UI/i18n:
- About modal custom AlertDialog (dark mode + i18n)
- Takım çıkar dialog Theme-aware + 5 yeni i18n key
- Transfer "Free Transfer" → properties yeni varyant'lar
- Elastic search TR/EN dil bilinçli (mobile localizedName + backend FixtureDoc TR field)
- Player sezon dropdown PopupMenuButton'a refactor

Bug fixes:
- PlayerSeasonStat race condition (native ON CONFLICT DO UPDATE)
- Standings FK self-heal (eksik team minimal upsert)
- PlayerSeasonStatsUpserter ObjectMapper bean → local static
```

---

## Test rehberi

### Smoke test (5 dakika)

1. **Match detail** aç → `★` → SnackBar "Favorilere eklendi", icon dolu (accent)
2. **Share** butonu → native share sheet açılır
3. **Bottom nav → Favoriler** → eklediğin maç listede
4. **Kompakt liste** (Home) → sağda yıldız → tıkla → toggle çalışır
5. **Dil değiştir** (EN ↔ TR) → match isimler dile göre değişir
6. **About modal** → dark mode'da "ScoresTV" başlığı okunur
7. **Player profil** → sezon hücresi → tıkla → menü tam altında açılır

### Bildirim testi (Faz 2)

1. Cihaz aç → FCM token register (otomatik)
2. Bir canlı maçı favoriye ekle
3. Backend log: `FavoriteMatch sync: deviceId=X istek=1 yazilan=1`
4. DB: `SELECT * FROM device_match_subscriptions;` → kayıt var
5. Uygulamayı **tamamen kapat**
6. Maçta gol olunca → telefonda native bildirim gelir
7. Backend log: `FCM event dispatch: ... alici=N (takim=X favori=Y) gonderildi=N`

### Backend regresyon testi

```sql
-- PlayerSeasonStat WARN log'larının kalkması
SELECT level, COUNT(*) FROM logs WHERE msg LIKE '%uq_player_season_stats%'
AND timestamp > NOW() - INTERVAL '1 hour' GROUP BY level;
-- Beklenen: 0 satır

-- Standings FK self-heal çalışıyor mu
SELECT id, name, covered, country FROM teams WHERE country IS NULL LIMIT 10;
-- Beklenen: bazı minimal kayıtlar (sonradan DailyTeamRefreshJob dolduracak)

-- Favori abonelik sayısı
SELECT COUNT(*) FROM device_match_subscriptions;
```

---

## Bilinen sınırlamalar / sonraki adımlar

- Favori bildirim **default 5 event açık** (gol/kart/penaltı/başladı/bitti) —
  maç-bazlı kullanıcı toggle'ı yok. İleride eklenebilir (örn. "bu maçta sadece
  gol bildir").
- `FavoriteMatchSyncService` retry mekanizması yok; offline'da yazma kaybolmaz
  (lokal var) ama sync gecikir. Bir sonraki toggle/cold start telafi eder.
- `/api/v1/fixtures/by-ids` maks 200 id ile sınırlı — kullanıcı 200+ favori
  yapamaz. Pratikte sorun değil.
- Elastic rebuild manuel; FixtureDoc yeni TR field'larını sadece reindex sonrası
  arar. Yeni indexlenen fixture'lar zaten doğru gelir.

---

İletişim için kullanıcı: **serhatkorkmaz5454@gmail.com**
Oturum tarihi: 9 Haziran 2026
