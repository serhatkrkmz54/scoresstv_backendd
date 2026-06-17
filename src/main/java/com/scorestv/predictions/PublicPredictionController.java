package com.scorestv.predictions;

import com.scorestv.predictions.dto.PredictionResultView;
import com.scorestv.predictions.dto.PredictionVoteRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Maç sonucu tahmin oylaması public endpoint'leri (anonim). voterId =
 * cihaz/tarayıcı başına anonim istemci kimliği.
 */
@RestController
@RequestMapping("/api/v1/predictions")
public class PublicPredictionController {

    private final PredictionService service;

    public PublicPredictionController(PredictionService service) {
        this.service = service;
    }

    /** Dağılım (+ voterId verilirse kendi seçimi + oylama açık mı). */
    @GetMapping("/fixtures/{fixtureId}")
    public PredictionResultView get(
            @PathVariable Long fixtureId,
            @RequestParam(value = "voterId", required = false) String voterId) {
        return service.result(fixtureId, voterId);
    }

    /** Oy ver/değiştir (kickoff'tan önce). */
    @PostMapping("/fixtures/{fixtureId}")
    public PredictionResultView vote(
            @PathVariable Long fixtureId,
            @RequestBody PredictionVoteRequest req) {
        return service.vote(fixtureId, req.voterId(), req.choice());
    }
}
