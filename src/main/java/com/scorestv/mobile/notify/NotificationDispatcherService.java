package com.scorestv.mobile.notify;

import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureEvent;
import com.scorestv.football.domain.FixtureEventRepository;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.mobile.domain.DeviceMatchSubscription;
import com.scorestv.mobile.domain.DeviceMatchSubscriptionRepository;
import com.scorestv.mobile.domain.UserNotificationPref;
import com.scorestv.mobile.domain.UserNotificationPrefRepository;
import com.scorestv.mobile.fcm.FcmMessagingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Canli mac olaylarini (gol, kart, penalti, kickoff, final) ilgili kullanicilara
 * FCM push notification olarak gonderir.
 *
 * <p><b>Akis (event):</b>
 * <ol>
 *   <li>FixtureEventsLiveProcessor yeni event tespit eder → bizi cagirir</li>
 *   <li>Event type+detail'i mobile event tipine (gol/kirmizi/penalti) cevir</li>
 *   <li>Event'in oldugu takimi (event.team) al — bu takimi takip eden cihazlari bul</li>
 *   <li>Her recipient icin FCM mesaji formatla (TR/EN), batch gonder</li>
 * </ol>
 *
 * <p><b>Akis (status change — kickoff/final):</b>
 * <ol>
 *   <li>LiveTickerService Snapshot karsilastirmasinda status NS→1H veya
 *       canli→FT/AET/PEN gecisini gorur → bizi cagirir</li>
 *   <li>Fixture'in iki takimini al — her ikisini takip eden cihazlari bul</li>
 *   <li>FCM mesaji "Galatasaray vs Fenerbahce başladı" formatinda</li>
 * </ol>
 *
 * <p><b>Async:</b> tum dispatch'ler @Async — live ticker tick'ini bekletmez.
 * <b>Idempotency:</b> ayni event icin tekrar cagri yapilsa bile recipient
 * unique olur (DB-level UNIQUE on device_token_id + team_id zaten korur).
 */
@Service
public class NotificationDispatcherService {

    private static final Logger log =
            LoggerFactory.getLogger(NotificationDispatcherService.class);

    private final UserNotificationPrefRepository prefRepository;
    private final DeviceMatchSubscriptionRepository matchSubRepository;
    private final FcmMessagingService fcmMessaging;
    private final NotificationMessageBuilder messageBuilder;
    private final FixtureRepository fixtureRepository;
    private final FixtureEventRepository fixtureEventRepository;
    private final NotificationOutboxEnqueuer enqueuer;

    /**
     * Cift bildirim onleme memo'su. Key formati:
     *   "{fixtureId}:{type}:{teamId}:{score-or-minute}"
     * Value: gonderim epoch_ms. Ayni anahtar son {@link #DEDUP_TTL_MS} icinde
     * varsa skip — LiveTickerService bir skor degisimini iki tick'te gorse
     * veya FixtureEventsLiveProcessor ayni event'i yeniden islese tekrar push
     * gitmez.
     *
     * <p>Map process-ici (clustered ortamda her node ayri memo) — bizim
     * single-app deployment'imizda yeterli. Cluster gerekirse Redis bazli
     * dedup.
     */
    private final java.util.concurrent.ConcurrentHashMap<String, Long> _dedupMemo =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final long DEDUP_TTL_MS = 120_000L; // 2 dakika

    /** Son N ms icinde ayni key icin push gonderildi mi? */
    private boolean _isRecentlySent(String key) {
        Long lastAt = _dedupMemo.get(key);
        if (lastAt == null) return false;
        long age = System.currentTimeMillis() - lastAt;
        if (age < DEDUP_TTL_MS) return true;
        // TTL gectir — temizle ve tekrar gonderebiliriz
        _dedupMemo.remove(key);
        return false;
    }

    /** Push gonderildi olarak isaretle + memo'yu basit sekilde budamayi dene. */
    private void _markSent(String key) {
        _dedupMemo.put(key, System.currentTimeMillis());
        // Lightweight cleanup: memo 500 entry'i gectiyse expired olanlari sil
        if (_dedupMemo.size() > 500) {
            long now = System.currentTimeMillis();
            _dedupMemo.entrySet().removeIf(
                    e -> now - e.getValue() > DEDUP_TTL_MS);
        }
    }

