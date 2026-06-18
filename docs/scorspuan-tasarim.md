# ScorsPuan — Tasarım & Plan Dökümanı

> Durum: TASLAK (yalnızca tasarım — henüz kod yok)
> Kapsam kararı: önce plan dökümanı → sonra kademeli inşa
> Ödül modeli: Faz 1–3 statü + kozmetik · Faz 4 gerçek ödül/çekiliş (yasal süreçle)

---

## 1. Amaç ve İlkeler

ScorsPuan, ScoresTV'de kullanıcıyı **günlük geri getiren** ve platformu rakiplerden **farklılaştıran** bir oyunlaştırma/sadakat katmanıdır. Hedef: etkileşim ve elde tutma (retention) — **bahis/iddaa değildir.**

İlkeler:

- **Kumar değil:** para yok, stake (yatırım) yok, puan **nakde çevrilemez**, satın alınamaz. "Oran / kupon / stake / ödeme / bahis" dili kullanılmaz. Tahmin = ücretsiz "free‑to‑play".
- **Sunucu‑otoriter:** puan asla istemciden (web/mobil) gelmez. Tüm ödüller backend'de, mevcut domain olayları tetiklenince verilir.
- **Idempotent ve denetlenebilir:** her ödül append‑only bir defter (ledger) satırıdır; aynı aksiyon iki kez puanlanmaz; her şey geri izlenebilir / geri alınabilir.
- **Suistimale dayanıklı:** kaynak başına günlük tavan, cooldown, içerik kalite eşiği, flag'lenen içerikten puan iptali.
- **Üyelik şart:** misafir/anonim kullanıcı puan kazanmaz.

---

## 2. Puan Kazanma Kaynakları

