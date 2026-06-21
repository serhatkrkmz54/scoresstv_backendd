package com.scorestv.football.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Lig verisine erişim. */
public interface LeagueRepository extends JpaRepository<League, Long> {

    /** Senkron kapsamına alınmış (covered = true) ligler. */
    List<League> findByCoveredTrue();

    /** En bayat (en eski updated_at) covered ligler — surekli tazelik
     *  enqueuer'i bunlari oncelikli tazeler. */
    List<League> findByCoveredTrueOrderByUpdatedAtAsc(Pageable pageable);

    /** updated_at'i "claim" olarak simdiye ceker — surekli tazelik rotasyonu icin. */
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            "UPDATE League l SET l.updatedAt = CURRENT_TIMESTAMP WHERE l.id = :id")
    void touchUpdatedAt(@org.springframework.data.repository.query.Param("id") Long id);

    /**
     * Logosu henüz MinIO'ya aynalanmamış (logo_key boş) ama kaynak logo URL'i
     * olan ligler — en fazla 200. Görsel aynalama işinin parti sorgusudur.
     */
    List<League> findTop200ByLogoKeyIsNullAndLogoUrlIsNotNull();

    /** Türkçe adı girilmiş (name_tr dolu) lig sayısı — çeviri durumu içindir. */
    long countByNameTrIsNotNull();

    /**
     * Lig adina + ulke ismine gore tek lig bulma — trophy widget'inda kullanilir.
     * Trophy yaniti yalniz {@code league + country + season} string'leri verir;
     * lig id'si yoktur. Bu yuzden (name, country) ile DB'de lookup yapip
     * name_tr/country_name_tr'ye yonlendiriyoruz. Case-insensitive eslesme.
     */
    java.util.Optional<League> findFirstByNameIgnoreCaseAndCountryNameIgnoreCase(
            String name, String countryName);

    /**
     * Standings sayfasi hub'i icin TUM ligler — ulkeye gore siralanmis.
     * Standings verisi olmayan ligler de listede gozukur; LeagueHubService
     * her birinin {@code hasStandings} bayragini ayrica isaretler.
     *
     * <p>Kullanici picker'da hangi ligin verisi oldugunu gorur, tikladiginda
     * standings yoksa lazy sync devreye girer (LeagueDetailLazySync).
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT l FROM League l ORDER BY l.countryName, l.name")
    java.util.List<League> findAllForHub();

    /**
     * Standings tablosunda satiri bulunan lig id'leri — hub'a "hasStandings"
     * bayragini set etmek icin tek sorguda doner (N+1 onlemi).
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT DISTINCT s.league.id FROM Standing s")
    java.util.List<Long> findLeagueIdsWithStandings();

    /**
     * Tum liglerin {@code covered} bayragini tek query ile set eder. Admin
     * "covered/all" endpoint'inden cagrilir; manuel id listesi olmadan tum
     * ligleri kapsama alir veya cikartir.
     *
     * <p>Note: {@code updated_at} otomatik tetiklenmez (native update),
     * gerekirse caller @Modifying flush yapsin.
     *
     * @return guncellenen satir sayisi
     */
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            "UPDATE League l SET l.covered = :covered")
    int setCoveredForAll(@org.springframework.data.repository.query.Param("covered") boolean covered);
}