    public NotificationDispatcherService(
            UserNotificationPrefRepository prefRepository,
            DeviceMatchSubscriptionRepository matchSubRepository,
            FcmMessagingService fcmMessaging,
            NotificationMessageBuilder messageBuilder,
            FixtureRepository fixtureRepository,
            FixtureEventRepository fixtureEventRepository,
            NotificationOutboxEnqueuer enqueuer) {
        this.prefRepository = prefRepository;
        this.matchSubRepository = matchSubRepository;
        this.fcmMessaging = fcmMessaging;
        this.messageBuilder = messageBuilder;
        this.fixtureRepository = fixtureRepository;
        this.fixtureEventRepository = fixtureEventRepository;
        this.enqueuer = enqueuer;
    }

    // ============================================================
    // EVENT (gol/kart/penalti) — FixtureEventsLiveProcessor'dan cagrilir
    // ============================================================

    @Async
    @Transactional
    public void dispatchEvent(Fixture fixtureArg, FixtureEvent eventArg) {
        if (!fcmMessaging.isEnabled()) return;
        if (fixtureArg == null || eventArg == null) return;

        // Async thread'de YENI session açılır; çağırandan gelen detached lazy
        // proxy'ler (takım vb.) burada init edilemez (LazyInitializationException).
        // Id ile MANAGED kopyaları çek — takım isimleri bu session içinde yüklenir.
        final Fixture fixture =
                fixtureRepository.findById(fixtureArg.getId()).orElse(null);
        if (fixture == null) return;
        final FixtureEvent event = eventArg.getId() != null
                ? fixtureEventRepository.findById(eventArg.getId()).orElse(eventArg)
                : eventArg;

        final String mobileType = _mapEventType(event);
        if (mobileType == null) return; // ilgilenmedigimiz event (orn. subst)

        // GOL bildirimi artik SKOR degisiminden ANINDA gonderiliyor (dispatchGoal,
        // LiveTickerService tetikler) — burada tekrar gondermeyiz, cift olmasin.
        // Kirmizi kart / penalti event-tetikli kalir (skoru degistirmezler).
        if ("gol".equals(mobileType)) return;

        final Long teamId = event.getTeam() != null ? event.getTeam().getId() : null;
        if (teamId == null) return;

        // OUTBOX dedup anahtarı — STABİL: event.getId()/playerId DAHIL DEĞİL.
        // API kırmızı kartı önce ISIMSIZ, sonra olayı silip YENİ id ile İSİMLİ
        // gönderiyor (events her sync'te replace — bkz. FixtureEvent javadoc).
        // Aynı dakika+uzatma+tip+takım aynı anahtara düşer → TEK bildirim; ilk
        // gelen (genelde isimsiz) kuyruğa girer, isimli sürüm dedup'a takılır.
        final int evExtra = event.getTimeExtra() == null ? 0 : event.getTimeExtra();
        // event tipi (goal/card/var) anahtara dahil: ayni dakikada VAR penalti
        // karari (type=var) ile kacan penalti (type=goal) farkli anahtara dussun,
        // biri digerini dedup'a takip dusurmesin. Tip STABIL (isimsiz->isimli
        // replace'te degismez) → kirmizi-kart cift-push korumasi bozulmaz.
        final String evType = event.getType() == null ? "" : event.getType().toLowerCase();
        final String dedupKey = String.format("EVENT:%d:%s:%s:%d:%d:%d",
                fixture.getId(), mobileType, evType, teamId,
                event.getTimeElapsed() == null ? 0 : event.getTimeElapsed(), evExtra);

        // Mesajı ŞİMDİ render et (oyuncu/dakika snapshot'ı), kuyruğa yaz.
        final var msg = messageBuilder.buildEventMessage(fixture, event, mobileType);
        final Map<String, String> data = new HashMap<>();
        data.put("type", mobileType);
        data.put("fixtureId", String.valueOf(fixture.getId()));
        data.put("teamId", String.valueOf(teamId));
        if (event.getTimeElapsed() != null) {
            data.put("eventMinute", String.valueOf(event.getTimeElapsed()));
        }
        enqueuer.enqueue(NotificationOutbox.KIND_EVENT, mobileType, fixture.getId(),
                teamId, msg.title(), msg.body(), data, dedupKey);
    }