Önerilen başlangıç değerleri (config'ten ayarlanabilir — bkz. `scors_point_rule`). "Günlük tavan" = o kaynaktan bir günde kazanılabilecek azami puan.

### 2.1 Alışkanlık / Sadakat

| ruleKey | Açıklama | Puan | Günlük tavan | Idempotency anahtarı |
|---|---|---|---|---|
| `DAILY_LOGIN` | Günde ilk giriş | 10 | 10 | `DAILY_LOGIN:{yyyy-mm-dd}` |
| `LOGIN_STREAK` | Seri kilometre taşı (3/7/30 gün) | +20 / +50 / +200 | — | `LOGIN_STREAK:{milestone}:{donem}` |
| `EMAIL_VERIFIED` | E‑posta doğrulama | 50 | tek sefer | `EMAIL_VERIFIED` |
| `PROFILE_COMPLETED` | Profil tamamlama | 50 | tek sefer | `PROFILE_COMPLETED` |
| `PUSH_ENABLED` | Mobil bildirim izni | 30 | tek sefer | `PUSH_ENABLED` |

### 2.2 Tahmin (merkez parça — bkz. §3)

| ruleKey | Açıklama | Puan |
|---|---|---|
| `PREDICTION_MADE` | Tahmin yapma (maç başına 1) | 5 |
| `PREDICTION_RESULT` | Doğru sonuç (1X2) | 20 |
| `PREDICTION_DIFF` | Doğru gol farkı (sonuç da doğruysa) | +10 |
| `PREDICTION_EXACT` | Tam skor | 50 (toplam) |
| `PREDICTION_STREAK` | Art arda doğru tahmin bonusu | +5 / +10 / … |

### 2.3 İçerik / Etkileşim

| ruleKey | Açıklama | Puan | Günlük tavan | Not |
|---|---|---|---|---|
| `COMMENT_CREATED` | Yorum yazma | 5 | 25 | min uzunluk (örn. 15 karakter); banned‑word filtresinden geçmeli |
| `COMMENT_LIKED` | Yorumuna beğeni gelmesi | 2 | 50 | beğenen ≠ yazar |
| `FAVORITE_ADDED` | Favori takım/oyuncu ekleme | 5 | ilk 10 favori | favori silinip eklenerek farm'lanamaz (kalıcı dedup) |
| `MATCH_FOLLOWED` | Maç takibi | 2 | 20 | — |

### 2.4 Sosyal / Büyüme

| ruleKey | Açıklama | Puan | Not |
|---|---|---|---|
| `REFERRAL_INVITER` | Davet edilen aktifleşince davet edene | 100 | davet edilen e‑posta doğrulayınca tetiklenir (sahte hesap farmına karşı) |
| `REFERRAL_INVITEE` | Davetle gelene karşılama | 50 | tek sefer |
| `SHARE` | Maç/haber paylaşımı | 5 | günlük tavan 15 |
| `POLL_VOTE` | Anket/oylama (MVP, kim kazanır) | 3 | anket başına 1 |
| `QUIZ_CORRECT` | Mini bilgi yarışması doğru cevap | 10 | quiz başına tavan |

### 2.5 Kilometre Taşları

Rozetler (badge) ve seviye atlamaları puan vermez; **statü** kazandırır (bkz. §5, §7).

---

## 3. Tahmin Motoru (Prediction Engine)

Skor sitesinin günlük‑dönüş motoru budur. Mantık:

- **Tahmin penceresi:** kullanıcı bir fikstür için `(ev skoru, deplasman skoru)` girer. Tahmin **`fixture.kickoffAt` geçtiğinde KİLİTLENİR** — sonra ne yeni tahmin ne değişiklik kabul edilir.
- **Puanlama (maç bitince):** mevcut "biten maç" sync'i (`FinishedMatchFinalSyncJob`) bir fikstürü `FT/AET/PEN` durumuna geçirince bir **settle job** o fikstürün tüm tahminlerini hesaplar:
  - Tam skor doğru → `PREDICTION_EXACT` (50)
  - Sonuç (1X2) doğru → `PREDICTION_RESULT` (20); gol farkı da doğruysa `PREDICTION_DIFF` (+10)
  - Yanlış → 0
  - (Opsiyonel) **sürpriz çarpanı:** standings'e göre alt sıradaki takım kazandıysa ×1.5
  - (Opsiyonel) **seri bonusu:** art arda doğru tahminde artan bonus
- **Idempotency:** her tahmin **bir kez** puanlanır (`scors_prediction.settled = true` + ledger unique anahtarı). Settle job tekrar çalışsa çift puan vermez.
- **Kapsam kararı (açık soru):** tüm maçlar mı yoksa yalnızca kapsadığımız ligler mi? Öneri: başlangıçta popüler ligler + Dünya Kupası.

Örnek:

```
Maç gerçek: 2-1 (ev kazandı, fark +1)
Kullanıcı A: 2-1  → EXACT  = 50  (+ varsa diff/sonuç içinde)
Kullanıcı B: 3-2  → RESULT (ev kazandı) + DIFF (fark +1) = 20 + 10 = 30
Kullanıcı C: 1-0  → RESULT (ev kazandı), fark yanlış      = 20
Kullanıcı D: 0-2  → yanlış                                = 0
```

---

## 4. Çekirdek Mantık

### 4.1 Puan Defteri (Ledger)

Tüm ödüller **append‑only** `scors_point_ledger` satırlarıdır. Bakiye = satırların toplamı. Avantajları: tam denetim izi, idempotency, geri alma (flag'lenen içerikten puan iptali = **negatif** satır).

### 4.2 Idempotency + Tavan + Cooldown

- Her ödülün bir **dedup anahtarı** var (örn. `DAILY_LOGIN:2026-06-18`, `PREDICTION_EXACT:fixture-12345`). `UNIQUE(user_id, dedup_key)` → çift puan imkânsız.
- **Günlük tavan:** o gün ilgili `ruleKey` için toplam ≥ tavan ise yeni ödül verilmez.
- **Cooldown:** ardışık aynı tip ödüller arası asgari süre (spam'e karşı).
- **Kalite eşiği:** yorum min uzunluk; flag/silme → ilgili ledger satırı negatiflenir.

### 4.3 Bakiye, Seviye

- `scors_balance` materialize edilir (her ödülde aynı tx içinde güncellenir) — okuma hızlı.
- Seviye toplam puandan türetilir (bkz. §5).
- **Dönem (season):** haftalık + global toplam ayrı tutulur (tarih‑kovalı `season_key`), böylece "haftanın tahmincisi" sıfırlanabilir, "tüm zamanlar" korunur.

---

## 5. Seviye / Tier

| Seviye | Eşik (toplam puan) |
|---|---|
| Çaylak (Bronz) | 0 |
| Taraftar (Gümüş) | 500 |
| Uzman (Altın) | 2.500 |
| Efsane (Platin) | 10.000 |

Seviye sadece statü/rozet sağlar; profilde ve yorumlarda rozet olarak görünür.

---

## 6. Lider Tablosu (Leaderboard)

- Kapsamlar: **global**, **haftalık**, **lige göre**, **arkadaş**.
- `season_key` ile dönem‑bazlı toplam (haftalık reset; tüm zamanlar korunur).
- Performans: periyodik materialize ya da `season_key` üzerinde indeksli toplama sorgusu + kısa süreli cache.
- Profilde sıra + "Top %1 Tahminci" gibi rozetler.

---

## 7. Rozetler (Badges)

Statü ödülü. Örnekler:

- **İlk Tahmin**, **10 Doğru Sonuç**, **Tam Skor Ustası** (5 tam skor), **7 Gün Seri**, **30 Gün Seri**, **Sosyal Kelebek** (10 davet), **Yorumcu** (100 yorum), **Haftanın Şampiyonu** (lider tablosu 1.'si).
- `scors_badge` (tanım) + `scors_user_badge` (kazanım). Kriter sağlanınca event ile verilir.

---

## 8. Ödül Modeli (Fazlı)

- **Şimdi (Faz 1–3): statü + kozmetik.** Lider tablosu, rozet, seviye, profil rozeti; puanla açılan kozmetikler (avatar çerçevesi, isim rengi, tema, özel emoji). Parasal değil → yasal risk minimum.
- **İleride (Faz 4): gerçek ödül / çekiliş.** Forma, bilet, ürün çekilişleri. **TR'de dikkat:**
  - Çekiliş/promosyon → ilgili mevzuat ve **izin/harç** gerektirebilir (kampanyalı çekiliş izni).
  - **KVKK:** puan/davranış verisi işleme için aydınlatma + gerekli yerde açık rıza.
  - Puan **satın alınamaz** kalmalı (aksi halde sanal para/lisans tartışması doğar).
  - 18 yaş altı kullanıcılar için ayrı değerlendirme.
  - Bu faz öncesi hukuki danışmanlık alınmalı.

---

## 9. Suistimal / Sahtecilik Önleme

- Kaynak başına günlük tavan + cooldown (config).
- Kayıt sırasında captcha; hesap yaşı eşiği (yeni hesap bazı ödüllere hemen giremez).
- Cihaz/parmak izi başına hesap sınırı (referral farmına karşı).
- Flag/sil edilen yorum → puan **geri alınır** (negatif ledger).
- Favori ekle/sil ile farm engelleme: kalıcı dedup (`FAVORITE_ADDED:{entityType}:{entityId}` tek sefer).
- Referral ödülü davet edilen **e‑posta doğrulayınca** verilir.
- Tüm puanlama sunucu tarafında; istemci yalnız aksiyon yapar.

---

## 10. Veri Modeli (Postgres / Flyway taslağı)

> Tablo ön eki `scors_`. Aşağıdaki DDL yön gösterici taslaktır; kesin tipler/indeksler inşa sırasında netleşir.

```sql
-- 10.1 Puan defteri (append-only)
CREATE TABLE scors_point_ledger (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users(id),
    rule_key    VARCHAR(48) NOT NULL,
    points      INT         NOT NULL,             -- +/- (geri alma negatif)
    ref_type    VARCHAR(32),                      -- 'fixture','comment','referral'...
    ref_id      VARCHAR(64),
    dedup_key   VARCHAR(128) NOT NULL,            -- 'DAILY_LOGIN:2026-06-18'
    season_key  VARCHAR(16)  NOT NULL,            -- 'ALL' + 'W:2026-25' (haftalık)
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_scors_ledger_dedup UNIQUE (user_id, dedup_key)
);
CREATE INDEX ix_scors_ledger_user        ON scors_point_ledger (user_id);
CREATE INDEX ix_scors_ledger_rule_day    ON scors_point_ledger (user_id, rule_key, created_at);
CREATE INDEX ix_scors_ledger_season      ON scors_point_ledger (season_key, user_id);

-- 10.2 Bakiye (materialize)
CREATE TABLE scors_balance (
    user_id        BIGINT PRIMARY KEY REFERENCES users(id),
    total_points   BIGINT NOT NULL DEFAULT 0,
    level          VARCHAR(24) NOT NULL DEFAULT 'BRONZE',
    longest_streak INT NOT NULL DEFAULT 0,
    current_streak INT NOT NULL DEFAULT 0,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 10.3 Kural konfigürasyonu
CREATE TABLE scors_point_rule (
    rule_key         VARCHAR(48) PRIMARY KEY,
    points           INT NOT NULL,
    daily_cap        INT,            -- null = sınırsız
    cooldown_seconds INT,
    enabled          BOOLEAN NOT NULL DEFAULT true,
    description      VARCHAR(255)
);

-- 10.4 Tahmin
CREATE TABLE scors_prediction (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    fixture_id      BIGINT NOT NULL,
    pred_home       SMALLINT NOT NULL,
    pred_away       SMALLINT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    settled         BOOLEAN NOT NULL DEFAULT false,
    awarded_points  INT,
    CONSTRAINT uq_scors_pred_user_fixture UNIQUE (user_id, fixture_id)
);
CREATE INDEX ix_scors_pred_fixture_unsettled ON scors_prediction (fixture_id) WHERE settled = false;

-- 10.5 Rozetler
CREATE TABLE scors_badge (
    badge_key   VARCHAR(48) PRIMARY KEY,
    name        VARCHAR(80) NOT NULL,
    description VARCHAR(255),
    icon        VARCHAR(120)
);
CREATE TABLE scors_user_badge (
    user_id   BIGINT NOT NULL REFERENCES users(id),
    badge_key VARCHAR(48) NOT NULL REFERENCES scors_badge(badge_key),
    earned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, badge_key)
);
```

---

## 11. Backend Mimari (Spring Boot)

- **`PointService.award(userId, ruleKey, refType, refId)`** — çekirdek. Akış: kural enabled mı → günlük tavan/cooldown kontrolü → ledger satırı ekle (`ON CONFLICT (user_id, dedup_key) DO NOTHING`) → eklendiyse `scors_balance` güncelle + seviye/rozet kontrol. **`REQUIRES_NEW`** ile izole (mevcut `PlayerUpserter` deseni gibi — bir ödül hatası çağıran tx'i kirletmesin).
- **Olay kancaları (event‑driven):** mevcut `ApplicationEventPublisher` deseni kullanılır. Dinleyiciler:
  - `UserLoggedInEvent` → `DAILY_LOGIN` + streak
  - `CommentCreatedEvent` → `COMMENT_CREATED` (kalite/tavan kontrolü)
  - `CommentLikedEvent` → `COMMENT_LIKED`
  - `FavoriteAddedEvent` → `FAVORITE_ADDED`
  - `FixtureFinishedEvent` → tahmin settle (bkz. altta)
  - `CommentFlagged/DeletedEvent` → puan geri alma (negatif)
- **`PredictionService`** — create/update (kickoff öncesi), listele, kilit kontrolü.
- **`PredictionSettleJob`** — biten maç sync'ine bağlı (`@TransactionalEventListener` veya `@Scheduled` tarama): yeni biten fikstürlerin açık tahminlerini hesapla, `PointService.award(...)` ile puanla, `settled=true` işaretle (idempotent).
- **`LeaderboardService`** — `season_key` bazlı toplama + cache.
- **`StreakService`** — `last_login_date` ile ardışık gün takibi (opsiyonel "1 gün affı/freeze").
- **Config:** `scorstv.scorspuan.enabled` ile tüm modül kapatılabilir; kurallar `scors_point_rule` tablosundan.

---

## 12. API Uçları (öneri)

Kullanıcıya açık (puan **kazanma** uçları yok — puan yalnız olaylarla verilir):

```
GET  /api/v1/scorspuan/me                 → bakiye, seviye, streak, rozetler
GET  /api/v1/scorspuan/leaderboard        ?scope=global|weekly|league&leagueId&page
GET  /api/v1/scorspuan/badges             → tüm rozetler + kullanıcının kazandıkları
POST /api/v1/scorspuan/predictions        {fixtureId, home, away}   (kickoff öncesi)
GET  /api/v1/scorspuan/predictions        ?status=open|settled
GET  /api/v1/scorspuan/fixtures/{id}/prediction  → kullanıcının o maç tahmini
```

Admin:

```
POST /api/v1/admin/scorspuan/rules        → kural puan/tavan güncelle
POST /api/v1/admin/scorspuan/adjust       → manuel puan düzeltme (denetimli)
```

---

## 13. Frontend (Web + Mobil)

**Web (Next.js):**
- Header'da küçük puan/seviye rozeti.
- `/scorspuan` (veya `/puan`) sayfası: bakiye, seviye ilerleme çubuğu, rozetler, lider tablosu sekmeleri.
- Maç detayında **tahmin kutusu** (kickoff öncesi giriş; sonra kilitli + sonuç).
- Profilde rozet + sıra.

**Mobil (Flutter):**
- Profil/drawer'da puan kartı + streak göstergesi.
- Maç detayında tahmin widget'ı.
- Lider tablosu ekranı (haftalık/global sekmeleri).
- Push: "Bugünkü maç tahminlerini yap!", "Serini bozma!", "Tahminin tuttu, +50 ScorsPuan".

---

## 14. Yol Haritası (Fazlar)

- **Faz 0 — Temel (altyapı):** `scors_point_ledger` + `scors_balance` + `PointService` + `scors_point_rule` + `DAILY_LOGIN`/streak + seviye + `/scorspuan/me`. Profilde puan/rozet görünür.
- **Faz 1 — Tahmin + Lider tablosu (asıl çekirdek):** `scors_prediction` + kilit + settle job + lider tablosu + tahmin ekranları (web+mobil). Günlük‑dönüş motoru burada devreye girer.
- **Faz 2 — Etkileşim + Rozetler:** yorum/favori/davet/anket/quiz puanları + rozet sistemi + push entegrasyonu + suistimal kontrolleri.
- **Faz 3 — Kozmetik mağaza:** puanla açılan avatar çerçevesi/tema/isim rengi.
- **Faz 4 — Gerçek ödül/çekiliş:** yalnızca hukuki süreç (izin, KVKK, yaş) tamamlandıktan sonra.

---

## 15. Açık Kararlar (netleştirilecek)

1. **Tahmin kapsamı:** tüm maçlar mı, sadece popüler ligler + Dünya Kupası mı? (öneri: popüler ligler ile başla)
2. **Dönem periyodu:** haftalık reset + tüm zamanlar mı; aylık da olsun mu?
3. **Streak affı:** 1 gün kaçırınca seri korunsun mu (freeze)?
4. **Puan dengesi/enflasyonu:** başlangıç değerleri canlıda telemetriyle ayarlanmalı.
5. **Misafir kullanıcı:** kesin üyelik şartı (öneri: evet).
6. **Sürpriz çarpanı / seri bonusu:** Faz 1'de mi yoksa sonra mı?

---

*Sonraki adım: Faz 0 + Faz 1 için detaylı teknik tasarım (entity sınıfları, Flyway migration, PointService/PredictionService imzaları, event tanımları) ve ardından kademeli implementasyon.*
