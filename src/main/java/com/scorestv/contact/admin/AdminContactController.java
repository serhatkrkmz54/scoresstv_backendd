package com.scorestv.contact.admin;

import com.scorestv.contact.ContactService;
import com.scorestv.contact.ContactStatus;
import com.scorestv.contact.dto.ContactMessageView;
import com.scorestv.contact.dto.ContactStatusUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * İletişim mesajları admin endpoint'leri.
 *
 * <p>Tümü ADMIN rolü gerektirir ({@code @PreAuthorize}). SecurityConfig'te
 * {@code anyRequest().authenticated()} altinda kalir; rol kontrolu burada.
 */
@RestController
@RequestMapping("/api/v1/admin/contact")
@PreAuthorize("hasRole('ADMIN')")
public class AdminContactController {

    private final ContactService service;

    public AdminContactController(ContactService service) {
        this.service = service;
    }

    /** Mesaj listesi — opsiyonel ?status=NEW|READ|ARCHIVED + sayfalama. */
    @GetMapping
    public Page<ContactMessageView> list(
            @RequestParam(required = false) ContactStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(status, page, size);
    }

    /** Okunmamış mesaj sayısı (admin menü rozeti). */
    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount() {
        return Map.of("count", service.countNew());
    }

    /** Durum güncelle (okundu/arşiv). */
    @PatchMapping("/{id}/status")
    public ContactMessageView updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody ContactStatusUpdateRequest req) {
        return service.updateStatus(id, req.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