    // ============================================================
    // GOL — LiveTickerService SKOR degisimini gorunce ANINDA cagrilir.
    // Olay (golcu) beklenmez; o an DB'de varsa eklenir, yoksa skor-only.
    // ============================================================

    @Async
    @Transactional
    public void dispatchGoal(Long fixtureId, Long scoringTeamId) {
        if (!fcmMessaging.isEnabled() || fixtureId == null) return;
        final Fixture fixture =
                fixtureRepository.findById(fixtureId).orElse(null);
        if (fixture == null) return;

        // OUTBOX dedup anahtarı: skor snapshot'ı (home-away) golü benzersizleştirir.
        // Aynı skor değişimini iki tick'te görsek de tek bildirim girer.
        final String dedupKey = String.format("GOAL:%d:%d:%d-%d",
                fixtureId,
                scoringTeamId == null ? 0L : scoringTeamId,
                fixture.getHomeGoals() == null ? 0 : fixture.getHomeGoals(),
                fixture.getAwayGoals() == null ? 0 : fixture.getAwayGoals());

        // Mesajı ŞİMDİ render et (skor + dakika snapshot'ı dondurulur) ve kuyruğa
        // yaz — geç gönderimde skor değişse bile mesaj o anki golü gösterir.
        final Integer minute = fixture.getElapsed();
        final var msg = messageBuilder.buildScoreGoal(fixture, null, minute);
        final Map<String, String> data = new HashMap<>();
        data.put("type", "gol");
        data.put("fixtureId", String.valueOf(fixtureId));
        if (minute != null) data.put("eventMinute", String.valueOf(minute));
        // teamId = gol atan takım → "gol" tercihi açık o takım takipçileri alır.
        enqueuer.enqueue(NotificationOutbox.KIND_GOAL, "gol", fixtureId,
                scoringTeamId, msg.title(), msg.body(), data, dedupKey);
    }

    // ============================================================
    // KICKOFF — LiveTickerService status NS→1H tespit edince cagrilir
    // ============================================================

    @Async
    @Transactional(readOnly = true)
    public void dispatchKickoff(Fixture fixtureArg) {
        if (!fcmMessaging.isEnabled() || fixtureArg == null) return;
        final Fixture fixture =
                fixtureRepository.findById(fixtureArg.getId()).orElse(null);
        if (fixture == null) return;

        // Cift bildirim guardu — LiveTickerService NS→1H gecisini bazen iki
        // tick'te goruyor.
        final String koKey = "kickoff:" + fixture.getId();
        if (_isRecentlySent(koKey)) {
            log.info("Kickoff push SKIP (dedup) fixtureId={}", fixture.getId());
            return;
        }
        _markSent(koKey);
        _dispatchMatchStatus(fixture, "basladi");
    }

    // ============================================================
    // FINAL — LiveTickerService 1H/2H/ET→FT/AET/PEN tespit edince cagrilir
    // ============================================================

    @Async
    @Transactional(readOnly = true)
    public void dispatchFinal(Fixture fixtureArg) {
        // dispatchFinal body asagida — dedup en uste eklenir.
        // (Mevcut implementasyon korunur — sadece guard.)
        if (fixtureArg != null) {
            final String fnKey = "final:" + fixtureArg.getId();
            if (_isRecentlySent(fnKey)) {
                log.info("Final push SKIP (dedup) fixtureId={}",
                        fixtureArg.getId());
                return;
            }
            _markSent(fnKey);
        }
        if (!fcmMessaging.isEnabled() || fixtureArg == null) return;
        final Fixture fixture =
                fixtureRepository.findById(fixtureArg.getId()).orElse(null);
        if (fixture == null) return;
        _dispatchMatchStatus(fixture, "bitti");
    }

    // ============================================================
    // KADRO (İlk 11) — ImminentLineupsJob kadro İLK açıklandığında cagrilir.
    // ============================================================

    @Async
    @Transactional
    public void dispatchLineup(Long fixtureId) {
        if (!fcmMessaging.isEnabled() || fixtureId == null) return;
        final Fixture fixture =
                fixtureRepository.findById(fixtureId).orElse(null);
        if (fixture == null) return;
        // Kadro maç başına BİR kez açıklanır → "LINEUP:fixture" dedup_key tek
        // bildirim sağlar. Mesajı şimdi render et, kuyruğa yaz (garantili teslim).
        final var msg = messageBuilder.buildLineupMessage(fixture);
        final Map<String, String> data = new HashMap<>();
        data.put("type", "kadro");
        data.put("fixtureId", String.valueOf(fixtureId));
        enqueuer.enqueue(NotificationOutbox.KIND_LINEUP, "kadro", fixtureId, null,
                msg.title(), msg.body(), data, "LINEUP:" + fixtureId);
    }

