package com.scorestv.football.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** Maç (fikstür) verisine erişim. */
public interface FixtureRepository extends JpaRepository<Fixture, Long> {

    /**
     * "Başladı" bildirimi için ATOMİK claim. Yalnız {@code notif_kickoff_at}
     * NULL iken set eder; eşzamanlı/tekrar tick'lerde sadece BİRİ 1 satır
     * günceller → tam-bir-kez. Dönen değer etkilenen satır sayısı (0 veya 1).
     */
    @Modifying
    @Query("UPDATE Fixture f SET f.notifKickoffAt = :now "
            + "WHERE f.id = :id AND f.notifKickoffAt IS NULL")
    int claimKickoffNotification(@Param("id") Long id, @Param("now") Instant now);

    /** "Bitti" bildirimi için atomik claim — bkz. {@link #claimKickoffNotification}. */
    @Modifying
    @Query("UPDATE Fixture f SET f.notifFinalAt = :now "
            + "WHERE f.id = :id AND f.notifFinalAt IS NULL")
    int claimFinalNotification(@Param("id") Long id, @Param("now") Instant now);

    /**
     * Belirli bir zaman aralığında başlayan maçlar (anasayfa fikstür listesi).
     * Örn. "bugünün maçları" için günün başı/sonu verilir.
     */
    List<Fixture> findByKickoffAtBetweenOrderByKickoffAtAsc(Instant start, Instant end);

    /**
     * Verilen durum kodlarındaki maçlar. Canlı maçları bulmak için
     * (örn. "1H", "HT", "2H", "ET", "P") veya senkron tetiklemesi için kullanılır.
     */
    List<Fixture> findByStatusShortIn(Collection<String> statuses);

    /**
     * Aynı sorgu, ama lig {@code JOIN FETCH} ile birlikte yüklenir.
     * Live joblar {@link com.scorestv.football.live.SyncRateLimiter} ile
     * {@code fixture.getLeague().isCovered()}'i okur; transaction kapandıktan
     * sonra lazy proxy {@code LazyInitializationException} atardı —
     * bu sorgu önlüyor.
     */
    @Query("SELECT f FROM Fixture f "
            + "JOIN FETCH f.league "
            + "WHERE f.statusShort IN :statuses")
    List<Fixture> findByStatusShortInWithLeague(@Param("statuses") Collection<String> statuses);

    /**
     * Sadece id listesi — verilen statülerdeki maçların id'lerini döner.
     * {@link com.scorestv.football.live.LiveTickerService} stuck-detection
     * için kullanır (DB'de LIVE ama API live response'unda olmayanları bulur).
     */
    @Query("SELECT f.id FROM Fixture f WHERE f.statusShort IN :statuses")
    List<Long> findIdsByStatusShortIn(@Param("statuses") Collection<String> statuses);

    /**
     * "Yaşlı canlı" maçlar: verilen statülerde + kickoff'tan {@code cutoff}
     * once olmuş = uzun süre takılı kalmış, muhtemelen API'den FT geldi ama
     * bizim ticker yetisemedi. LiveTickerService bunları stuck listesine
     * dahil eder ve {@code /fixtures?ids=A-B-C} ile zorla yeniden ceker.
     *
     * <p>Ornek: 105 dk gecmis hala "1H" gosterilen mac → /fixtures?id ile
     * cek, status FT/AET/PEN'e gecer.
     */
    @Query("SELECT f.id FROM Fixture f "
            + "WHERE f.statusShort IN :statuses AND f.kickoffAt < :cutoff")
    List<Long> findAgedLiveIds(
            @Param("statuses") Collection<String> statuses,
            @Param("cutoff") Instant cutoff);

    /**
     * Verilen yarı-açık zaman aralığında ([start, end)) hiç maç var mı?
     * Başlangıç senkronunda bir tarihin zaten dolu olup olmadığını saptamak
     * için kullanılır.
     */
    boolean existsByKickoffAtGreaterThanEqualAndKickoffAtLessThan(Instant start, Instant end);

