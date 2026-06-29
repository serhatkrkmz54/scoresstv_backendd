package com.scorestv.football.image;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * ADMIN'in görsel aynalamayı elle tetiklemesi için endpoint.
 */
@RestController
@RequestMapping("/api/v1/admin/api-football/images")
@PreAuthorize("hasRole('ADMIN')")
public class ImageMirrorController {

    private final ImageMirrorService mirrorService;

    public ImageMirrorController(ImageMirrorService mirrorService) {
        this.mirrorService = mirrorService;
    }

    /** Aynalanmamış tüm logo/bayrak/oyuncu fotoğraflarını MinIO'ya aktarır. */
    @PostMapping("/mirror")
    public ImageMirrorResult mirror() {
        return mirrorService.mirrorAll();
    }

    /** Yalnız oyuncu fotoğraflarını aynalar (50k+ oyuncu için ayrı tetikleme). */
    @PostMapping("/mirror/players")
    public int mirrorPlayers() {
        return mirrorService.mirrorPlayerPhotos();
    }

    /** Yalnız teknik direktör fotoğraflarını aynalar. */
    @PostMapping("/mirror/coaches")
    public int mirrorCoaches() {
        return mirrorService.mirrorCoachPhotos();
    }

    /** Yalnız stadyum görsellerini aynalar. */
    @PostMapping("/mirror/venues")
    public int mirrorVenues() {
        return mirrorService.mirrorVenueImages();
    }

    /**
     * Placeholder hash'(ler)ini KESFEDER: ornek takim+oyuncu logolarini indirip
     * en sik tekrar eden hash'leri doner. Donen en ust {@code sha256}'yi
     * IMAGE_PLACEHOLDER_SHA256 env'ine yaz, yeniden baslat, sonra purge cagir.
     * Ornek: GET /mirror/detect-placeholders?sample=400
     */
    @GetMapping("/mirror/detect-placeholders")
    public List<PlaceholderCandidate> detectPlaceholders(
            @RequestParam(defaultValue = "400") int sample) {
        return mirrorService.detectPlaceholderCandidates(sample);
    }

    /**
     * MEVCUT "Image not available" placeholder'larini temizler (team/league/country):
     * MinIO nesnesini siler + key/url'i null'lar. Asenkron — loglardan izle.
     * Once scorestv.image.placeholder-sha256 ayarlanmis olmali.
     */
    @PostMapping("/mirror/purge-placeholders")
    public String purgePlaceholders() {
        mirrorService.purgePlaceholdersAsync();
        return "Placeholder temizligi baslatildi — ilerlemeyi loglardan izle.";
    }
}
