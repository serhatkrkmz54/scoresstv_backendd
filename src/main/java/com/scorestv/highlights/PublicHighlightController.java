package com.scorestv.highlights;

import com.scorestv.highlights.dto.HighlightView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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

    /** Bir maçın (fixture) highlight'ları. Yoksa/bitmemişse boş liste. */
    @GetMapping("/fixtures/{fixtureId}")
    public List<HighlightView> forFixture(@PathVariable Long fixtureId) {
        return service.forFixture(fixtureId);
    }
}