    /**
     * Bir günün maçlarını, lig/takım/stadyum ilişkileriyle birlikte tek
     * sorguda yükler (JOIN FETCH ile N+1 önlenir). Anasayfa fikstür listesi
     * için kullanılır.
     */
    @Query("SELECT f FROM Fixture f "
            + "JOIN FETCH f.league "
            + "JOIN FETCH f.homeTeam "
            + "JOIN FETCH f.awayTeam "
            + "LEFT JOIN FETCH f.venue "
            + "WHERE f.kickoffAt >= :start AND f.kickoffAt < :end "
            + "ORDER BY f.kickoffAt ASC")
    List<Fixture> findDayWithDetails(@Param("start") Instant start, @Param("end") Instant end);

    /**
     * Bir günün maçları + önceki günden hâlâ canlı oynanan ("misafir") maçlar.
     *
     * <p>Geç başlayan ve gece yarısını aşan maçlar (örn. 23:00 kickoff,
     * 01:00'te biten) için: önceki güne ait olsa da canlı oldukları sürece
     * <b>bir sonraki günde de</b> listelenir; bittikleri an (status artık
     * {@code LIVE_STATUSES}'te değil) o günden otomatik düşer.
     *
     * <p>Tek sorguda ilişkiler JOIN FETCH ile yüklenir; başlama saatine göre
     * sıralı dönülür — misafirler daha eski kickoff'a sahip oldukları için
     * doğal olarak başa düşer.
     *
     * @param priorStart önceki günün başlangıcı (sarkma penceresinin alt sınırı)
     * @param start      sorgulanan günün başlangıcı (X)
     * @param end        sorgulanan günün bitişi (X+1)
     * @param live       canlı sayılan durum kodları
     */
    @Query("SELECT f FROM Fixture f "
            + "JOIN FETCH f.league "
            + "JOIN FETCH f.homeTeam "
            + "JOIN FETCH f.awayTeam "
            + "LEFT JOIN FETCH f.venue "
            + "WHERE (f.kickoffAt >= :start AND f.kickoffAt < :end) "
            + "   OR (f.kickoffAt >= :priorStart AND f.kickoffAt < :start "
            + "       AND f.statusShort IN :live) "
            + "ORDER BY f.kickoffAt ASC")
    List<Fixture> findDayWithLiveSpillover(@Param("priorStart") Instant priorStart,
                                           @Param("start") Instant start,
                                           @Param("end") Instant end,
                                           @Param("live") Collection<String> live);

    /**
     * Tarih şeridi için günlük istatistik: verilen aralıkta, verilen saat
     * diliminde her gün için toplam ve canlı maç sayısı. Sonuç her satır:
     * {@code [java.sql.Date date, Number total, Number live]}.
     *
     * <p>Native sorgu — PostgreSQL'in {@code AT TIME ZONE} ve {@code FILTER}
     * özelliklerini kullanır; JPQL'de doğrudan karşılığı yok.
     */
    /*
     * NOT: Hibernate named param ':tz'ı her geçtiği yerde ayrı '?' placeholder
     * olarak yerleştirir. PostgreSQL bu üç '?'i farklı expression sayar →
     * SELECT'teki DATE(... ?) ile GROUP BY'daki DATE(... ?) eşleşmez ve
     * "kickoff_at must appear in GROUP BY" hatası verir. Çözüm: GROUP BY 1,
     * ORDER BY 1 ile SELECT pozisyonuna referans.
     */
    @Query(value = """
            SELECT
              DATE(kickoff_at AT TIME ZONE :tz) AS day,
              COUNT(*) AS total,
              COUNT(*) FILTER (
                  WHERE status_short IN ('1H','HT','2H','ET','BT','P','LIVE')
              ) AS live
            FROM fixtures
            WHERE kickoff_at >= :start AND kickoff_at < :end
            GROUP BY 1
            ORDER BY 1
            """, nativeQuery = true)
    List<Object[]> aggregateByDay(@Param("tz") String timezone,
                                  @Param("start") Instant start,
                                  @Param("end") Instant end);

    /**
     * Şu an canlı oynanan maçlar; lig ve takımlar tek sorguda fetch edilir
     * (N+1 önlemi). Canlı endpoint ve banner için.
     */
    @Query("SELECT f FROM Fixture f "
            + "JOIN FETCH f.league "
            + "JOIN FETCH f.homeTeam "
            + "JOIN FETCH f.awayTeam "
            + "WHERE f.statusShort IN :live "
            + "ORDER BY f.kickoffAt ASC")
    List<Fixture> findLiveWithDetails(@Param("live") Collection<String> liveStatuses);

