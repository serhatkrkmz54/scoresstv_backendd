package com.scorestv.football.live;

import com.scorestv.football.domain.FixtureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Maç durumu (başladı/bitti) bildirimleri için TAM-BİR-KEZ kapısı.
 *
 * <p>Atomik {@code UPDATE ... WHERE notif_*_at IS NULL} ile yarışa dayanıklı:
 * eşzamanlı veya tekrar eden tick'lerden yalnız BİRİ 1 satır günceller (claim'i
 * kazanır) ve push'u o gönderir. Bu yaklaşım diff'e, in-memory dedup'a,
 * uygulama restart'ına ve "durumu başka bir yol (detay lazy-sync) önce
 * ilerletti" durumuna BAĞIMSIZ çalışır — bu yüzden "bazen gitmiyor" sorununu
 * kökten çözer.
 */
@Service
public class FixtureNotifyGate {

    private final FixtureRepository fixtureRepository;

    public FixtureNotifyGate(FixtureRepository fixtureRepository) {
        this.fixtureRepository = fixtureRepository;
    }

    /** "Başladı" bildirimi için claim — kazanırsa {@code true} (tam-bir-kez). */
    @Transactional
    public boolean claimKickoff(Long fixtureId) {
        return fixtureRepository.claimKickoffNotification(fixtureId, Instant.now()) == 1;
    }

    /** "Bitti" bildirimi için claim — kazanırsa {@code true} (tam-bir-kez). */
    @Transactional
    public boolean claimFinal(Long fixtureId) {
        return fixtureRepository.claimFinalNotification(fixtureId, Instant.now()) == 1;
    }
}
