package com.scorestv.contact;

import com.scorestv.contact.dto.ContactCreateRequest;
import com.scorestv.contact.dto.ContactMessageView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class ContactService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ContactMessageRepository repository;

    public ContactService(ContactMessageRepository repository) {
        this.repository = repository;
    }

    /** Public form gönderimi — yeni mesaj kaydeder, id döner. */
    @Transactional
    public Long create(ContactCreateRequest req, String ip) {
        ContactMessage m = new ContactMessage();
        m.setName(req.name().trim());
        m.setEmail(req.email().trim());
        m.setSubject(req.subject() != null && !req.subject().isBlank() ? req.subject().trim() : null);
        m.setMessage(req.message().trim());
        m.setStatus(ContactStatus.NEW);
        m.setIpAddress(ip);
        return repository.save(m).getId();
    }

    /** Admin listesi — opsiyonel durum filtresi, en yeni önce. */
    @Transactional(readOnly = true)
    public Page<ContactMessageView> list(ContactStatus status, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        Pageable pageable = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ContactMessage> result = (status != null)
                ? repository.findByStatus(status, pageable)
                : repository.findAll(pageable);
        return result.map(ContactMessageView::of);
    }

    /** Okunmamış (NEW) mesaj sayısı — admin rozeti için. */
    @Transactional(readOnly = true)
    public long countNew() {
        return repository.countByStatus(ContactStatus.NEW);
    }

    @Transactional
    public ContactMessageView updateStatus(Long id, ContactStatus status) {
        ContactMessage m = repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Mesaj bulunamadı: " + id));
        m.setStatus(status);
        return ContactMessageView.of(repository.save(m));
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