    /**
     * Verilen id'lere ait maçları lig + takım ilişkileriyle birlikte tek
     * sorguda yükler. Canlı ticker, değişen maçları yayına vermeden önce
     * tam yüklemek için kullanır.
     */
    @Query("SELECT f FROM Fixture f "
            + "JOIN FETCH f.league "
            + "JOIN FETCH f.homeTeam "
            + "JOIN FETCH f.awayTeam "
            + "WHERE f.id IN :ids")
    List<Fixture> findAllByIdWithDetails(@Param("ids") Collection<Long> ids);

    /**
     * Tek bir maçı lig/takım/stadyum ilişkileriyle birlikte yükler.
     * Maç detay sayfası için (slug → id çözüldükten sonra).
     */
    @Query("SELECT f FROM Fixture f "
            + "JOIN FETCH f.league "
            + "JOIN FETCH f.homeTeam "
            + "JOIN FETCH f.awayTeam "
            + "LEFT JOIN FETCH f.venue "
            + "WHERE f.id = :id")
    java.util.Optional<Fixture> findOneWithDetails(@Param("id") Long id);

    /**
     * Yaklaşan maçlar (NS/TBD), verilen zaman aralığında başlayacak ve henüz
     * kadrosu açıklanmamış olanlar. {@link com.scorestv.football.live.ImminentLineupsJob}
     * her tick'te bunları çekip {@code /fixtures/lineups} yoklar.
     *
     * <p>{@code NOT EXISTS} ile zaten kadrosu olan maçlar elenir — boş yere
     * API çağrısı yapmayız.
     */
    @Query("SELECT f FROM Fixture f "
            + "WHERE f.kickoffAt >= :now AND f.kickoffAt < :until "
            + "  AND f.statusShort IN ('NS','TBD') "
            + "  AND NOT EXISTS ("
            + "      SELECT 1 FROM com.scorestv.football.domain.FixtureLineup l "
            + "      WHERE l.fixture = f) "
            + "ORDER BY f.kickoffAt ASC")
    List<Fixture> findImminentWithoutLineups(@Param("now") Instant now,
                                             @Param("until") Instant until);

    /**
     * İki takım arasındaki geçmiş (bitmiş) karşılaşmalar — yeni → eski.
     * Maç detayı "Head-to-Head" bölümü için. Pageable ile {@code last} sayısı
     * belirlenir; status_short filtresiyle yalnız oynanmış/sonuçlanmış maçlar
     * döner (ertelenmiş/iptal hariç).
     *
     * <p>Genel kullanım için {@link #findMeetings} tercih edilir —
     * "geçmişte hiç oynamamış ama bugün canlı" iki takım için bu sorgu boş
     * döner ve widget boş kalır.
     */
    @Query("SELECT f FROM Fixture f "
            + "JOIN FETCH f.league "
            + "JOIN FETCH f.homeTeam "
            + "JOIN FETCH f.awayTeam "
            + "WHERE ((f.homeTeam.id = :teamA AND f.awayTeam.id = :teamB) "
            + "    OR (f.homeTeam.id = :teamB AND f.awayTeam.id = :teamA)) "
            + "  AND f.statusShort IN ('FT', 'AET', 'PEN', 'ABD', 'AWD', 'WO') "
            + "ORDER BY f.kickoffAt DESC")
    List<Fixture> findRecentMeetings(@Param("teamA") Long teamA,
                                     @Param("teamB") Long teamB,
                                     org.springframework.data.domain.Pageable pageable);

    /**
     * İki takım arasındaki TÜM karşılaşmalar (geçmiş + canlı + gelecek),
     * yeni → eski. Mevcut detay maçı bile listede kalır — API hangi
     * karşılaşmaları döndürdüyse hepsi gösterilir (kullanıcı isteği).
     *
     * <p>Maç detayı "Head-to-Head" widget'ı bunu kullanır; Pageable ile
     * en fazla N karşılaşma alınır.
     */
    @Query("SELECT f FROM Fixture f "
            + "JOIN FETCH f.league "
            + "JOIN FETCH f.homeTeam "
            + "JOIN FETCH f.awayTeam "
            + "WHERE ((f.homeTeam.id = :teamA AND f.awayTeam.id = :teamB) "
            + "    OR (f.homeTeam.id = :teamB AND f.awayTeam.id = :teamA)) "
            + "ORDER BY f.kickoffAt DESC")
    List<Fixture> findMeetings(@Param("teamA") Long teamA,
                               @Param("teamB") Long teamB,
                               org.springframework.data.domain.Pageable pageable);

