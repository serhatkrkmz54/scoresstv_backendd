package com.scorestv.volleyball.web;

import com.scorestv.volleyball.VolleyballGameService;
import com.scorestv.volleyball.VolleyballProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Voleybol mac uclari (public): tarihe gore fikstur, canli, tekil. */
@RestController
@RequestMapping("/api/v1/volleyball/games")
public class VolleyballGameController {

    private final VolleyballGameService service;
    private final VolleyballProperties props;

    public VolleyballGameController(VolleyballGameService service, VolleyballProperties props) {
        this.service = service;
        this.props = props;
    }

    /** {@code GET /api/v1/volleyball/games?date=YYYY-MM-DD&lang=tr} — gun bos ise bugun. */
    @GetMapping
    public List<VolleyballGameView> byDate(
            @RequestParam(required = false) String date,
            @RequestParam(required = false, defaultValue = "en") String lang) {
        LocalDate d = (date == null || date.isBlank())
                ? LocalDate.now(ZoneId.of(props.timezone()))
                : LocalDate.parse(date);
        return service.byDate(d, isTr(lang));
    }

    /** {@code GET /api/v1/volleyball/games/live} — su an canli maclar. */
    @GetMapping("/live")
    public List<VolleyballGameView> live(
            @RequestParam(required = false, defaultValue = "en") String lang) {
        return service.live(isTr(lang));
    }

    /** {@code GET /api/v1/volleyball/games/by-ids?ids=1,2,3} — favoriler (maks 50). */
    @GetMapping("/by-ids")
    public List<VolleyballGameView> byIds(
            @RequestParam("ids") List<Long> ids,
            @RequestParam(required = false, defaultValue = "en") String lang) {
        var capped = ids.size() > 50 ? ids.subList(0, 50) : ids;
        return service.byIds(capped, isTr(lang));
    }

    /** {@code GET /api/v1/volleyball/games/{id}} — tekil mac. */
    @GetMapping("/{id}")
    public VolleyballGameView byId(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "en") String lang) {
        return service.byId(id, isTr(lang)).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mac bulunamadi"));
    }

    private static boolean isTr(String lang) {
        return "tr".equalsIgnoreCase(lang);
    }
}
