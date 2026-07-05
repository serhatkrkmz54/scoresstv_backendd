package com.scorestv.news;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsAuditLogRepository extends JpaRepository<NewsAuditLog, Long> {
}
