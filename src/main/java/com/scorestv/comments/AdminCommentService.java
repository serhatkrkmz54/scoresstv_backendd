package com.scorestv.comments;

import com.scorestv.comments.dto.AdminCommentPage;
import com.scorestv.comments.dto.AdminCommentView;
import com.scorestv.common.ApiException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Panel yorum moderasyonu servisi (EDITOR/ADMIN). Tum spor kollarindaki
 * yorumlari listeler (spor/silinme/metin filtreli), soft-delete eder veya
 * geri alir. Public yorum akisi zaten {@code deleted=false} filtreledigi icin
 * silinen yorum uygulamada gorunmez; geri-al onu tekrar gorunur yapar.
 */
@Service
public class AdminCommentService {

    private static final int MAX_PAGE_SIZE = 100;

    private final FixtureCommentRepository repo;

    public AdminCommentService(FixtureCommentRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public AdminCommentPage list(String sport, Boolean deleted, String q,
                                 int page, int size) {
        String sportFilter = (sport == null || sport.isBlank())
                ? null : sport.trim().toUpperCase();
        String query = (q == null || q.isBlank()) ? null : q.trim();
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        Page<FixtureComment> result =
                repo.findForModeration(sportFilter, deleted, query, pageable);

        List<AdminCommentView> items = result.getContent().stream()
                .map(c -> new AdminCommentView(
                        c.getId(),
                        c.getSport(),
                        c.getMatchId(),
                        c.getContent(),
                        c.isDeleted(),
                        c.getCreatedAt() != null ? c.getCreatedAt().toString() : null,
                        c.getUser() != null ? c.getUser().getId() : null,
                        c.getUser() != null ? c.getUser().getDisplayName() : null,
                        c.getUser() != null ? c.getUser().getCountry() : null,
                        c.getParent() != null ? c.getParent().getId() : null))
                .toList();

        return new AdminCommentPage(items, result.getTotalElements(), result.hasNext());
    }

    @Transactional
    public void softDelete(Long id) {
        FixtureComment c = repo.findById(id)
                .orElseThrow(() -> ApiException.notFound("Yorum bulunamadi."));
        c.setDeleted(true);
        repo.save(c);
    }

    @Transactional
    public void restore(Long id) {
        FixtureComment c = repo.findById(id)
                .orElseThrow(() -> ApiException.notFound("Yorum bulunamadi."));
        c.setDeleted(false);
        repo.save(c);
    }
}
