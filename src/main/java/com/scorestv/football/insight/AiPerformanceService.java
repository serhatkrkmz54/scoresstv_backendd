package com.scorestv.football.insight;

import com.scorestv.football.insight.AiPerformanceView.AiMonthBlock;
import com.scorestv.football.insight.AiPerformanceView.AiStatBlock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Notlanmış AI tahminlerini aylık/yıllık/tüm-zaman isabet karnesine çevirir. */
@Service
public class AiPerformanceService {

    private final AiPredictionRepository repo;

    public AiPerformanceService(AiPredictionRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public AiPerformanceView performance() {
        final Instant now = Instant.now();
        final AiStatBlock month = block(repo.aggregate(now.minus(Duration.ofDays(30))));
        final AiStatBlock quarter = block(repo.aggregate(now.minus(Duration.ofDays(90))));
        final AiStatBlock year = block(repo.aggregate(now.minus(Duration.ofDays(365))));
        final AiStatBlock all = block(repo.aggregate(Instant.EPOCH));

        final List<AiMonthBlock> months = new ArrayList<>();
        for (Object[] r : repo.monthlyAggregate(now.minus(Duration.ofDays(365)))) {
            final int y = num(r[0]);
            final int m = num(r[1]);
            final long total = lng(r[2]);
            final long rt = lng(r[3]);
            final long rh = lng(r[4]);
            final long oh = lng(r[5]);
            final long bh = lng(r[6]);
            final long eh = lng(r[7]);
            months.add(new AiMonthBlock(
                    String.format("%04d-%02d", y, m),
                    total,
                    overall(rh, oh, bh, rt, total),
                    pct(rh, rt), pct(oh, total), pct(bh, total), pct(eh, total)));
        }
        return new AiPerformanceView(month, quarter, year, all, months);
    }

    private AiStatBlock block(AiAgg a) {
        final long total = n(a == null ? null : a.total());
        final long rt = n(a == null ? null : a.resultTotal());
        final long rh = n(a == null ? null : a.resultHits());
        final long oh = n(a == null ? null : a.ouHits());
        final long bh = n(a == null ? null : a.bttsHits());
        final long eh = n(a == null ? null : a.exactHits());
        return new AiStatBlock(total, rt,
                rh, pct(rh, rt),
                oh, pct(oh, total),
                bh, pct(bh, total),
                eh, pct(eh, total),
                overall(rh, oh, bh, rt, total));
    }

    /** Birleşik isabet: 1X2 (favori olanlar) + Alt/Üst + KG toplam isabet oranı. */
    private static int overall(long rh, long oh, long bh, long rt, long total) {
        return pct(rh + oh + bh, rt + total + total);
    }

    private static long n(Long x) {
        return x == null ? 0L : x;
    }

    private static long lng(Object o) {
        return o instanceof Number num ? num.longValue() : 0L;
    }

    private static int num(Object o) {
        return o instanceof Number num ? num.intValue() : 0;
    }

    private static int pct(long hit, long total) {
        return total <= 0 ? 0 : (int) Math.round(100.0 * hit / total);
    }
}
