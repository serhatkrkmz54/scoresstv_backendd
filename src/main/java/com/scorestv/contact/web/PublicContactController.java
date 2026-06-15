package com.scorestv.contact.web;

import com.scorestv.contact.ContactService;
import com.scorestv.contact.dto.ContactCreateRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
