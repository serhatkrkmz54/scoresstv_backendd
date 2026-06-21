package com.scorestv.football.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Takım verisine erişim. */
public interface TeamRepository extends JpaRepository<Team, Long> {

    /** En bayat (en eski updated_at) covered takimlar — surekli tazelik
     *  enqueuer'i bunlari oncelikli tazeler. */
    List<Team> findByCoveredTrueOrderByUpdatedAtAsc(Pageable pageable);

    /** updated_at'i "claim" olarak simdiye ceker — surekli tazelik rotasyonu icin. */
    @Transactional
    @Modifying
    @Query("UPDATE Team t SET t.updatedAt = CURRENT_TIMESTAMP WHERE t.id = :id")
    void touchUpdatedAt(@Param("id") Long id);

    /**
     * Logosu henüz MinIO'ya aynalanmamış (logo_key boş) ama kaynak logo URL'i
     * olan takımlar — en fazla 200. Görsel aynalama işinin parti sorgusudur.
     */
    List<Team> findTop200ByLogoKeyIsNullAndLogoUrlIsNotNull();

    /**
     * Stadyumu bağlı en az bir takım var mı? Takım senkronunun (en az bir kez)
     * çalışıp çalışmadığını saptamak için kullanılır.
     */
    boolean existsByVenueIsNotNull();

    /** Türkçe adı girilmiş (name_tr dolu) takım sayısı — çeviri durumu içindir. */
    long countByNameTrIsNotNull();

    /** Kapsamli (covered=true) takimlar — DailyTeamRefreshJob bunu kullanir. */
    List<Team> findByCoveredTrue();

    /** Milli takimlar (national=true) — FIFA siralamasi isim eslestirmesi icin. */
    List<Team> findByNationalTrue();
}
