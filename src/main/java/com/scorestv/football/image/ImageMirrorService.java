package com.scorestv.football.image;

import com.scorestv.football.domain.Coach;
import com.scorestv.football.domain.CoachRepository;
import com.scorestv.football.domain.Country;
import com.scorestv.football.domain.CountryRepository;
import com.scorestv.football.domain.League;
import com.scorestv.football.domain.LeagueRepository;
import com.scorestv.football.domain.Player;
import com.scorestv.football.domain.PlayerRepository;
import com.scorestv.football.domain.Team;
import com.scorestv.football.domain.TeamRepository;
import com.scorestv.football.domain.Venue;
import com.scorestv.football.domain.VenueRepository;
import com.scorestv.storage.MinioStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Logo/bayrak görsellerini API-Football media sunucusundan indirip MinIO'ya
 * aynalar. Aynalanan her varlığın {@code *_key} alanı doldurulur; bu alan dolu
 * olanlar bir daha indirilmez (idempotent, devam ettirilebilir).
 *
 * <p>İndirmeler arasında küçük bir gecikme uygulanır — media sunucusunun
 * saniye/dakika hız limitine takılmamak için. Görsel indirmek API-Football
 * günlük kotasından düşmez.
 */
@Service
public class ImageMirrorService {

    private static final Logger log = LoggerFactory.getLogger(ImageMirrorService.class);

    /** İndirmeler arası bekleme (ms) — media sunucusu hız limitine saygı için. */
    private static final long THROTTLE_MS = 80L;

    /** mirror() placeholder tespit edince bu sentinel'i doner (null/key'den ayri). */
    private static final String PLACEHOLDER = "__PLACEHOLDER__";

    private final MinioStorageService storage;
    private final TeamRepository teamRepository;
    private final LeagueRepository leagueRepository;
    private final CountryRepository countryRepository;
    private final PlayerRepository playerRepository;
    private final CoachRepository coachRepository;
    private final VenueRepository venueRepository;
    private final RestClient downloadClient;

    /// API-Football "Image not available" placeholder gorsellerinin SHA-256
    /// hash'leri (kucuk harf hex). Indirilen gorselin hash'i bu kumedeyse
    /// gercek logo degildir — depolanmaz, mobil kendi fallback'ini gosterir.
    /// Konfigurasyon: scorestv.image.placeholder-sha256 (virgulle ayrilmis).
    private final Set<String> placeholderHashes;

    public ImageMirrorService(MinioStorageService storage,
                              TeamRepository teamRepository,
                              LeagueRepository leagueRepository,
                              CountryRepository countryRepository,
                              PlayerRepository playerRepository,
                              CoachRepository coachRepository,
                              VenueRepository venueRepository,
                              @Value("${scorestv.image.placeholder-sha256:}")
                              String placeholderHashesCsv) {
        this.storage = storage;
        this.teamRepository = teamRepository;
        this.leagueRepository = leagueRepository;
        this.countryRepository = countryRepository;
        this.playerRepository = playerRepository;
        this.coachRepository = coachRepository;
        this.venueRepository = venueRepository;
        this.placeholderHashes = Arrays.stream(placeholderHashesCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        if (!placeholderHashes.isEmpty()) {
            log.info("Placeholder gorsel filtresi aktif: {} hash", placeholderHashes.size());
        }

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.downloadClient = RestClient.builder().requestFactory(factory).build();
    }

    /** Verilen baytlarin SHA-256 hex hash'i (kucuk harf). */
    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (Exception ex) {
            return "";
        }
    }

    /** Indirilen gorsel bilinen bir placeholder mi? */
    private boolean isPlaceholderImage(byte[] data) {
        if (placeholderHashes.isEmpty()) return false;
        return placeholderHashes.contains(sha256Hex(data));
    }

