package com.scorestv.comments;

import com.scorestv.comments.dto.AdminCommentPage;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Panel yorum moderasyonu (EDITOR/ADMIN). Tum spor kollarindaki yorumlari
 * listeler + soft-delete/geri-al. SecurityConfig: /api/v1/admin/** authenticated;
 * ayrica sinif-seviyesi @PreAuthorize ile role gatelenir.
 */
@RestController
@RequestMapping("/api/v1/admin/comments")
@PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
public class AdminCommentController {

    private final AdminCommentService service;

    public AdminCommentController(AdminCommentService service) {
        this.service = service;
    }

    /** Moderasyon listesi — spor/silinme durumu/metin filtreli, en yeni once. */
    @GetMapping
    public AdminCommentPage list(
            @RequestParam(required = false) String sport,
            @RequestParam(required = false) Boolean deleted,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return service.list(sport, deleted, q, page, size);
    }

    /** Yorumu gizle (soft-delete). Uygulamada aninda gorunmez olur. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.softDelete(id);
    }

    /** Silinmis yorumu geri getir (tekrar gorunur yapar). */
    @PostMapping("/{id}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restore(@PathVariable Long id) {
        service.restore(id);
    }
}