    /** Bir ligin "covered" işaretli olanlarının id'leri — periyodik standings job için. */
    @Query("SELECT l.id FROM League l WHERE l.covered = true")
    List<Long> findCoveredLeagueIds();

    /**
     * Verilen zaman aralığında başlayacak, kapsamlı (covered) liglerdeki
     * NS/TBD maçlar. {@link com.scorestv.football.sync.DailyH2hPrefetchJob}
     * her birine H2H pre-fetch yapar.
     */
    @Query("SELECT f FROM Fixture f "
            + "JOIN FETCH f.homeTeam "
            + "JOIN FETCH f.awayTeam "
            + "WHERE f.kickoffAt >= :start AND f.kickoffAt < :end "
            + "  AND f.league.covered = true "
            + "  AND f.statusShort IN ('NS','TBD')")
    List<Fixture> findUpcomingCoveredFixtures(@Param("start") Instant start,
                                              @Param("end") Instant end);

    /**
     * Bir ligin belirli bir sezondaki tüm fikstürleri — round bazlı gruplama
     * için kullanılır. Lazy fetch ile takım/lig çekilir (lig detay sayfası
     * round içinde takım adlarını gösterir).
     *
     * <p>Sıralama: kickoffAt (eski → yeni). Hafta hafta gruplamayı serving
     * katmanı yapar (her satırın {@code round} alanını kullanarak).
     */
    @Query("SELECT f FROM Fixture f "
            + "JOIN FETCH f.homeTeam "
            + "JOIN FETCH f.awayTeam "
            + "WHERE f.league.id = :leagueId AND f.season = :season "
            + "ORDER BY f.kickoffAt ASC")
    List<Fixture> findByLeagueIdAndSeason(@Param("leagueId") Long leagueId,
                                          @Param("season") Integer season);

    /** Bir lig+sezondaki fikstür sayısı — lazy sync "DB boş mu" kontrolü için. */
    long countByLeagueIdAndSeason(Long leagueId, Integer season);

    /**
     * Bir ligin belirli sezonundaki DISTINCT takim listesi (home + away birlesik).
     *
     * <p>Takim secim (onboarding) ekrani icin: ligin tum takimlari, agir
     * fixture verisi olmadan. Native UNION, JPA distinct yetersiz olabilir.
     */
    @Query(value = "SELECT * FROM teams WHERE id IN ("
            + "  SELECT home_team_id FROM fixtures "
            + "  WHERE league_id = :leagueId AND season = :season "
            + "  UNION "
            + "  SELECT away_team_id FROM fixtures "
            + "  WHERE league_id = :leagueId AND season = :season"
            + ") ORDER BY name", nativeQuery = true)
    List<com.scorestv.football.domain.Team> findDistinctTeamsByLeagueAndSeason(
            @Param("leagueId") Long leagueId, @Param("season") Integer season);

    /**
     * Yakın geçmişte (verilen aralıkta) BİTMİŞ covered maçlar.
     * {@code FinishedMatchFinalSyncJob} bu maçların stats / playerStats /
     * lineups / events / standings verilerini bir kez daha çekip API
     * düzeltmelerini yakalar (FT olduktan sonra ~30-90 dk içinde gelir).
     */
    @Query("SELECT f FROM Fixture f "
            + "JOIN FETCH f.league "
            + "JOIN FETCH f.homeTeam "
            + "JOIN FETCH f.awayTeam "
            + "WHERE f.kickoffAt >= :start AND f.kickoffAt < :end "
            + "  AND f.league.covered = true "
            + "  AND f.statusShort IN ('FT','AET','PEN')")
    List<Fixture> findRecentlyFinishedCoveredFixtures(@Param("start") Instant start,
                                                      @Param("end") Instant end);

