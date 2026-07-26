package com.scorestv.user.dto;

import java.util.Map;

/** Panel "Üyeler" üst istatistik kartları. */
public record AdminUserStatsResponse(
        long total,
        long enabled,
        long disabled,
        /** Son 7 günde kayıt olan üye sayısı. */
        long newLast7Days,
        /** Rol → sayı (USER/EDITOR/ADMIN). */
        Map<String, Long> byRole,
        long google,
        long apple,
        long local
) {}
