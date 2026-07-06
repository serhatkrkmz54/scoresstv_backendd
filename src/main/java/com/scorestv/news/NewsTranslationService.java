package com.scorestv.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scorestv.common.ApiException;
import com.scorestv.news.dto.TranslateNewsRequest;
import com.scorestv.news.dto.TranslateNewsResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Haber ceviri servisi — DeepL (ucretsiz Developer plani / api-free) uzerinden
 * TR&lt;-&gt;EN. body HTML oldugu icin {@code tag_handling=html} ile etiketler
 * korunur (baglar, gorseller, gomulu icerik bozulmaz).
 *
 * <p>Saglayici soyutlamasi: yalnizca BU sinif DeepL'e baglidir; controller ve
 * DTO'lar saglayicidan bagimsizdir. Baska bir saglayiciya gecmek icin sadece
 * bu sinif degisir.
 *
 * <p>Yapilandirma (application.yml):
 * <pre>
 * news:
 *   translate:
 *     deepl:
 *       api-key: ${DEEPL_API_KEY:}
 *       base-url: https://api-free.deepl.com   # ucretsiz plan; Pro: https://api.deepl.com
 * </pre>
 * Anahtar tanimli degilse servis "kapali" sayilir; {@link #translate} 400 doner
 * (istemci ceviri butonunu gizleyebilir ya da mesaji gosterir).
 */
@Service
public class NewsTranslationService {

    private static final Logger log = LoggerFactory.getLogger(NewsTranslationService.class);

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public NewsTranslationService(
            @Value("${news.translate.deepl.api-key:}") String apiKey,
            @Value("${news.translate.deepl.base-url:https://api-free.deepl.com}") String baseUrl) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = (baseUrl == null || baseUrl.isBlank())
                ? "https://api-free.deepl.com" : baseUrl.trim();
    }

    /** Ceviri kullanilabilir mi (anahtar tanimli mi)? */
    public boolean isEnabled() {
        return !apiKey.isBlank();
    }

    /**
     * Baslik + ozet (duz metin) ve govde (HTML, tag-korumali) ceviri. Bos alan
     * aynen doner. sourceLang null ise DeepL otomatik algilar.
     */
    public TranslateNewsResult translate(TranslateNewsRequest req) {
        if (!isEnabled()) {
            throw ApiException.badRequest(
                    "Ceviri servisi yapilandirilmamis (DeepL API anahtari eksik).");
        }
        String src = deeplSource(req.sourceLang());
        String tgt = deeplTarget(req.targetLang());
        if (tgt == null) {
            throw ApiException.badRequest("Hedef dil belirtilmeli (tr veya en).");
        }
        String title = translateOne(req.title(), src, tgt, false);
        String summary = translateOne(req.summary(), src, tgt, false);
        String body = translateOne(req.body(), src, tgt, true);
        return new TranslateNewsResult(title, summary, body);
    }

    /** Tek metin cevir. Bos ise aynen doner. html=true → tag_handling=html. */
    private String translateOne(String text, String src, String tgt, boolean html) {
        if (text == null || text.isBlank()) {
            return text;
        }
        try {
            StringBuilder form = new StringBuilder();
            form.append("text=").append(enc(text));
            form.append("&target_lang=").append(tgt);
            if (src != null) {
                form.append("&source_lang=").append(src);
            }
            if (html) {
                form.append("&tag_handling=html");
            }
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v2/translate"))
                    .header("Authorization", "DeepL-Auth-Key " + apiKey)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(25))
                    .POST(HttpRequest.BodyPublishers.ofString(
                            form.toString(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> res = client.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int code = res.statusCode();
            if (code / 100 != 2) {
                log.warn("DeepL ceviri hatasi: status={} body={}", code, abbreviate(res.body()));
                if (code == 456) {
                    throw ApiException.upstream(
                            "Ceviri kotasi doldu (DeepL aylik limit). Daha sonra deneyin.");
                }
                if (code == 401 || code == 403) {
                    throw ApiException.badRequest("DeepL API anahtari gecersiz.");
                }
                throw ApiException.upstream("Ceviri servisi hatasi (" + code + ").");
            }
            JsonNode arr = mapper.readTree(res.body()).path("translations");
            if (arr.isArray() && !arr.isEmpty()) {
                return arr.get(0).path("text").asText(text);
            }
            return text;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("DeepL ceviri istisnasi", e);
            throw ApiException.upstream("Ceviri yapilamadi: " + e.getMessage());
        }
    }

    /** Kaynak dil kodu — DeepL source_lang (bolgesel varyant YOK: EN, TR). */
    private static String deeplSource(String lang) {
        if (lang == null || lang.isBlank()) {
            return null; // otomatik algila
        }
        return switch (lang.trim().toLowerCase()) {
            case "tr" -> "TR";
            case "en" -> "EN";
            default -> lang.trim().toUpperCase();
        };
    }

    /** Hedef dil kodu — DeepL target_lang (EN icin bolgesel: EN-US). */
    private static String deeplTarget(String lang) {
        if (lang == null || lang.isBlank()) {
            return null;
        }
        return switch (lang.trim().toLowerCase()) {
            case "tr" -> "TR";
            case "en" -> "EN-US";
            default -> lang.trim().toUpperCase();
        };
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 300 ? s.substring(0, 300) : s;
    }
}
