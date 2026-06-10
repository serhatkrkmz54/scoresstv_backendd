package com.scorestv.football.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Ülke referans verisine erişim. */
public interface CountryRepository extends JpaRepository<Country, Long> {

    /** Ada göre ülke — senkron upsert'inin anahtar aramasıdır. */
    Optional<Country> findByName(String name);

    /**
     * Bayrağı henüz MinIO'ya aynalanmamış (flag_key boş) ama kaynak bayrak
     * URL'i olan ülkeler — en fazla 200. Görsel aynalama işinin parti sorgusudur.
     */
    List<Country> findTop200ByFlagKeyIsNullAndFlagUrlIsNotNull();

    /** Türkçe adı girilmiş (name_tr dolu) ülke sayısı — çeviri durumu içindir. */
    long countByNameTrIsNotNull();
}
