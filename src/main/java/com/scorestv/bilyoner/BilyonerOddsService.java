package com.scorestv.bilyoner;

import com.scorestv.football.domain.Team;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Bilyoner oranlarını fikstür maçlarıyla eşleştirir.
 *
 * <p>Tek fetch tüm bülteni getirir; {@code cacheSeconds} boyunca bellekte
 * snapshot olarak tutulur (her maç detayında yeniden çekilmez). Eşleştirme:
 * normalize edilmiş ev/deplasman adları (EN + TR) + kickoff toleransı.
 *
 * <p>{@code enabled=false} ise her zaman {@code null} döner — özellik kapalı.
 */
@Service
public class BilyonerOddsService {

    private static final Logger log = LoggerFactory.getLogger(BilyonerOddsService.class);

    private final BilyonerOddsClient client;
    private final BilyonerProperties props;

    private volatile List<BilyonerOddsClient.RawEvent> snapshot = List.of();
    private volatile Instant snapshotAt = Instant.EPOCH;

    public BilyonerOddsService(BilyonerOddsClient client, BilyonerProperties props) {
        this.client = client;
        this.props = props;
    }

    /** Bu maça ait oranlar; kapalıysa veya eşleşme yoksa {@code null}. */
    public MatchOdds forFixture(Team home, Team away, Instant kickoff) {
        if (!props.enabled() || home == null || away == null) return null;
        var events = snapshot();
        if (events.isEmpty()) return null;

        Set<String> homeKeys = keys(home);
        Set<String> awayKeys = keys(away);
        long tolMs = Duration.ofMinutes(props.kickoffToleranceMinutes()).toMillis();

        for (var e : events) {
            if (e.home() == null || e.away() == null) continue;
            if (!matches(homeKeys, e.home()) || !matches(awayKeys, e.away())) {
                continue;
            }
            if (kickoff != null && e.kickoff() != null) {
                long diff = Math.abs(kickoff.toEpochMilli() - e.kickoff().toEpochMilli());
                if (diff > tolMs) continue;
            }
            MatchOdds odds = toMatchOdds(e);
            if (odds != null) return odds;
        }
        return null;
    }

    /** Zamanlanmış iş için snapshot'ı zorla tazeler (freshness kontrolü yok). */
    public void refresh() {
        if (!props.enabled()) return;
        var data = client.fetchEvents();
        if (!data.isEmpty()) {
            snapshot = data;
            snapshotAt = Instant.now();
            log.info("Bilyoner oran snapshot (zamanlanmış) güncellendi: {} maç", data.size());
        }
    }

    private List<BilyonerOddsClient.RawEvent> snapshot() {
        if (fresh() && !snapshot.isEmpty()) return snapshot;
        synchronized (this) {
            if (fresh() && !snapshot.isEmpty()) return snapshot;
            var data = client.fetchEvents();
            if (!data.isEmpty()) {
                snapshot = data;
                log.info("Bilyoner oran snapshot güncellendi: {} maç", data.size());
            }
            // Boş döndüyse eski snapshot korunur; her iki halde de retry fırtınasını
            // önlemek için zaman damgasını ileri al.
            snapshotAt = Instant.now();
            return snapshot;
        }
    }

    private boolean fresh() {
        return Duration.between(snapshotAt, Instant.now()).getSeconds() < props.cacheSeconds();
    }

    private Set<String> keys(Team t) {
        Set<String> s = new HashSet<>();
        if (t.getName() != null) s.add(norm(t.getName()));
        if (t.getNameTr() != null) s.add(norm(t.getNameTr()));
        s.remove("");
        return s;
    }

    /**
     * Bir takım Bilyoner adıyla eşleşiyor mu? Önce birebir (normalize sonrası),
     * tutmazsa içerme (Helsingborg ↔ Helsingborgs IF gibi ek/önek farkları) —
     * ≥4 karakter şartıyla kısa yanlış eşleşmeleri önler. Ev+deplasman ikisinin
     * de eşleşmesi + kickoff toleransı yanlış pozitifleri zaten kısıtlar.
     */
    private static boolean matches(Set<String> keys, String bilyonerName) {
        final String bn = norm(bilyonerName);
        if (bn.isEmpty()) return false;
        for (String k : keys) {
            if (k.equals(bn)) return true;
            if (k.length() >= 4 && bn.length() >= 4
                    && (k.contains(bn) || bn.contains(k))) {
                return true;
            }
        }
        return false;
    }

