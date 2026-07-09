package com.scorestv.news;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NewsAuditLogRepository extends JpaRepository<NewsAuditLog, Long> {

    /** Panel dashboard "son aktivite" akisi — en yeni 12 denetim kaydi. */
    List<NewsAuditLog> findTop12ByOrderByAtDesc();

    /** Denetim gunlugu tam sayfa — en yeni once. */
    Page<NewsAuditLog> findAllByOrderByAtDesc(Pageable pageable);

    /** Denetim gunlugu — belirli eyleme gore filtreli, en yeni once. */
    Page<NewsAuditLog> findByActionOrderByAtDesc(String action, Pageable pageable);
}
