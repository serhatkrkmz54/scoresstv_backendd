package com.scorestv.football.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Maç olaylarına erişim. */
public interface FixtureEventRepository extends JpaRepository<FixtureEvent, Long> {

    /** Bir maçın olayları, dakikaya göre sıralı (maç detayı zaman çizelgesi). */
    List<FixtureEvent> findByFixtureIdOrderByTimeElapsedAsc(Long fixtureId);

    /**
     * Bir maçın tüm olaylarını siler. Senkronda olaylar silinip yeniden
     * yazıldığı için kullanılır. Çağıran metot {@code @Transactional} olmalıdır.
     */
    void deleteByFixtureId(Long fixtureId);
}
