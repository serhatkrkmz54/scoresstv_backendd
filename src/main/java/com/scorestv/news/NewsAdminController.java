package com.scorestv.news;

import com.scorestv.common.ApiException;
import com.scorestv.news.dto.BulkNewsRequest;
import com.scorestv.news.dto.BulkResult;
import com.scorestv.news.dto.CreateNewsRequest;
import com.scorestv.news.dto.MediaUsage;
import com.scorestv.news.dto.NewsDetail;
import com.scorestv.news.dto.NewsPageResponse;
import com.scorestv.news.dto.NewsStats;
import com.scorestv.news.dto.NewsAuditPage;
import com.scorestv.news.dto.NewsListItem;
import com.scorestv.news.dto.RescheduleRequest;
import com.scorestv.news.dto.SaveSliderRequest;
import com.scorestv.news.dto.UpdateFlagsRequest;
import com.scorestv.indexnow.IndexNowService;
import com.scorestv.news.ingest.NewsIngestService;
import com.scorestv.football.seo.SeoProperties;
import org.springframework.web.bind.annotation.PatchMapping;
import com.scorestv.news.dto.TranslateNewsRequest;
import com.scorestv.news.dto.TranslateNewsResult;
import com.scorestv.news.dto.UpdateNewsRequest;
import com.scorestv.security.CurrentUser;
import com.scorestv.user.Role;
import org.springframework.beans.factory.ObjectProvider;
import com.scorestv.storage.MinioStorageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Haber (news) yonetim endpoint'leri. Tumu kimlik dogrulama gerektirir
 * (SecurityConfig: /api/v1/admin/news/** authenticated) ve metot seviyesinde
 * @PreAuthorize ile role gatelenir:
 * <ul>
 *   <li>EDITOR: olustur/guncelle/yayinla/yayindan kaldir/listele/gorsel yukle</li>
 *   <li>ADMIN: yalniz DELETE (soft-delete)</li>
 * </ul>
 * ADMIN, EDITOR yetkilerini de kapsayacak sekilde EDITOR endpoint'lerinde
 * "hasAnyRole('EDITOR','ADMIN')" ile yetkilendirilir.
 */
@RestController
@RequestMapping("/api/v1/admin/news")
public class NewsAdminController {

    /** Gorsel yukleme limitleri. */
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024; // 10 MB

    private final NewsService service;
    private final MinioStorageService storage;
    private final NewsTranslationService translationService;
    /** ES kapaliyken (scorestv.elasticsearch.enabled=false) bean yoktur — opsiyonel. */
    private final ObjectProvider<NewsIndexer> newsIndexer;
    private final IndexNowService indexNowService;
    private final SeoProperties seoProperties;
    private final NewsIngestService ingestService;

    public NewsAdminController(NewsService service, MinioStorageService storage,
                               NewsTranslationService translationService,
                               ObjectProvider<NewsIndexer> newsIndexer,
                               IndexNowService indexNowService,
                               SeoProperties seoProperties,
                               NewsIngestService ingestService) {
        this.service = service;
        this.storage = storage;
        this.translationService = translationService;
        this.newsIndexer = newsIndexer;
        this.indexNowService = indexNowService;
        this.seoProperties = seoProperties;
        this.ingestService = ingestService;
    }

    /**
     * Haber içe aktarmayı ELLE tetikle (ADMIN) — zamanlanmış job'u beklemeden
     * kaynaktan çeker, yeni haberleri DRAFT olarak açar. Açılan sayıyı döner.
     * DRAFT'lar normal admin listesinde (status=DRAFT) görünür, editör onaylar.
     */
    @PostMapping("/ingest/run")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> ingestRun() {
        int created = ingestService.runOnce();
        return Map.of("created", created);
    }

    /** Admin liste — tum durumlar + filtre + metin aramasi. */
    @GetMapping
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public NewsPageResponse list(
            @RequestParam(required = false) NewsStatus status,
            @RequestParam(required = false) String lang,
            @RequestParam(required = false) NewsCategory category,
            @RequestParam(required = false) String sport,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listForAdmin(status, lang, category, sport, q, page, size);
    }

    /** Admin detay — id ile (her durum). */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public NewsDetail get(@PathVariable Long id) {
        return service.getForAdmin(id);
    }

    /** Panel dashboard ozeti — kartlar + trend + en cok okunan + editor + aktivite. */
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public NewsStats stats() {
        return service.stats();
    }

    /** Denetim gunlugu tam sayfa (EDITOR/ADMIN). */
    @GetMapping("/audit")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public NewsAuditPage audit(@RequestParam(required = false) String action,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "30") int size) {
        return service.auditLog(action, page, size);
    }

    /** Ana sayfa slider — dile gore mevcut sirali liste. */
    @GetMapping("/slider")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public List<NewsListItem> slider(@RequestParam(defaultValue = "tr") String lang) {
        return service.sliderForAdmin(lang);
    }

    /** Slider'i kaydet — uyelik + sirayi TAM degistir. */
    @PutMapping("/slider")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public List<NewsListItem> saveSlider(@RequestBody SaveSliderRequest req,
                                         @AuthenticationPrincipal CurrentUser currentUser) {
        String lang = (req.lang() == null || req.lang().isBlank()) ? "tr" : req.lang().trim();
        return service.saveSlider(lang, req.ids(), currentUser.id());
    }

    /** Hizli bayrak degisimi (one cikan / son dakika / slider). */
    @PatchMapping("/{id}/flags")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public NewsDetail flags(@PathVariable Long id,
                            @RequestBody UpdateFlagsRequest req,
                            @AuthenticationPrincipal CurrentUser currentUser) {
        return service.updateFlags(id, req, currentUser.id());
    }

    /** Yayin takviminden hizli yeniden zamanlama. */
    @PatchMapping("/{id}/schedule")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public NewsDetail reschedule(@PathVariable Long id,
                                 @RequestBody RescheduleRequest req,
                                 @AuthenticationPrincipal CurrentUser currentUser) {
        return service.reschedule(id, req, currentUser.id());
    }

    /** Bu haberin canonical URL'sini IndexNow'a bildir (ADMIN). */
    @PostMapping("/{id}/indexnow")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> indexNow(@PathVariable Long id) {
        NewsDetail detail = service.getForAdmin(id);
        String base = seoProperties.siteUrl();
        if (base != null && base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String path = "en".equalsIgnoreCase(detail.lang()) ? "/news/" : "/haber/";
        String url = base + path + detail.slug();
        indexNowService.submit(List.of(url));
        return Map.of("ok", true, "url", url);
    }

    /** Ceviri servisi durumu — panel "Ceviri olustur" butonunu gizlemek icin. */
    @GetMapping("/translate/status")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public Map<String, Boolean> translateStatus() {
        return Map.of("enabled", translationService.isEnabled());
    }

    /**
     * Baslik/ozet/govdeyi kaynak dilden hedef dile cevir (EDITOR/ADMIN). Kayit
     * OLUSTURMAZ — sadece cevrilmis metni doner; editor bu degerlerle bagli
     * (translationGroupId) yeni bir dil taslagi acar. body HTML tag-korumali.
     */
    @PostMapping("/translate")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public TranslateNewsResult translate(@RequestBody TranslateNewsRequest req) {
        return translationService.translate(req);
    }

    /** Yeni haber olustur (EDITOR/ADMIN). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public NewsDetail create(@Valid @RequestBody CreateNewsRequest req,
                             @AuthenticationPrincipal CurrentUser currentUser) {
        return service.create(req, currentUser.id());
    }

    /** Haberi guncelle (EDITOR/ADMIN). */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public NewsDetail update(@PathVariable Long id,
                             @Valid @RequestBody UpdateNewsRequest req,
                             @AuthenticationPrincipal CurrentUser currentUser) {
        return service.update(id, req, currentUser.id());
    }

    /**
     * Yayinla (EDITOR/ADMIN). Push niyeti opsiyonel query param'larla tasinir:
     * {@code ?sendPush=true&pushTarget=FAVORITES}. sendPush verilmezse push
     * gonderilmez; pushTarget verilmezse FAVORITES varsayilir.
     */
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public NewsDetail publish(@PathVariable Long id,
                              @RequestParam(required = false) Boolean sendPush,
                              @RequestParam(required = false) NewsPushTarget pushTarget,
                              @AuthenticationPrincipal CurrentUser currentUser) {
        return service.publish(id, currentUser.id(), sendPush, pushTarget);
    }

    /** Yayindan kaldir (EDITOR/ADMIN). */
    @PostMapping("/{id}/unpublish")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public NewsDetail unpublish(@PathVariable Long id,
                                @AuthenticationPrincipal CurrentUser currentUser) {
        return service.unpublish(id, currentUser.id());
    }

    /** Sil — yalniz ADMIN (soft-delete). */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable Long id,
                       @AuthenticationPrincipal CurrentUser currentUser) {
        service.softDelete(id, currentUser.id());
    }

    /**
     * Toplu islem (EDITOR/ADMIN). Yayinla/geri cek/arsivle/kategori/spor.
     * DELETE eylemi burada AYRICA ADMIN'e gate edilir (per-id delete gibi).
     * SET_CATEGORY icin category, SET_SPORT icin sport zorunludur.
     */
    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public BulkResult bulk(@RequestBody BulkNewsRequest req,
                           @AuthenticationPrincipal CurrentUser currentUser) {
        if (req == null || req.action() == null) {
            throw ApiException.badRequest("Eylem belirtilmedi.");
        }
        if (req.ids() == null || req.ids().isEmpty()) {
            throw ApiException.badRequest("Secili haber yok.");
        }
        if (req.action() == BulkAction.DELETE && currentUser.role() != Role.ADMIN) {
            throw ApiException.forbidden("Silme yetkisi yok.");
        }
        if (req.action() == BulkAction.SET_CATEGORY && req.category() == null) {
            throw ApiException.badRequest("Kategori secilmedi.");
        }
        if (req.action() == BulkAction.SET_SPORT
                && (req.sport() == null || req.sport().isBlank())) {
            throw ApiException.badRequest("Spor secilmedi.");
        }
        return service.bulk(req.ids(), req.action(), req.category(),
                req.sport(), currentUser.id());
    }

    /**
     * Tum yayindaki haberleri Elasticsearch'e yeniden indexler (EDITOR/ADMIN).
     * On-demand backfill — idempotent (upsert). ES kapaliyken (bean yok) 409
     * benzeri bir uyari doner; index'i bozmaz.
     */
    @PostMapping("/reindex")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public Map<String, Object> reindex() {
        NewsIndexer indexer = newsIndexer.getIfAvailable();
        if (indexer == null) {
            return Map.of("status", "disabled",
                    "message", "Elasticsearch kapali (scorestv.elasticsearch.enabled=false).");
        }
        long n = indexer.reindexAll();
        return Map.of("status", "ok", "indexed", n);
    }

    /**
     * Bir haberin ceviri grubu (EDITOR/ADMIN) — kendisi + dil esleri.
     * Cift-dil yan yana duzenleme ekrani bununla iki makaleyi birden yukler.
     */
    @GetMapping("/{id}/group")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public List<NewsDetail> group(@PathVariable Long id) {
        return service.group(id);
    }

    /**
     * Haber gorseli yukle (EDITOR/ADMIN) — MinIO'ya "articles/{uuid}.{ext}"
     * anahtariyla yazar, {key, url} doner. Editor bu URL'yi govdeye veya kapak
     * anahtarina yerlestirir.
     */
    @PostMapping("/images")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public ImageUploadResult uploadImage(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Dosya bos olamaz.");
        }
        String ct = file.getContentType();
        if (ct == null || !ct.startsWith("image/")) {
            throw ApiException.badRequest("Yalniz resim dosyalari yuklenebilir.");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw ApiException.badRequest("Gorsel 10MB sinirini asamaz.");
        }
        String ext = extensionFor(file.getOriginalFilename(), ct);
        String key = "articles/" + UUID.randomUUID() + (ext != null ? "." + ext : "");
        try {
            String url = storage.upload(key, file.getBytes(), ct);
            return new ImageUploadResult(key, url);
        } catch (Exception e) {
            throw ApiException.badRequest("Gorsel yuklenemedi: " + e.getMessage());
        }
    }

    /**
     * Medya kutuphanesi (EDITOR/ADMIN) — daha once yuklenmis haber gorsellerini
     * ("articles/" onekli) en yeni ustte doner. Kapak secerken ayni gorseli
     * tekrar yuklemeden mevcutlardan secmeye yarar.
     */
    @GetMapping("/media")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public List<MediaItem> listMedia(
            @RequestParam(value = "limit", defaultValue = "120") int limit) {
        int capped = Math.min(Math.max(limit, 1), 300);
        return storage.list("articles/", capped).stream()
                .map(o -> new MediaItem(
                        o.key(),
                        o.url(),
                        o.size(),
                        o.lastModified() != null ? o.lastModified().toString() : null))
                .toList();
    }

    /** Medya kutuphanesi ogesi — MinIO anahtari + herkese acik URL + meta. */
    public record MediaItem(String key, String url, long size, String lastModified)
            implements Serializable {
    }

    /**
     * Bir gorselin hangi haber(ler)de kullanildigi (EDITOR/ADMIN) — kapak ya da
     * govde. Medya kutuphanesinde bir gorsele tiklaninca / silmeden once gosterilir.
     */
    @GetMapping("/media/usage")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    public List<MediaUsage> mediaUsage(@RequestParam("key") String key) {
        return service.mediaUsage(key);
    }

    /**
     * Medya gorselini MinIO'dan siler (EDITOR/ADMIN). Panel, gorsel bir habere
     * bagliysa kullaniciyi ONCEDEN uyarir; onaydan sonra bu uc cagrilir.
     */
    @DeleteMapping("/media")
    @PreAuthorize("hasAnyRole('EDITOR','ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMedia(@RequestParam("key") String key) {
        service.deleteMedia(key);
    }

    /** Dosya adi/MIME'den guvenli uzanti (jpg/png/webp...). */
    private static String extensionFor(String filename, String contentType) {
        if (filename != null && filename.contains(".")) {
            String ext = filename.substring(filename.lastIndexOf('.') + 1)
                    .toLowerCase().replaceAll("[^a-z0-9]", "");
            if (!ext.isBlank() && ext.length() <= 5) {
                return ext;
            }
        }
        if (contentType != null && contentType.contains("/")) {
            String sub = contentType.substring(contentType.indexOf('/') + 1)
                    .toLowerCase().replaceAll("[^a-z0-9]", "");
            if (sub.equals("jpeg")) {
                return "jpg";
            }
            if (sub.equals("svg+xml")) {
                return "svg";
            }
            if (!sub.isBlank() && sub.length() <= 5) {
                return sub;
            }
        }
        return null;
    }

    /** Gorsel yukleme yaniti — MinIO anahtari + herkese acik URL. */
    public record ImageUploadResult(String key, String url) implements Serializable {
    }
}