    /** Throttle'li indirme — hata/bos durumda null (loglamaz). */
    private byte[] downloadQuiet(String url) {
        try {
            Thread.sleep(THROTTLE_MS);
            byte[] data = downloadClient.get().uri(URI.create(url))
                    .retrieve().toEntity(byte[].class).getBody();
            return (data != null && data.length > 0) ? data : null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * Placeholder hash'(ler)ini KESFEDER — env'e elle hash bulmaya gerek kalmadan.
     *
     * <p>Mantik: API-Football "Image not available" gorseli, logosu olmayan
     * YUZLERCE takim/oyuncuda BIREBIR AYNI bayttir; gercek logolar ise benzersiz.
     * Bu yuzden bir ornek kume indirilip SHA-256'lari sayilirsa, en sik tekrar
     * eden hash(ler) placeholder'dir. Donen en ust hash'i (count yuksek) alip
     * {@code IMAGE_PLACEHOLDER_SHA256} env'ine yaz, yeniden baslat, purge cagir.
     *
     * @param sample taranacak takim + oyuncu sayisi (ust sinir; orn. 400)
     * @return >=2 kez tekrar eden hash'ler, en siktan aza dogru
     */
    public List<PlaceholderCandidate> detectPlaceholderCandidates(int sample) {
        int budget = Math.max(1, sample);
        Map<String, Integer> counts = new HashMap<>();
        Map<String, String> example = new HashMap<>();

        List<Team> teams = teamRepository
                .findAll(PageRequest.of(0, budget)).getContent();
        for (Team t : teams) {
            tallyHash(t.getLogoUrl(), counts, example);
        }
        List<Player> players = playerRepository
                .findAll(PageRequest.of(0, budget)).getContent();
        for (Player p : players) {
            tallyHash(p.getPhotoUrl(), counts, example);
        }

        List<PlaceholderCandidate> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() >= 2) {
                out.add(new PlaceholderCandidate(
                        e.getKey(), e.getValue(), example.get(e.getKey())));
            }
        }
        out.sort(Comparator.comparingInt(PlaceholderCandidate::count).reversed());
        log.info("Placeholder kesfi: {} aday hash bulundu (sample={}).",
                out.size(), budget);
        return out;
    }

    private void tallyHash(String url, Map<String, Integer> counts,
                           Map<String, String> example) {
        if (url == null || url.isEmpty()) return;
        byte[] data = downloadQuiet(url);
        if (data == null) return;
        String h = sha256Hex(data);
        if (h.isEmpty()) return;
        counts.merge(h, 1, Integer::sum);
        example.putIfAbsent(h, url);
    }

    /**
     * Takım logoları, lig logoları, ülke bayrakları, oyuncu fotoğraflarını,
     * teknik direktör fotoğraflarını ve stadyum görsellerini aynalar.
     */
    public ImageMirrorResult mirrorAll() {
        log.info("Görsel aynalama başladı.");
        int teams = mirrorTeamLogos();
        int leagues = mirrorLeagueLogos();
        int countries = mirrorCountryFlags();
        int players = mirrorPlayerPhotos();
        int coaches = mirrorCoachPhotos();
        int venues = mirrorVenueImages();
        ImageMirrorResult result = new ImageMirrorResult(
                teams, leagues, countries, players, coaches, venues);
        log.info("Görsel aynalama bitti: {}", result);
        return result;
    }

    /** Fotoğrafı henüz aynalanmamış teknik direktörleri parti parti işler. */
    public int mirrorCoachPhotos() {
        Set<Long> attempted = new HashSet<>();
        int mirrored = 0;
        while (true) {
            List<Coach> batch = coachRepository.findTop200ByPhotoKeyIsNullAndPhotoUrlIsNotNull();
            batch.removeIf(c -> attempted.contains(c.getId()));
            if (batch.isEmpty()) {
                break;
            }
            for (Coach c : batch) {
                attempted.add(c.getId());
                String key = mirror(c.getPhotoUrl(), "coaches", c.getId());
                if (PLACEHOLDER.equals(key)) {
                    c.setPhotoUrl(null);
                    coachRepository.save(c);
                } else if (key != null) {
                    c.setPhotoKey(key);
                    coachRepository.save(c);
                    mirrored++;
                }
            }
        }
        log.info("Tekik direktör fotoğrafları aynalandı: {}", mirrored);
        return mirrored;
    }

    /** Gorseli henuz aynalanmamis stadyumlari parti parti isler. */
    public int mirrorVenueImages() {
        Set<Long> attempted = new HashSet<>();
        int mirrored = 0;
        while (true) {
            List<Venue> batch = venueRepository.findTop200ByImageKeyIsNullAndImageUrlIsNotNull();
            batch.removeIf(v -> attempted.contains(v.getId()));
            if (batch.isEmpty()) {
                break;
            }
            for (Venue v : batch) {
                attempted.add(v.getId());
                String key = mirror(v.getImageUrl(), "venues", v.getId());
                if (PLACEHOLDER.equals(key)) {
                    v.setImageUrl(null);
                    venueRepository.save(v);
                } else if (key != null) {
                    v.setImageKey(key);
                    venueRepository.save(v);
                    mirrored++;
                }
            }
        }
        log.info("Stadyum görselleri aynalandı: {}", mirrored);
        return mirrored;
    }

