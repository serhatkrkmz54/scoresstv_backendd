package com.scorestv.game;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Scores Coin cüzdanı — bakiye + hareket defteri (ledger). Çözümleme motoru ve
 * (ileride) admin/mağaza buradan coin ekler/çıkarır. Ledger kaynak-doğrudur.
 */
@Service
public class ScoresCoinService {

    private final ScoresCoinLedgerRepository ledgerRepo;
    private final UserGameStatRepository statRepo;

    public ScoresCoinService(ScoresCoinLedgerRepository ledgerRepo,
                             UserGameStatRepository statRepo) {
        this.ledgerRepo = ledgerRepo;
        this.statRepo = statRepo;
    }

    /** Kullanıcı oyun özeti (yoksa oluştur). */
    @Transactional
    public UserGameStat getOrCreate(Long userId) {
        return statRepo.findById(userId).orElseGet(() -> {
            UserGameStat s = new UserGameStat();
            s.setUserId(userId);
            return statRepo.save(s);
        });
    }

    /**
     * Genel amaçlı coin hareketi (admin/mağaza vb.). Bakiye + (kazançsa) lifetime
     * güncellenir, ledger satırı yazılır. Çözümleme kendi streak-farkındalıklı
     * geçişini yaptığı için bunu KULLANMAZ.
     */
    @Transactional
    public void grant(Long userId, int delta, String reason, String refType, Long refId) {
        final UserGameStat stat = getOrCreate(userId);
        final long newBalance = stat.getCoinBalance() + delta;
        stat.setCoinBalance(newBalance);
        if (delta > 0) stat.setLifetimeCoins(stat.getLifetimeCoins() + delta);
        statRepo.save(stat);
        appendLedger(userId, delta, newBalance, reason, refType, refId);
    }

    /** Ledger satırı ekler (bakiye çağıran tarafından hesaplanmış olarak verilir). */
    @Transactional
    public void appendLedger(Long userId, int delta, long balanceAfter,
                             String reason, String refType, Long refId) {
        ScoresCoinLedger l = new ScoresCoinLedger();
        l.setUserId(userId);
        l.setDelta(delta);
        l.setBalanceAfter(balanceAfter);
        l.setReason(reason);
        l.setRefType(refType);
        l.setRefId(refId);
        ledgerRepo.save(l);
    }

    @Transactional(readOnly = true)
    public List<ScoresCoinLedger> history(Long userId, int page, int size) {
        return ledgerRepo.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(page, size));
    }
}
