package com.scorestv.game;

import com.scorestv.common.ApiException;
import com.scorestv.game.ReferralDtos.ReferralInfo;
import com.scorestv.game.ReferralDtos.RedeemRequest;
import com.scorestv.game.ReferralDtos.RedeemResult;
import com.scorestv.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Davet / referans uclari. {@code /api/v1/game/**} guvenlik katmaninda
 * permitAll; login burada {@code currentUser} null kontrolu ile zorlanir
 * (GameController ile ayni desen).
 */
@RestController
@RequestMapping("/api/v1/game/referral")
public class ReferralController {

    private final ReferralService service;

    public ReferralController(ReferralService service) {
        this.service = service;
    }

    /** Davet bilgim — kodum + kac kisi davet ettim + kazandigim puan. */
    @GetMapping
    public ReferralInfo info(@AuthenticationPrincipal CurrentUser currentUser) {
        if (currentUser == null) {
            throw ApiException.unauthorized("Davet kodu icin giris gerekli.");
        }
        return service.info(currentUser.id());
    }

    /** Bir davet kodu kullan — her iki tarafa puan. */
    @PostMapping("/redeem")
    public RedeemResult redeem(@Valid @RequestBody RedeemRequest req,
                               @AuthenticationPrincipal CurrentUser currentUser) {
        if (currentUser == null) {
            throw ApiException.unauthorized("Kod kullanmak icin giris gerekli.");
        }
        return service.redeem(currentUser.id(), req.code());
    }
}