    /** Logosu henüz aynalanmamış takımları parti parti işler. */
    public int mirrorTeamLogos() {
        Set<Long> attempted = new HashSet<>();
        int mirrored = 0;
        while (true) {
            List<Team> batch = teamRepository.findTop200ByLogoKeyIsNullAndLogoUrlIsNotNull();
            batch.removeIf(team -> attempted.contains(team.getId()));
            if (batch.isEmpty()) {
                break;
            }
            for (Team team : batch) {
                attempted.add(team.getId());
                String key = mirror(team.getLogoUrl(), "teams", team.getId());
                if (PLACEHOLDER.equals(key)) {
                    team.setLogoUrl(null);
                    teamRepository.save(team);
                } else if (key != null) {
                    team.setLogoKey(key);
                    teamRepository.save(team);
                    mirrored++;
                }
            }
        }
        log.info("Takım logoları aynalandı: {}", mirrored);
        return mirrored;
    }

    /** Logosu henüz aynalanmamış ligleri parti parti işler. */
    public int mirrorLeagueLogos() {
        Set<Long> attempted = new HashSet<>();
        int mirrored = 0;
        while (true) {
            List<League> batch = leagueRepository.findTop200ByLogoKeyIsNullAndLogoUrlIsNotNull();
            batch.removeIf(league -> attempted.contains(league.getId()));
            if (batch.isEmpty()) {
                break;
            }
            for (League league : batch) {
                attempted.add(league.getId());
                String key = mirror(league.getLogoUrl(), "leagues", league.getId());
                if (PLACEHOLDER.equals(key)) {
                    league.setLogoUrl(null);
                    leagueRepository.save(league);
                } else if (key != null) {
                    league.setLogoKey(key);
                    leagueRepository.save(league);
                    mirrored++;
                }
            }
        }
        log.info("Lig logoları aynalandı: {}", mirrored);
        return mirrored;
    }

    /**
     * Fotoğrafı henüz aynalanmamış oyuncuları parti parti işler. 50k+ oyuncu
     * potansiyeli olduğu için throttle ve batch parametreleri korunur; günlük
     * cron yalnız işi büyütmeden devam ettirir (idempotent).
     */
    public int mirrorPlayerPhotos() {
        Set<Long> attempted = new HashSet<>();
        int mirrored = 0;
        while (true) {
            List<Player> batch = playerRepository.findTop200ByPhotoKeyIsNullAndPhotoUrlIsNotNull();
            batch.removeIf(p -> attempted.contains(p.getId()));
            if (batch.isEmpty()) {
                break;
            }
            for (Player p : batch) {
                attempted.add(p.getId());
                String key = mirror(p.getPhotoUrl(), "players", p.getId());
                if (PLACEHOLDER.equals(key)) {
                    p.setPhotoUrl(null);
                    playerRepository.save(p);
                } else if (key != null) {
                    p.setPhotoKey(key);
                    playerRepository.save(p);
                    mirrored++;
                }
            }
        }
        log.info("Oyuncu fotoğrafları aynalandı: {}", mirrored);
        return mirrored;
    }

    /** Bayrağı henüz aynalanmamış ülkeleri parti parti işler. */
    public int mirrorCountryFlags() {
        Set<Long> attempted = new HashSet<>();
        int mirrored = 0;
        while (true) {
            List<Country> batch = countryRepository.findTop200ByFlagKeyIsNullAndFlagUrlIsNotNull();
            batch.removeIf(country -> attempted.contains(country.getId()));
            if (batch.isEmpty()) {
                break;
            }
            for (Country country : batch) {
                attempted.add(country.getId());
                String key = mirror(country.getFlagUrl(), "flags", country.getId());
                if (PLACEHOLDER.equals(key)) {
                    country.setFlagUrl(null);
                    countryRepository.save(country);
                } else if (key != null) {
                    country.setFlagKey(key);
                    countryRepository.save(country);
                    mirrored++;
                }
            }
        }
        log.info("Ülke bayrakları aynalandı: {}", mirrored);
        return mirrored;
    }

