package com.scorestv.football.sync;

import com.scorestv.common.ApiException;
import com.scorestv.football.ApiFootballClient;
import com.scorestv.football.ApiFootballResponse;
import com.scorestv.football.domain.Fixture;
import com.scorestv.football.domain.FixtureRepository;
import com.scorestv.football.sync.dto.FixtureApiDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * İki takım arasındaki Head-to-Head (geçmiş karşılaşmalar) senkronu.
 *
 * <p>Çağrı: {@code GET /fixtures/headtohead?h2h=A-B&last=N}. Dönen maçlar
 * mevcut {@link FixtureUpserter} ile {@code fixtures} tablosuna yazılır —
 * h2h için ayrı tablo yok; arşiv aynı tabloda büyür.
 *
 * <p>Bu sayede: bir maç detayına ilk girildikten sonra h2h fixtures'lar
 * DB'mizde kalır, sonraki sorgular DB'den (cache'ten) gelir.
 */
@Service
public class H2hSyncService {

    private static final Logger log = LoggerFactory.getLogger(H2hSyncService.class);

    private static final ParameterizedTypeReference<ApiFootballResponse<List<FixtureApiDto>>>
            H2H_TYPE = new ParameterizedTypeReference<>() {
            };

    /** API tek istekte en fazla bu kadar geçmiş maç döner — koruyucu üst sınır. */
    private static final int MAX_LAST = 30;

    private final ApiFootballClient client;
    private final FixtureUpserter upserter;
    private final FixtureRepository fixtureRepository;

    public H2hSyncService(ApiFootballClient client,
                          FixtureUpserter upserter,
                          FixtureRepository fixtureRepository) {
        this.client = client;
        this.upserter = upserter;
        this.fixtureRepository = fixtureRepository;
    }

    /**
     * İki takım arasındaki TÜM karşılaşmaları (geçmiş + bugün + gelecek)
     * çeker — {@code last}/{@code next} filtresi GEÇMEZ.
     *
     * <p><b>Neden filtreyi kaldırdık:</b> API'ye {@code last=10} geçince
     * sadece BİTMİŞ maçlar döner. Yeni karşılaşan takımlar için (ör. Tobol 2
     * × Taraz, hiç FT yok ama bugün canlı maç var) sonuç boş döner ve H2H
     * widget hep boş kalır. Filtresiz çağrıda tüm karşılaşmalar gelir; DB
     * sorgusu maç-detayı widget'ında {@code Pageable} ile sınırlanır.
     */
    public H2hSyncResult sync(Long teamA, Long teamB) {
        String h2hParam = teamA + "-" + teamB;
        ApiFootballResponse<List<FixtureApiDto>> response = client.get(
                "/fixtures/headtohead",
                Map.of("h2h", h2hParam),
                H2H_TYPE);
        List<FixtureApiDto> items = response.response();
        int upserted = upserter.upsert(items == null ? List.of() : items);
        if (upserted > 0) {
            log.info("H2H senkronu: teamA={} teamB={} — {} maç upsert edildi (tüm karşılaşmalar)",
                    teamA, teamB, upserted);
        }
        return new H2hSyncResult(teamA, teamB, upserted);
    }

    /**
     * Sadece son {@code last} BİTMİŞ karşılaşmayı çeker — admin endpoint
     * için (operasyonel kontrol, tarihsel arşive sınırlı). Genel kullanım
     * için {@link #sync(Long, Long)} tercih edilir.
     */
    public H2hSyncResult sync(Long teamA, Long teamB, int last) {
        int bounded = Math.max(1, Math.min(last, MAX_LAST));
        String h2hParam = teamA + "-" + teamB;
        ApiFootballResponse<List<FixtureApiDto>> response = client.get(
                "/fixtures/headtohead",
                Map.of("h2h", h2hParam, "last", bounded),
                H2H_TYPE);
        List<FixtureApiDto> items = response.response();
        int upserted = upserter.upsert(items == null ? List.of() : items);
        if (upserted > 0) {
            log.info("H2H senkronu (last={}): teamA={} teamB={} — {} maç upsert edildi",
                    bounded, teamA, teamB, upserted);
        }
        return new H2hSyncResult(teamA, teamB, upserted);
    }

    /**
     * Bir fixture id'sinden takım id'lerini çözüp TÜM h2h karşılaşmalarını
     * (filtresiz) senkronlar — public detay endpoint'i ve periyodik prefetch
     * için kullanılır.
     */
    public H2hSyncResult syncForFixture(Long fixtureId) {
        Fixture f = fixtureRepository.findById(fixtureId)
                .orElseThrow(() -> ApiException.notFound("Maç bulunamadı: " + fixtureId));
        return sync(f.getHomeTeam().getId(), f.getAwayTeam().getId());
    }

    /**
     * Admin override: son {@code last} BİTMİŞ karşılaşmayla sınırlandırılmış
     * senkron. Genel arama için {@link #syncForFixture(Long)} tercih edilmeli.
     */
    public H2hSyncResult syncForFixture(Long fixtureId, int last) {
        Fixture f = fixtureRepository.findById(fixtureId)
                .orElseThrow(() -> ApiException.notFound("Maç bulunamadı: " + fixtureId));
        return sync(f.getHomeTeam().getId(), f.getAwayTeam().getId(), last);
    }
}
