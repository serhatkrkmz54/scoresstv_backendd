package com.scorestv.contact;

import com.scorestv.contact.dto.ContactCreateRequest;
import com.scorestv.contact.dto.ContactMessageView;
import com.scorestv.storage.MinioStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class ContactService {

    private static final Logger log = LoggerFactory.getLogger(ContactService.class);

    private static final int MAX_PAGE_SIZE = 100;

    /** Mobil "Bize Ulaşın" eki: en fazla dosya + dosya başı boyut. */
    private static final int MAX_FILES = 5;
    private static final long MAX_FILE_BYTES = 50L * 1024 * 1024; // 50 MB

    private final ContactMessageRepository repository;
    private final MinioStorageService storage;

    public ContactService(ContactMessageRepository repository,
                          MinioStorageService storage) {
        this.repository = repository;
        this.storage = storage;
    }

    /** Public form gönderimi (web) — yeni mesaj kaydeder, id döner. */
    @Transactional
    public Long create(ContactCreateRequest req, String ip) {
        ContactMessage m = new ContactMessage();
        m.setName(req.name().trim());
        m.setEmail(req.email().trim());
        m.setSubject(req.subject() != null && !req.subject().isBlank() ? req.subject().trim() : null);
        m.setMessage(req.message().trim());
        m.setStatus(ContactStatus.NEW);
        m.setSource("web");
        m.setIpAddress(ip);
        return repository.save(m).getId();
    }

    /**
     * Mobil "Bize Ulaşın" / sorun bildirimi — mesaj + opsiyonel resim/video
     * ekleriyle kaydeder. Giriş gerektirmez; e-posta kullanıcı tarafından
     * verilir. Ekler MinIO'ya yüklenir, anahtar/URL DB'ye yazılır.
     *
     * <p>Dosya sayısı {@value #MAX_FILES}, dosya başı {@value #MAX_FILE_BYTES}
     * byte ile sınırlı; yalnız {@code image/*} ve {@code video/*} kabul edilir.
     * Bir ekin yüklenmesi başarısız olsa bile mesaj kaydı KAYBOLMAZ (best-effort).
     */
    @Transactional
    public Long createReport(String name, String email, String subject, String message,
                             String ip, String source, List<MultipartFile> files) {
        ContactMessage m = new ContactMessage();
        m.setName(resolveName(name, email));
        m.setEmail(email.trim());
        m.setSubject(subject != null && !subject.isBlank() ? subject.trim() : null);
        m.setMessage(message.trim());
        m.setStatus(ContactStatus.NEW);
        m.setSource(source != null && !source.isBlank() ? source : "mobile");
        m.setIpAddress(ip);
        m = repository.save(m); // id, ek dosya anahtarı için gerekli

        if (files != null && !files.isEmpty()) {
            int count = 0;
            for (MultipartFile f : files) {
                if (count >= MAX_FILES) break;
                if (f == null || f.isEmpty()) continue;
                String ct = f.getContentType();
                if (ct == null || !(ct.startsWith("image/") || ct.startsWith("video/"))) {
                    continue; // sadece resim/video
                }
                if (f.getSize() > MAX_FILE_BYTES) {
                    continue; // 50MB üstü atla
                }
                try {
                    String ext = extensionFor(f.getOriginalFilename(), ct);
                    String key = "contact/" + m.getId() + "/" + UUID.randomUUID()
                            + (ext != null ? "." + ext : "");
                    String url = storage.upload(key, f.getBytes(), ct);
                    ContactAttachment a = new ContactAttachment();
                    a.setStorageKey(key);
                    a.setUrl(url);
                    a.setContentType(ct);
                    a.setFileSize(f.getSize());
                    a.setOriginalName(clip(f.getOriginalFilename(), 255));
                    m.addAttachment(a);
                    count++;
                } catch (Exception e) {
                    log.warn("Bize Ulaşın eki yüklenemedi (messageId={}): {}",
                            m.getId(), e.getMessage());
                }
            }
            if (count > 0) {
                repository.save(m); // cascade ile ekler yazılır
            }
        }
        return m.getId();
    }

    /** İsim boşsa e-posta yerel kısmından türet, o da yoksa jenerik. */
    private static String resolveName(String name, String email) {
        if (name != null && !name.isBlank()) return name.trim();
        if (email != null && email.contains("@")) {
            String local = email.substring(0, email.indexOf('@')).trim();
            if (!local.isBlank()) return clip(local, 120);
        }
        return "Mobil Kullanıcı";
    }

    private static String extensionFor(String filename, String contentType) {
        if (filename != null && filename.contains(".")) {
            String ext = filename.substring(filename.lastIndexOf('.') + 1)
                    .toLowerCase().replaceAll("[^a-z0-9]", "");
            if (!ext.isBlank() && ext.length() <= 5) return ext;
        }
        if (contentType != null && contentType.contains("/")) {
            String sub = contentType.substring(contentType.indexOf('/') + 1)
                    .toLowerCase().replaceAll("[^a-z0-9]", "");
            if (sub.equals("jpeg")) return "jpg";
            if (sub.equals("quicktime")) return "mov";
            if (!sub.isBlank() && sub.length() <= 5) return sub;
        }
        return null;
    }

    private static String clip(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
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
