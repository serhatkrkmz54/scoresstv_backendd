package com.scorestv.game;

import com.scorestv.common.ApiException;
import com.scorestv.game.ReferralDtos.ReferralInfo;
import com.scorestv.game.ReferralDtos.RedeemResult;
import com.scorestv.user.User;
import com.scorestv.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

/**
 * Davet / referans mantigi — benzersiz kod uretimi + kod kullanma (her iki
 * tarafa {@value #REWARD_EACH} Scores Puani). Anti-abuse: kendi kodunu
 * kullanamama, hesap basina TEK kez, gecersiz kod reddi.
 */
@Service
public class ReferralService {

    private static final Logger log = LoggerFactory.getLogger(ReferralService.class);

    /** Her iki tarafa (davet eden + gelen) verilen puan. */
    public static final int REWARD_EACH = 50;

    private static final String REASON = "REFERRAL";
    /** Karisik karakterler (0/O, 1/I/L) haric — telefonda okunur/yazilir. */
    private static final char[] ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_LEN = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepo;
    private final ReferralRepository referralRepo;
    private final ScoresCoinService coinService;

    public ReferralService(UserRepository userRepo,
                           ReferralRepository referralRepo,
                           ScoresCoinService coinService) {
        this.userRepo = userRepo;
        this.referralRepo = referralRepo;
        this.coinService = coinService;
    }

    /** Kullanicinin davet bilgisi (kodu yoksa uretilir). */
    @Transactional
    public ReferralInfo info(Long userId) {
        String code = getOrCreateCode(userId);
        long invited = referralRepo.countByReferrerId(userId);
        return new ReferralInfo(code, invited, invited * REWARD_EACH, REWARD_EACH);
    }

    /** Kullanicinin davet kodu — yoksa uretip kaydeder. */
    @Transactional
    public String getOrCreateCode(Long userId) {
        User u = userRepo.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("Kullanici bulunamadi."));
        if (u.getReferralCode() != null && !u.getReferralCode().isBlank()) {
            return u.getReferralCode();
        }
        String code = generateUniqueCode();
        u.setReferralCode(code);
        userRepo.save(u);
        return code;
    }

    /**
     * Davet kodu kullan — kod sahibi + kodu kullanan yeni kullaniciya
     * {@value #REWARD_EACH} puan. Idempotent degil: hesap basina TEK kez
     * (uq_referrals_referee) — ikinci deneme "zaten kullandin" hatasi verir.
     */
    @Transactional
    public RedeemResult redeem(Long refereeId, String rawCode) {
        final String code = rawCode == null ? "" : rawCode.trim().toUpperCase();
        if (code.isEmpty()) {
            throw ApiException.badRequest("Davet kodu bos olamaz.");
        }
        if (referralRepo.existsByRefereeId(refereeId)) {
            throw ApiException.badRequest("Zaten bir davet kodu kullandin.");
        }
        final User referrer = userRepo.findByReferralCode(code)
                .orElseThrow(() -> ApiException.badRequest("Gecersiz davet kodu."));
        if (referrer.getId().equals(refereeId)) {
            throw ApiException.badRequest("Kendi davet kodunu kullanamazsin.");
        }

        Referral r = new Referral();
        r.setReferrerId(referrer.getId());
        r.setRefereeId(refereeId);
        r.setRewardEach(REWARD_EACH);
        referralRepo.save(r); // UNIQUE(referee_id) → yarista cift kayit engellenir

        coinService.grant(referrer.getId(), REWARD_EACH, REASON, "REFERRAL", refereeId);
        coinService.grant(refereeId, REWARD_EACH, REASON, "REFERRAL", referrer.getId());

        log.info("Davet kullanildi: referrer={} referee={} (+{}/+{})",
                referrer.getId(), refereeId, REWARD_EACH, REWARD_EACH);
        return new RedeemResult(REWARD_EACH);
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 12; attempt++) {
            String c = randomCode(CODE_LEN);
            if (!userRepo.existsByReferralCode(c)) {
                return c;
            }
        }
        // Cok nadir cakisma — bir uzun kodla garantiye al.
        return randomCode(CODE_LEN + 2);
    }

    private static String randomCode(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