    /**
     * MEVCUT placeholder'lari temizler — takim/lig/ulke. Key'i dolu varliklarin
     * gorselini kaynaktan yeniden indirip hash kontrol eder; placeholder ise
     * MinIO nesnesini siler ve key+url'i null'lar (mobil fallback gosterir).
     * Asenkron — admin endpoint tetikler, sonucu loglardan izlenir.
     */
    @Async
    public void purgePlaceholdersAsync() {
        if (placeholderHashes.isEmpty()) {
            log.warn("purgePlaceholders: placeholder-sha256 bos — atlandi.");
            return;
        }
        log.info("Placeholder temizligi basladi (team/league/country).");
        int removed = 0;
        int page = 0;
        while (true) {
            var slice = teamRepository.findAll(PageRequest.of(page, 200));
            if (slice.isEmpty()) break;
            for (Team t : slice) {
                if (t.getLogoKey() != null && t.getLogoUrl() != null
                        && isPlaceholderAt(t.getLogoUrl())) {
                    deleteQuiet(t.getLogoKey());
                    t.setLogoKey(null);
                    t.setLogoUrl(null);
                    teamRepository.save(t);
                    removed++;
                }
            }
            if (!slice.hasNext()) break;
            page++;
        }
        page = 0;
        while (true) {
            var slice = leagueRepository.findAll(PageRequest.of(page, 200));
            if (slice.isEmpty()) break;
            for (League l : slice) {
                if (l.getLogoKey() != null && l.getLogoUrl() != null
                        && isPlaceholderAt(l.getLogoUrl())) {
                    deleteQuiet(l.getLogoKey());
                    l.setLogoKey(null);
                    l.setLogoUrl(null);
                    leagueRepository.save(l);
                    removed++;
                }
            }
            if (!slice.hasNext()) break;
            page++;
        }
        page = 0;
        while (true) {
            var slice = countryRepository.findAll(PageRequest.of(page, 200));
            if (slice.isEmpty()) break;
            for (Country c : slice) {
                if (c.getFlagKey() != null && c.getFlagUrl() != null
                        && isPlaceholderAt(c.getFlagUrl())) {
                    deleteQuiet(c.getFlagKey());
                    c.setFlagKey(null);
                    c.setFlagUrl(null);
                    countryRepository.save(c);
                    removed++;
                }
            }
            if (!slice.hasNext()) break;
            page++;
        }
        log.info("Placeholder temizligi bitti: {} kayit kaldirildi.", removed);
    }

    /** Kaynak URL'den indirip placeholder mi diye bakar (throttle'li). */
    private boolean isPlaceholderAt(String url) {
        try {
            Thread.sleep(THROTTLE_MS);
            byte[] data = downloadClient.get().uri(URI.create(url))
                    .retrieve().toEntity(byte[].class).getBody();
            return data != null && data.length > 0 && isPlaceholderImage(data);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private void deleteQuiet(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException ex) {
            log.warn("MinIO silme hata {}: {}", key, ex.getMessage());
        }
    }

    /**
     * Harici görseli aynalar (indir+store) ve key döner — basketbol gibi diğer
     * modüllerin {@link #mirror} mantığını yeniden kullanması için public giriş.
     * Placeholder ise {@link #PLACEHOLDER}, hata ise {@code null} döner.
     */
    public String mirrorExternal(String sourceUrl, String folder, Long id) {
        return mirror(sourceUrl, folder, id);
    }

    /** {@link #mirrorExternal} dönüşü placeholder sentinel'i mi? */
    public boolean isPlaceholderKey(String key) {
        return PLACEHOLDER.equals(key);
    }

    /**
     * Bir görseli kaynak URL'den indirip MinIO'ya yükler ve nesne anahtarını döner.
     * Başarısızlıkta {@code null} döner (loglanır) — varlık bir sonraki turda denenir.
     */
    private String mirror(String sourceUrl, String folder, Long id) {
        try {
            Thread.sleep(THROTTLE_MS);
            ResponseEntity<byte[]> response = downloadClient.get()
                    .uri(URI.create(sourceUrl))
                    .retrieve()
                    .toEntity(byte[].class);

            byte[] data = response.getBody();
            if (data == null || data.length == 0) {
                log.warn("Görsel boş geldi: {}", sourceUrl);
                return null;
            }
            // API-Football "Image not available" placeholder'i — depolama, gercek
            // logo degil. Mobil bos URL alip kendi fallback'ini (ikon/monogram) gosterir.
            if (isPlaceholderImage(data)) {
                log.info("Placeholder gorsel atlandi: {}", sourceUrl);
                return PLACEHOLDER;
            }
            String extension = extensionOf(sourceUrl);
            String key = folder + "/" + id + "." + extension;
            MediaType contentType = response.getHeaders().getContentType();
            String type = (contentType != null)
                    ? contentType.toString()
                    : contentTypeForExtension(extension);
            storage.upload(key, data, type);
            return key;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Görsel aynalama yarıda kesildi.");
            return null;
        } catch (RuntimeException ex) {
            log.warn("Görsel aynalanamadı: {} — {}", sourceUrl, ex.getMessage());
            return null;
        }
    }

    /** URL'nin son dosya uzantısını küçük harf döner; bulunamazsa "png". */
    private String extensionOf(String url) {
        int dot = url.lastIndexOf('.');
        int slash = url.lastIndexOf('/');
        if (dot > slash && dot < url.length() - 1) {
            return url.substring(dot + 1).toLowerCase(Locale.ROOT);
        }
        return "png";
    }

    private String contentTypeForExtension(String extension) {
        return switch (extension) {
            case "png" -> "image/png";
            case "svg" -> "image/svg+xml";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            default -> "application/octet-stream";
        };
    }
}
