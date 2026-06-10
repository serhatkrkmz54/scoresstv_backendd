package com.scorestv.football.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Takım verisine erişim. */
public interface TeamRepository extends JpaRepository<Team, Long> {

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
}
