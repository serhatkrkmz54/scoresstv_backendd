package com.scorestv.highlights;

import com.scorestv.highlights.dto.HighlightView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Maç highlight/özet public endpoint'i. Highlightly proxy'si — anahtar
 * sunucuda, sonuç cache'li. Yalnızca biten maçlarda dolu liste döner.
 */
@RestController
@RequestMapping("/api/v1/highlights")
public class PublicHighlightController {

    private final HighlightsService service;

    public PublicHighlightController(HighlightsService service) {
        this.service = service;
    }

    /**
     * Bir maçın (fixture) highlight'ları. Yoksa/bitmemişse boş liste.
     *
     * <p>Ülke, coğrafi engelli highlight'ları gömmemek için kullanılır:
     * Cloudflare {@code CF-IPCountry} header'ı (mobil doğrudan çağrı / web BFF
     * iletimi) ya da açık {@code ?country=} parametresi. "XX"/"T1" gibi bilinmeyen
     * değerler yok sayılır.
     */
    @GetMapping("/fixtures/{fixtureId}")
    public List<HighlightView> forFixture(
            @PathVariable Long fixtureId,
            @RequestHeader(value = "CF-IPCountry", required = false) String cfCountry,
            @RequestParam(value = "country", required = false) String countryParam) {
        return service.forFixture(fixtureId, resolveCountry(cfCountry, countryParam));
    }

    /** Geçerli bir ülke kodu seç: önce açık param, sonra CF-IPCountry. */
    private static String resolveCountry(String cfCountry, String countryParam) {
        String c = isValidCc(countryParam) ? countryParam : cfCountry;
        return isValidCc(c) ? c.trim().toUpperCase() : null;
    }

    private static boolean isValidCc(String c) {
        if (c == null) return false;
        String t = c.trim();
        // Cloudflare bilinmeyen için "XX", Tor için "T1" döner — geçersiz say.
        return t.length() == 2 && !t.equalsIgnoreCase("XX") && !t.equalsIgnoreCase("T1");
    }
}