    /**
     * SENKRON outbox gönderimi — {@link NotificationOutboxWorker} çağırır.
     * Mesaj zaten render edilmiş (title/body/data) olarak gelir; bu metot
     * yalnız ALICILARI tazece çözer (token'lar değişmiş olabilir) ve FCM'e
     * gönderir. <b>Sert FCM hatasında EXCEPTION fırlatır</b> ki worker backoff'la
     * tekrar denesin. Alıcı yoksa 0 döner (başarı — gönderilecek yok).
     *
     * @param fixtureId maç id
     * @param teamId    GOAL/EVENT: ilgili takım takipçileri; null ise (KICKOFF/
     *                  FINAL) maçın HER İKİ takımının takipçileri alır
     * @param notifType basladi|bitti|gol|kirmizi|penalti (tercih sorgusu)
     */
    @Transactional(readOnly = true)
    public int sendOutboxRow(Long fixtureId, Long teamId, String notifType,
                             String title, String body, Map<String, String> data) {
        if (!fcmMessaging.isEnabled()) {
            // FCM kapali — worker'in beklemesi (retry) icin gecici hata say.
            throw new IllegalStateException("FCM devre disi");
        }
        final Set<String> tokens = new LinkedHashSet<>();
        if (teamId != null) {
            // GOAL/EVENT — ilgili takimin (notifType tercihi acik) takipcileri.
            for (UserNotificationPref p : _recipientsFor(notifType, teamId)) {
                tokens.add(p.getDeviceToken().getFcmToken());
            }
        } else {
            // KICKOFF/FINAL — maçın iki takımı.
            final Fixture fixture = fixtureRepository.findById(fixtureId).orElse(null);
            if (fixture != null) {
                if (fixture.getHomeTeam() != null) {
                    for (UserNotificationPref p :
                            _recipientsFor(notifType, fixture.getHomeTeam().getId())) {
                        tokens.add(p.getDeviceToken().getFcmToken());
                    }
                }
                if (fixture.getAwayTeam() != null) {
                    for (UserNotificationPref p :
                            _recipientsFor(notifType, fixture.getAwayTeam().getId())) {
                        tokens.add(p.getDeviceToken().getFcmToken());
                    }
                }
            }
        }
        // Mac-bazli favori abonelikleri — her tip bildirimi alir.
        for (DeviceMatchSubscription s :
                matchSubRepository.findRecipientsForFixture(fixtureId)) {
            tokens.add(s.getDeviceToken().getFcmToken());
        }
        if (tokens.isEmpty()) return 0; // alici yok — basari

        // Sert hatada fırlatır → worker retry eder.
        final int sent = fcmMessaging.sendMulticastOrThrow(
                List.copyOf(tokens), title, body, data);
        log.info("FCM outbox dispatch: fixtureId={} type={} alici={} gonderildi={}",
                fixtureId, notifType, tokens.size(), sent);
        return sent;
    }

