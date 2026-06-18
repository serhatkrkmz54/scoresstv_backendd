package com.scorestv.broadcasts.web;

import com.scorestv.broadcasts.BroadcastService;
import com.scorestv.broadcasts.dto.BroadcastView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Maç TV yayını public endpoint'i. TheSportsDB proxy'si — anahtar sunucuda,
 * sonuç cache'li. Eşleşme yoksa boş liste döner.
 */
@RestController
@RequestMapping("/api/v1/broadcasts")
public class PublicBroadcastController {

    private final BroadcastService service;

    public PublicBroadcastController(BroadcastService service) {
        this.service = service;
    }

    /**
     * Bir maçın (fixture) TV kanalları. {@code country} kullanıcının ülkesini
     * öne almak için: Cloudflare {@code CF-IPCountry} header'ı (mobil doğrudan /
     * web BFF iletimi) ya da açık {@code ?country=} parametresi.
     */
    @GetMapping("/fixtures/{fixtureId}")
    public List<BroadcastView> forFixture(
            @PathVariable Long fixtureId,
            @RequestHeader(value = "CF-IPCountry", required = false) String cfCountry,
            @RequestParam(value = "country", required = false) String countryParam) {
        return service.forFixture(fixtureId, resolveCountry(cfCountry, countryParam));
    }

    private static String resolveCountry(String cfCountry, String countryParam) {
        String c = isValidCc(countryParam) ? countryParam : cfCountry;
        return isValidCc(c) ? c.trim().toUpperCase() : null;
    }

    private static boolean isValidCc(String c) {
        if (c == null) return false;
        String t = c.trim();
        return t.length() == 2 && !t.equalsIgnoreCase("XX") && !t.equalsIgnoreCase("T1");
    }
}
