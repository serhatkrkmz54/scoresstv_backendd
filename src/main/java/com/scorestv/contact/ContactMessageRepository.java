package com.scorestv.contact;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, Long> {

    Page<ContactMessage> findByStatus(ContactStatus status, Pageable pageable);

    long countByStatus(ContactStatus status);
}
