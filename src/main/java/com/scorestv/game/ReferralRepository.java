package com.scorestv.game;

import org.springframework.data.jpa.repository.JpaRepository;

/** {@link Referral} CRUD + anti-abuse / istatistik sorgulari. */
public interface ReferralRepository extends JpaRepository<Referral, Long> {

    /** Bu yeni kullanici daha once bir davet kodu kullandi mi? (tek kullanim). */
    boolean existsByRefereeId(Long refereeId);

    /** Bu kullanici kac kisiyi davet etti (kac kisi kodunu kullandi). */
    long countByReferrerId(Long referrerId);
}
