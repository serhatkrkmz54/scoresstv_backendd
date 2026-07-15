package com.scorestv.game;

import com.scorestv.user.UserRegisteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Yeni üye kaydı commit olduktan SONRA hoşgeldin bonusunu (10 Scores Coin) verir.
 *
 * <p>{@code AFTER_COMMIT}: kullanıcı satırı DB'ye yazılmış olur (FK sağlanır) ve
 * bonus AYRI bir işlemde verilir — bonus hatası kaydı ASLA geri almaz. İdempotent
 * ({@link ScoresCoinService#grantWelcomeBonus}) olduğu için tekrar tetiklense de
 * çift coin olmaz.
 */
@Component
public class GameWelcomeBonusListener {

    private static final Logger log = LoggerFactory.getLogger(GameWelcomeBonusListener.class);

    private final ScoresCoinService scoresCoinService;

    public GameWelcomeBonusListener(ScoresCoinService scoresCoinService) {
        this.scoresCoinService = scoresCoinService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUserRegistered(UserRegisteredEvent event) {
        try {
            boolean granted = scoresCoinService.grantWelcomeBonus(event.userId());
            if (granted) {
                log.info("Hoşgeldin bonusu verildi: userId={} (+{} coin)",
                        event.userId(), ScoresCoinService.WELCOME_BONUS_COINS);
            }
        } catch (RuntimeException ex) {
            log.warn("Hoşgeldin bonusu verilemedi userId={}: {}",
                    event.userId(), ex.getMessage());
        }
    }
}