    // NFD ile ayrışmayan özel harfler (ø, đ, ł, ı, ð, þ) için elle eşleme.
    private static final Map<Character, Character> EXTRA_MAP = Map.of(
            'ø', 'o', 'đ', 'd', 'ł', 'l', 'ı', 'i', 'ð', 'd', 'þ', 't');

    /**
     * İsim normalizasyonu — TÜM aksan/varyasyonları sadeleştirir:
     * küçük harf → ß/æ/œ açılımı → NFD ile aksanları ayır+sil → kalan özel
     * harfleri eşle → sadece [a-z0-9] tut.
     *
     * <p>"Västra Frölunda IF" → "vastrafrolundaif" = "Vastra Frolunda IF".
     * "Beşiktaş" → "besiktas", "Mönchengladbach" → "monchengladbach".
     */
    private static String norm(String name) {
        if (name == null) return "";
        String s = name.toLowerCase(Locale.ROOT)
                .replace("ß", "ss").replace("æ", "ae").replace("œ", "oe");
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            Character mapped = EXTRA_MAP.get(c);
            if (mapped != null) c = mapped;
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) sb.append(c);
        }
        return sb.toString();
    }

    private MatchOdds toMatchOdds(BilyonerOddsClient.RawEvent e) {
        List<MatchOdds.Market> markets = new ArrayList<>();
        addMarket(markets, e, "Maç Sonucu",
                new String[][]{{"1", "MS 1"}, {"X", "MS X"}, {"2", "MS 2"}});
        addMarket(markets, e, "Çifte Şans",
                new String[][]{{"1-X", "ÇŞ 1-X"}, {"1-2", "ÇŞ 1-2"}, {"X-2", "ÇŞ X-2"}});
        addMarket(markets, e, "Karşılıklı Gol",
                new String[][]{{"Var", "KG Var"}, {"Yok", "KG Yok"}});
        addMarket(markets, e, "1.5 Alt/Üst",
                new String[][]{{"Alt", "1,5 Alt"}, {"Üst", "1,5 Üst"}});
        addMarket(markets, e, "2.5 Alt/Üst",
                new String[][]{{"Alt", "2,5 Alt"}, {"Üst", "2,5 Üst"}});
        addMarket(markets, e, "3.5 Alt/Üst",
                new String[][]{{"Alt", "3,5 Alt"}, {"Üst", "3,5 Üst"}});
        addMarket(markets, e, "İlk Yarı Sonucu",
                new String[][]{{"1", "İY 1"}, {"X", "İY X"}, {"2", "İY 2"}});
        addMarket(markets, e, "İY 0.5 Alt/Üst",
                new String[][]{{"Alt", "İY 0,5 Alt"}, {"Üst", "İY 0,5 Üst"}});
        addMarket(markets, e, "İY 1.5 Alt/Üst",
                new String[][]{{"Alt", "İY 1,5 Alt"}, {"Üst", "İY 1,5 Üst"}});
        addMarket(markets, e, "Gol Aralığı",
                new String[][]{
                        {"0-1", "0-1 Gol"}, {"2-3", "2-3 Gol"},
                        {"4-5", "4-5 Gol"}, {"6+", "6+ Gol"}});
        if (markets.isEmpty()) return null;

        String click = (props.affiliateUrl() == null || props.affiliateUrl().isBlank())
                ? "https://www.bilyoner.com"
                : props.affiliateUrl();
        return new MatchOdds("Bilyoner", click, markets);
    }

    /**
     * Market'i ekle — yalnızca TÜM seçenekleri GEÇERLİ oransa.
     * Bilyoner kapalı/fiyatlanmamış marketleri {@code val="0"} olarak döndürür;
     * herhangi bir seçenek 0/eksik/anlamsız (≤1.00) ise market hiç gösterilmez —
     * böylece kartta "0" oran görünmez.
     */
    private void addMarket(List<MatchOdds.Market> out, BilyonerOddsClient.RawEvent e,
                           String name, String[][] labelKeys) {
        List<MatchOdds.Outcome> outcomes = new ArrayList<>();
        for (String[] lk : labelKeys) {
            String val = e.odds().get(lk[1]);
            if (!validOdd(val)) return;
            outcomes.add(new MatchOdds.Outcome(lk[0], val));
        }
        out.add(new MatchOdds.Market(name, outcomes));
    }

    /** Geçerli oran: sayıya çevrilebilen ve 1.00'dan büyük değer. */
    private static boolean validOdd(String v) {
        if (v == null || v.isBlank()) return false;
        try {
            return Double.parseDouble(v.replace(',', '.')) > 1.0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