    /**
     * Takim sayfasi icin: bir takimin son N oynanan (FT/AET/PEN) maci,
     * yeni → eski. Sezon filtresi yoktur — takim son oynadigi macin sezonu
     * hangisiyse onlar gelir.
     */
    @Query("SELECT f FROM Fixture f "
            + "JOIN FETCH f.league "
            + "JOIN FETCH f.homeTeam "
            + "JOIN FETCH f.awayTeam "
            + "WHERE (f.homeTeam.id = :teamId OR f.awayTeam.id = :teamId) "
            + "  AND f.statusShort IN ('FT','AET','PEN','ABD','AWD','WO') "
            + "ORDER BY f.kickoffAt DESC")
    List<Fixture> findRecentByTeam(@Param("teamId") Long teamId,
                                   org.springframework.data.domain.Pageable pageable);

    /**
     * Takim sayfasi icin: bir takimin yakin N gelecek (NS/TBD/CANC) maci,
     * yakin → uzak.
     */
    @Query("SELECT f FROM Fixture f "
            + "JOIN FETCH f.league "
            + "JOIN FETCH f.homeTeam "
            + "JOIN FETCH f.awayTeam "
            + "WHERE (f.homeTeam.id = :teamId OR f.awayTeam.id = :teamId) "
            + "  AND f.statusShort IN ('NS','TBD','PST') "
            + "ORDER BY f.kickoffAt ASC")
    List<Fixture> findUpcomingByTeam(@Param("teamId") Long teamId,
                                     org.springframework.data.domain.Pageable pageable);

    /**
     * Bir takimin belirli sezonda yer aldigi liglerin id'leri.
     * Takim sayfasi statistics ve standings position icin "hangi ligler" listesi.
     */
    @Query("SELECT DISTINCT f.league.id FROM Fixture f "
            + "WHERE (f.homeTeam.id = :teamId OR f.awayTeam.id = :teamId) "
            + "  AND f.season = :season")
    List<Long> findLeagueIdsByTeamAndSeason(@Param("teamId") Long teamId,
                                            @Param("season") Integer season);

    /**
     * Bir takimin DB'de mevcut TUM sezonlari (yeni → eski). Takim detay
     * sayfasi sezon dropdown'unu doldurur.
     */
    @Query("SELECT DISTINCT f.season FROM Fixture f "
            + "WHERE (f.homeTeam.id = :teamId OR f.awayTeam.id = :teamId) "
            + "ORDER BY f.season DESC")
    List<Integer> findSeasonYearsByTeam(@Param("teamId") Long teamId);

    /**
     * Lookup ucu icin: bir takimin verilen referans andan SONRA baslayan ilk
     * maci (yakin → uzak). {@link #findUpcomingByTeam}'den farki status filtresi
     * yoktur; yalnizca {@code kickoffAt >= :ref} bakilir. Anasayfa gun bazli
     * filtrede "aranan takimin o gun maci yoksa siradaki maci öner" akisi icin.
     *
     * <p>Pageable ile limit 1 verilir; JOIN FETCH ile lig/takimlar tek sorguda
     * yuklenir (FixtureQueryService.toSummary lazy proxy'lere dokunur).
     */
    @Query("SELECT f FROM Fixture f "
            + "JOIN FETCH f.league "
            + "JOIN FETCH f.homeTeam "
            + "JOIN FETCH f.awayTeam "
            + "LEFT JOIN FETCH f.venue "
            + "WHERE (f.homeTeam.id = :teamId OR f.awayTeam.id = :teamId) "
            + "  AND f.kickoffAt >= :ref "
            + "ORDER BY f.kickoffAt ASC")
    List<Fixture> findNextByTeamAfter(@Param("teamId") Long teamId,
                                      @Param("ref") Instant ref,
                                      org.springframework.data.domain.Pageable pageable);

    /**
     * Lookup ucu icin: bir takimin verilen referans andan ÖNCE baslayan son
     * maci (yeni → eski). {@link #findRecentByTeam}'den farki status filtresi
     * yoktur; yalnizca {@code kickoffAt < :ref} bakilir.
     *
     * <p>Pageable ile limit 1 verilir; JOIN FETCH ile lig/takimlar tek sorguda
     * yuklenir.
     */
    @Query("SELECT f FROM Fixture f "
            + "JOIN FETCH f.league "
            + "JOIN FETCH f.homeTeam "
            + "JOIN FETCH f.awayTeam "
            + "LEFT JOIN FETCH f.venue "
            + "WHERE (f.homeTeam.id = :teamId OR f.awayTeam.id = :teamId) "
            + "  AND f.kickoffAt < :ref "
            + "ORDER BY f.kickoffAt DESC")
    List<Fixture> findPreviousByTeamBefore(@Param("teamId") Long teamId,
                                           @Param("ref") Instant ref,
                                           org.springframework.data.domain.Pageable pageable);

