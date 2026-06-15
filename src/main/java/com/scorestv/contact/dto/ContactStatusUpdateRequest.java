package com.scorestv.contact.dto;

import com.scorestv.contact.ContactStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Admin'in bir mesajın durumunu güncellemesi (NEW / READ / ARCHIVED).
 */
public record ContactStatusUpdateRequest(
        @NotNull(message = "Durum zorunlu.")
        ContactStatus status
) {}
