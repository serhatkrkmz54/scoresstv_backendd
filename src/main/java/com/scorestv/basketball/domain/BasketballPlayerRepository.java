package com.scorestv.basketball.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Basketbol oyuncu master tablosu. */
public interface BasketballPlayerRepository extends JpaRepository<BasketballPlayer, Long> {

    /** Slug -> oyuncu (oyuncu detay sayfasi URL'den cozumlemesi). */
    Optional<BasketballPlayer> findBySlug(String slug);

    /**
     * Profil sync'i hic yapilmamis veya freshness threshold'unu asmis
     * oyuncular. Daily refresh job covered liglerden gelen oyuncular icin
     * bu listeyi besler.
     */
    List<BasketballPlayer> findTop50ByLastProfileSyncedAtIsNullOrLastProfileSyncedAtBefore(
            Instant threshold);

    /** Foto MinIO'ya aynalanmamis oyuncular (foto var ama key yok). */
    List<BasketballPlayer> findTop200ByPhotoKeyIsNullAndPhotoIsNotNull();
}