    /**
     * Form widget'i icin: bir takimin verilen andan ÖNCE OYNANMIS (biten) son
     * maclari, yeni → eski. {@link #findRecentByTeam}'in status filtresi +
     * {@link #findPreviousByTeamBefore}'un zaman filtresi birlikte; boylece
     * bakilan mevcut mac listeye GIRMEZ. Pageable ile limit verilir; JOIN FETCH
     * ile lig/takimlar tek sorguda yuklenir.
     */
    @Query("SELECT f FROM Fixture f "
            + "JOIN FETCH f.league "
            + "JOIN FETCH f.homeTeam "
            + "JOIN FETCH f.awayTeam "
            + "WHERE (f.homeTeam.id = :teamId OR f.awayTeam.id = :teamId) "
            + "  AND f.statusShort IN ('FT','AET','PEN','ABD','AWD','WO') "
            + "  AND f.kickoffAt < :ref "
            + "ORDER BY f.kickoffAt DESC")
    List<Fixture> findRecentPlayedByTeamBefore(@Param("teamId") Long teamId,
                                               @Param("ref") Instant ref,
                                               org.springframework.data.domain.Pageable pageable);

    /**
     * Son {@code since} anından beri güncellenen, HENÜZ oynanmamış (gelecek)
     * maçlar — {@link com.scorestv.indexnow.IndexNowSubmitJob} "yeni oluşan maç"
     * kolu için. Fixture entity'sinde {@code createdAt} yok (kendi @Id'si
     * atanmış, BaseEntity extend etmez); bunun yerine {@code updatedAt} +
     * "kickoff gelecekte" ile yeni fikstür kayıtları yakalanır (canlı/biten
     * maçlar bu koldan hariç, kendi kolunda gelir).
     *
     * <p>Takım adları canonical slug için lazımdır; JOIN FETCH ile yüklenir.
     */
    @Query("SELECT f FROM Fixture f "
            + "JOIN FETCH f.homeTeam "
            + "JOIN FETCH f.awayTeam "
            + "WHERE f.updatedAt > :since AND f.kickoffAt BETWEEN :now AND :until "
            + "ORDER BY f.updatedAt DESC")
    List<Fixture> findUpcomingUpdatedSince(@Param("since") Instant since,
                                           @Param("now") Instant now,
                                           @Param("until") Instant until,
                                           org.springframework.data.domain.Pageable pageable);

    /**
     * YAKIN zamanda oynanıp ({@code kickoffAt > recentCutoff}) sonucu kesinleşmiş
     * (FT/AET/PEN) ve son {@code since} anından beri güncellenen maçlar —
     * {@link com.scorestv.indexnow.IndexNowSubmitJob} "yeni biten maç" kolu için.
     *
     * <p>{@code recentCutoff} (ör. son 48 saat) ŞART: aksi halde eski biten
     * maçların istatistik/hydrate senkronu {@code updatedAt}'i tazeleyince binlerce
     * eski maç yeniden IndexNow'a gönderiliyordu. En taze güncellenenler önce
     * gelsin diye {@code updatedAt DESC} sıralı; çağıran {@code Pageable} ile
     * üst sınır koyar. Takım adları canonical slug için JOIN FETCH edilir.
     */
    @Query("SELECT f FROM Fixture f "
            + "JOIN FETCH f.homeTeam "
            + "JOIN FETCH f.awayTeam "
            + "WHERE f.statusShort IN :statuses AND f.updatedAt > :since "
            + "AND f.kickoffAt > :recentCutoff "
            + "ORDER BY f.updatedAt DESC")
    List<Fixture> findRecentlyFinishedUpdatedSince(
            @Param("statuses") Collection<String> statuses,
            @Param("since") Instant since,
            @Param("recentCutoff") Instant recentCutoff,
            org.springframework.data.domain.Pageable pageable);
}
