package com.scorestv.contact.web;

import com.scorestv.common.ApiException;
import com.scorestv.contact.ContactService;
import com.scorestv.contact.dto.ContactCreateRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * İletişim formu public endpoint'i.
 *
 * <p>{@code POST /api/v1/contact} — giriş gerektirmez (SecurityConfig'te
 * permitAll). Gelen mesaj DB'ye kaydedilir, admin panelinden okunur.
 */
@RestController
@RequestMapping("/api/v1/contact")
public class PublicContactController {

    private static final int MAX_IP_LEN = 64;

    private final ContactService service;

    public PublicContactController(ContactService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> submit(@Valid @RequestBody ContactCreateRequest req,
                                      HttpServletRequest http) {
        Long id = service.create(req, clientIp(http));
        return Map.of("status", "ok", "id", id);
    }

    /**
     * Mobil "Bize Ulaşın" — resim/video ekli sorun bildirimi (multipart).
     * Giriş gerektirmez. Alanlar: email (zorunlu), subject (opsiyonel, serbest
     * metin), message (zorunlu), files (opsiyonel, en fazla 5 resim/video).
     */
    @PostMapping(path = "/report", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> report(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam("email") String email,
            @RequestParam(value = "subject", required = false) String subject,
            @RequestParam("message") String message,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            HttpServletRequest http) {
        if (email == null || email.isBlank()) {
            throw ApiException.badRequest("E-posta zorunlu.");
        }
        if (message == null || message.isBlank()) {
            throw ApiException.badRequest("Mesaj boş olamaz.");
        }
        if (message.length() > 4000) {
            throw ApiException.badRequest("Mesaj 4000 karakteri aşamaz.");
        }
        Long id = service.createReport(name, email, subject, message,
                clientIp(http), "mobile", files);
        return Map.of("status", "ok", "id", id);
    }

    /** nginx/Cloudflare arkasinda gercek IP X-Forwarded-For'da gelir. */
    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String first = xff.split(",")[0].trim();
            if (!first.isBlank()) return trim(first);
        }
        return trim(req.getRemoteAddr());
    }

    private static String trim(String ip) {
        if (ip == null) return null;
        return ip.length() > MAX_IP_LEN ? ip.substring(0, MAX_IP_LEN) : ip;
    }
}
