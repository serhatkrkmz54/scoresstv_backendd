package com.scorestv.football.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Stadyum verisine erişim. */
public interface VenueRepository extends JpaRepository<Venue, Long> {

    /** Türkçe adı girilmiş (name_tr dolu) stadyum sayısı — çeviri durumu içindir. */
    long countByNameTrIsNotNull();

    /** Gorseli henuz aynalanmamis stadyumlar (max 200) — image mirror icin. */
    List<Venue> findTop200ByImageKeyIsNullAndImageUrlIsNotNull();

    /**
     * Yaris-guvenli stadyum upsert'i. Es zamanli lazy sync'ler ayni venue id'sini
     * ayni anda INSERT etmeye calisinca venues_pkey (23505) patlayip TUM fikstur
     * batch'ini abort ediyordu. Postgres ON CONFLICT ile atomik: varsa name/city/
     * updated_at gunceller, yoksa ekler. name_tr ve diger elle girilen alanlara
     * DOKUNMAZ.
     */
    @Modifying
    @Query(value = "INSERT INTO venues (id, name, city, updated_at) "
            + "VALUES (:id, :name, :city, now()) "
            + "ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, "
            + "city = EXCLUDED.city, updated_at = now()",
            nativeQuery = true)
    void upsert(@Param("id") Long id, @Param("name") String name, @Param("city") String city);
}
