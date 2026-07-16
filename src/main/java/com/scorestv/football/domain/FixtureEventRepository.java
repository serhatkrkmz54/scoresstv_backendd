package com.scorestv.football.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** Maç olaylarına erişim. */
public interface FixtureEventRepository extends JpaRepository<FixtureEvent, Long> {

    /**
     * Bir maçın olayları KRONOLOJİK sıralı (maç detayı zaman çizelgesi).
     *
     * <p>Sıralama {@code timeElapsed} + {@code timeExtra} birlikte olmalı: 90'
     * (extra=null) golü, 90+5 (extra=5) değişikliğin ÜSTÜNDE gelmeli. {@code extra}
     * nullable olduğu için türetilmiş {@code OrderBy...} NULLS LAST tuzağına düşer
     * (null'ı sona atıp 90'ı 90+5'in ALTINA koyar); bu yüzden {@code COALESCE(extra,0)}
     * ile açıkça sıralarız. Eşit (elapsed,extra) için {@code id ASC} = API'nin
     * verdiği özgün sıra (kararlı).
     */
    @Query("SELECT e FROM FixtureEvent e WHERE e.fixture.id = :fixtureId "
            + "ORDER BY e.timeElapsed ASC, COALESCE(e.timeExtra, 0) ASC, e.id ASC")
    List<FixtureEvent> findByFixtureIdOrderByTimeElapsedAsc(@Param("fixtureId") Long fixtureId);

    /**
     * Bir maçın tüm olaylarını siler. Senkronda olaylar silinip yeniden
     * yazıldığı için kullanılır. Çağıran metot {@code @Transactional} olmalıdır.
     */
    void deleteByFixtureId(Long fixtureId);

    /**
     * Verilen maçlar için takım başına KIRMIZI KART sayısı — anasayfa canlı
     * tab'ında ve favorilerde "kırmızı kart yiyen takım" rozeti için toplu sorgu.
     * Kırmızı = type 'Card' + detail içinde 'red' (frontend isRedCard ile aynı).
     *
     * @return [fixtureId (Long), teamId (Long), adet (Long)] satırları
     */
    @Query("SELECT e.fixture.id, e.team.id, COUNT(e) FROM FixtureEvent e "
            + "WHERE e.fixture.id IN :fixtureIds "
            + "AND LOWER(e.type) = 'card' AND LOWER(e.detail) LIKE '%red%' "
            + "AND e.team.id IS NOT NULL "
            + "GROUP BY e.fixture.id, e.team.id")
    List<Object[]> countRedCardsByFixtureIds(
            @Param("fixtureIds") Collection<Long> fixtureIds);
}
