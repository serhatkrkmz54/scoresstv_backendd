package com.scorestv.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    /** Belirtilen kullanicinin tum aktif refresh token'larini iptal eder. */
    @Modifying
    @Query("UPDATE RefreshToken t SET t.revoked = true "
            + "WHERE t.userId = :userId AND t.revoked = false")
    int revokeAllByUserId(@Param("userId") Long userId);

    /**
     * Suresi dolmus token'lari siler (temizlik job'i icin).
     * Iptal edilmis ama suresi dolmamis token'lar reuse detection icin saklanir.
     */
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