    private void _dispatchMatchStatus(Fixture fixture, String mobileType) {
        // 1) Takim takibinden gelen recipient'lar — hem ev hem deplasman takim
        final Set<Long> teamIds = new LinkedHashSet<>();
        if (fixture.getHomeTeam() != null) teamIds.add(fixture.getHomeTeam().getId());
        if (fixture.getAwayTeam() != null) teamIds.add(fixture.getAwayTeam().getId());

        final Set<String> tokens = new LinkedHashSet<>();
        int teamRecipientCount = 0;
        for (Long teamId : teamIds) {
            final List<UserNotificationPref> recipients =
                    _recipientsFor(mobileType, teamId);
            teamRecipientCount += recipients.size();
            for (UserNotificationPref p : recipients) {
                tokens.add(p.getDeviceToken().getFcmToken());
            }
        }

        // 2) Mac-bazli favori abonelikleri — favori yapan tum cihazlar
        //    basladi/bitti event'lerini de alir.
        final List<DeviceMatchSubscription> favRecipients =
                matchSubRepository.findRecipientsForFixture(fixture.getId());
        for (DeviceMatchSubscription s : favRecipients) {
            tokens.add(s.getDeviceToken().getFcmToken());
        }

        if (tokens.isEmpty()) {
            log.debug("Dispatch {}: alici yok fixtureId={}", mobileType, fixture.getId());
            return;
        }

        final var msg = switch (mobileType) {
            case "basladi" -> messageBuilder.buildKickoffMessage(fixture);
            case "kadro" -> messageBuilder.buildLineupMessage(fixture);
            default -> messageBuilder.buildFinalMessage(fixture);
        };
        final Map<String, String> data = new HashMap<>();
        data.put("type", mobileType);
        data.put("fixtureId", String.valueOf(fixture.getId()));

        final int sent = fcmMessaging.sendMulticast(
                List.copyOf(tokens), msg.title(), msg.body(), data);
        log.info("FCM {} dispatch: fixtureId={} alici={} (takim={} favori={}) gonderildi={}",
                mobileType, fixture.getId(), tokens.size(),
                teamRecipientCount, favRecipients.size(), sent);
    }

    // ============================================================
    // Yardimcilar
    // ============================================================

    /**
     * API-Football event type+detail'i mobile bildirim tipine cevir.
     *
     * <p>API-Football event kombinasyonlari:
     * <ul>
     *   <li>Goal + "Normal Goal" / "Own Goal" / "Penalty" → "gol" (penaltidan gol
     *       de skor degisiminden "GOL!" atilir; ayrica penalti bildirimi gonderilmez)</li>
     *   <li>Goal + "Missed Penalty" → "penalti" (kacan penalti)</li>
     *   <li>Card + "Red Card" / "Second Yellow card" → "kirmizi"</li>
     *   <li>Var + detail icinde "Penalty" veya "Goal" → "penalti" (VAR karari;
     *       mesaj builder "📺 VAR: ..." metni uretir)</li>
     *   <li>Diger (Yellow Card, subst, kart-upgrade Var) → null (bildirim yok)</li>
     * </ul>
     */
    private String _mapEventType(FixtureEvent e) {
        if (e.getType() == null) return null;
        final String type = e.getType().toLowerCase();
        final String detail = e.getDetail() == null ? "" : e.getDetail().toLowerCase();

        if ("goal".equals(type)) {
            // Penaltidan GOL → "gol": skor degisimi zaten "⚽ GOL!" atar; burada
            // "penalti" donsek ayni an iki bildirim (gol + penalti) olurdu.
            // Sadece KACAN penalti ("Missed Penalty") penalti olarak bildirilir.
            if (detail.contains("missed")) return "penalti";
            return "gol";
        }
        if ("card".equals(type)) {
            if (detail.contains("red") || detail.contains("second yellow")) {
                return "kirmizi";
            }
            return null; // sari kart bildirim listemizde yok
        }
        if ("var".equals(type)) {
            // Penalti veya gol ile ilgili VAR kararlari "penalti" tercihine duser
            // (ayri VAR toggle yok). Mesaj builder type=Var'i gorup "VAR:" metni uretir.
            if (detail.contains("penalty") || detail.contains("goal")) return "penalti";
            return null; // kart upgrade vb. bildirilmez
        }
        return null;
    }

    /**
     * Verilen olay tipi + takim icin "bildirim acik" olan tum recipient kayitlari.
     * Repository'deki ayri ayri findRecipientsFor* metodlari kullanilir.
     */
    private List<UserNotificationPref> _recipientsFor(String mobileType, Long teamId) {
        return switch (mobileType) {
            case "gol" -> prefRepository.findRecipientsForGoal(teamId);
            case "kirmizi" -> prefRepository.findRecipientsForRedCard(teamId);
            case "penalti" -> prefRepository.findRecipientsForPenalty(teamId);
            case "basladi" -> prefRepository.findRecipientsForKickoff(teamId);
            case "bitti" -> prefRepository.findRecipientsForFinal(teamId);
            case "kadro" -> prefRepository.findRecipientsForLineup(teamId);
            case "ht" -> prefRepository.findRecipientsForHalftime(teamId);
            case "2yari" -> prefRepository.findRecipientsForSecondHalf(teamId);
            default -> List.of();
        };
    }
}
