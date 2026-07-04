package com.scorestv.predictions;

import com.scorestv.common.ApiException;
import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.predictions.dto.PredictionResultView;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * Maç sonucu tahmin oylaması (anonim). Kullanıcı/cihaz başına tek oy; oylama
 * yalnız kickoff'tan ÖNCE açıktır (maç başlayınca kilitlenir, dağılım görünür).
 */
@Service
public class PredictionService {

    /** Bu statülerde maç başlamamıştır → oylama açık. */
    private static final Set<String> NOT_STARTED = Set.of("NS", "TBD");
    private static final Set<String> CHOICES = Set.of("HOME", "DRAW", "AWAY");
    private static final String SPORT = "FOOTBALL";

    // Oy-şişirme freni: aynı IP saatte en fazla bu kadar oy POST'u atabilir.
    // Normal kullanıcı birkaç maça oy verir; script'le binlerce sahte voterId
    // üretmeyi caydırır (voterId dedup'ına ek katman).
    private static final String VOTE_IP_PREFIX = "predvote:ip:";
    private static final int MAX_VOTES_PER_IP = 40;
    private static final Duration VOTE_IP_WINDOW = Duration.ofHours(1);

    private final PredictionVoteRepository repository;
    private final FixtureRepository fixtureRepository;
    private final StringRedisTemplate redis;

    public PredictionService(PredictionVoteRepository repository,
                             FixtureRepository fixtureRepository,
                             StringRedisTemplate redis) {
        this.repository = repository;
        this.fixtureRepository = fixtureRepository;
        this.redis = redis;
    }

    /** Dağılım + (voterId verilirse) bu oylayanın seçimi. */
    @Transactional(readOnly = true)
    public PredictionResultView result(Long fixtureId, String voterId) {
        return _build(fixtureId, voterId, _votingOpen(fixtureId));
    }

    /** Oy ver/değiştir — yalnız oylama açıkken (kickoff'tan önce). */
    @Transactional
    public PredictionResultView vote(Long fixtureId, String voterId, String choice,
                                     String clientIp) {
        final String c = choice == null ? "" : choice.trim().toUpperCase();
        if (!CHOICES.contains(c)) {
            throw ApiException.badRequest("Geçersiz seçim (HOME/DRAW/AWAY).");
        }
        if (voterId == null || voterId.isBlank() || voterId.length() > 64) {
            throw ApiException.badRequest("Geçersiz oylayan kimliği.");
        }
        if (!_votingOpen(fixtureId)) {
            throw ApiException.badRequest("Oylama kapandı (maç başladı).");
        }
        _enforceIpLimit(clientIp);
        final String v = voterId.trim();
        final PredictionVote vote = repository
                .findByMatchIdAndSportAndVoterId(fixtureId, SPORT, v)
                .orElseGet(() -> {
                    PredictionVote n = new PredictionVote();
                    n.setMatchId(fixtureId);
                    n.setSport(SPORT);
                    n.setVoterId(v);
                    return n;
                });
        vote.setChoice(c);
        repository.save(vote);
        return _build(fixtureId, v, true);
    }

    /** Aynı IP'den saatte MAX_VOTES_PER_IP'ten fazla oy gelirse 429 döner. */
    private void _enforceIpLimit(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return; // IP yoksa (nadiren) limiti atla — voterId dedup yine korur.
        }
        String key = VOTE_IP_PREFIX + clientIp;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, VOTE_IP_WINDOW);
        }
        if (count != null && count > MAX_VOTES_PER_IP) {
            throw ApiException.tooManyRequests(
                    "Çok fazla oy denemesi. Lütfen bir süre sonra tekrar deneyin.");
        }
    }

    private boolean _votingOpen(Long fixtureId) {
        final Fixture f = fixtureRepository.findById(fixtureId).orElse(null);
        if (f == null) return false;
        final String status = f.getStatusShort();
        return status == null || NOT_STARTED.contains(status);
    }

    private PredictionResultView _build(Long fixtureId, String voterId, boolean open) {
        int home = 0, draw = 0, away = 0;
        for (Object[] row : repository.countByChoice(fixtureId, SPORT)) {
            final String choice = (String) row[0];
            final int cnt = ((Number) row[1]).intValue();
            switch (choice) {
                case "HOME" -> home = cnt;
                case "DRAW" -> draw = cnt;
                case "AWAY" -> away = cnt;
                default -> { /* yok */ }
            }
        }
        String myChoice = null;
        if (voterId != null && !voterId.isBlank()) {
            final Optional<PredictionVote> mine = repository
                    .findByMatchIdAndSportAndVoterId(fixtureId, SPORT, voterId.trim());
            myChoice = mine.map(PredictionVote::getChoice).orElse(null);
        }
        return new PredictionResultView(
                home, draw, away, home + draw + away, myChoice, open);
    }
}
