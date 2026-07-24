package com.scorestv.news.ai;

import com.scorestv.common.ApiException;
import com.scorestv.news.NewsArticle;
import com.scorestv.news.NewsArticleRepository;
import com.scorestv.news.NewsService;
import com.scorestv.news.dto.NewsDetail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI haber özeti — <b>isteğe bağlı, elle tetiklenen</b> zenginleştirme. Editör/
 * admin bir haberin KAYNAK linkini çektirir, ana metni ayıklar, Claude ile
 * özgün bir özet ürettirir ve o habere işler. Otomatik/toplu DEĞİL — yalnız
 * {@code POST /api/v1/admin/news/{id}/ai-summarize} ile bir haber için çalışır.
 */
@Service
public class NewsAiService {

    private final NewsArticleRepository articleRepo;
    private final ArticleTextExtractor extractor;
    private final AnthropicSummaryClient client;
    private final NewsService newsService;
    private final NewsAiProperties props;

    public NewsAiService(NewsArticleRepository articleRepo,
                         ArticleTextExtractor extractor,
                         AnthropicSummaryClient client,
                         NewsService newsService,
                         NewsAiProperties props) {
        this.articleRepo = articleRepo;
        this.extractor = extractor;
        this.client = client;
        this.newsService = newsService;
        this.props = props;
    }

    /**
     * Bir haberin kaynağından AI özeti üretip habere işler; güncel detayı döner.
     * Başarısızlıkta anlaşılır bir hata fırlatır (haber değişmez), editör özeti
     * elle yazabilir.
     */
    @Transactional
    public NewsDetail summarize(Long articleId, Long actorId) {
        if (!props.enabled()) {
            throw ApiException.badRequest("AI özet özelliği kapalı (NEWS_AI_ENABLED).");
        }
        if (props.apiKey() == null || props.apiKey().isBlank()) {
            throw ApiException.badRequest("ANTHROPIC_API_KEY tanımlı değil.");
        }
        NewsArticle a = articleRepo.findByIdAndDeletedAtIsNull(articleId)
                .orElseThrow(() -> ApiException.notFound("Haber bulunamadı: " + articleId));

        String url = a.getSourceUrl();
        if (url == null || url.isBlank()) {
            throw ApiException.badRequest(
                    "Bu haberin kaynak linki yok; AI özet çıkarılamaz. Özeti elle yazabilirsin.");
        }

        String text = extractor.extract(url);
        if (text == null || text.length() < props.minExtractedChars()) {
            throw ApiException.badRequest(
                    "Kaynak makale metni çıkarılamadı (paywall/JS/kısa içerik). "
                            + "Özeti elle yazabilir ya da başka kaynak deneyebilirsin.");
        }
        String capped = text.length() > props.maxInputChars()
                ? text.substring(0, props.maxInputChars()) : text;

        final boolean tr = !"en".equalsIgnoreCase(a.getLang());
        final String system = tr
                ? "Sen bir spor haber editorusun. Sana verilen haber metninden OZGUN kelimelerle, "
                + "Turkce, 3-5 cumlelik dogru bir haber ozeti yaz. KISA VE YALIN CUMLELER kur: her "
                + "cumle TEK bir bilgi versin, kisa olsun; uzun, ic ice, virgullerle uzayan "
                + "cumlelerden kacin. Kaynak metni birebir kopyalama; sadece gercekleri ozetle. "
                + "Yalniz ozeti dondur; baslik, giris cumlesi veya ek aciklama ekleme."
                : "You are a sports news editor. From the given article text, write an ORIGINAL, "
                + "accurate 3-5 sentence news summary in English. Use SHORT, SIMPLE SENTENCES: one "
                + "fact per sentence, kept short; avoid long, nested, comma-heavy sentences. Do not "
                + "copy the source verbatim; summarize only the facts. Return only the summary — no "
                + "title or extra commentary.";

        String summary = client.summarize(system, capped, 400);
        if (summary == null || summary.isBlank()) {
            throw ApiException.badRequest(
                    "AI özet üretilemedi (API hatası/kota). Tekrar dene ya da elle yaz.");
        }
        return newsService.applyAiSummary(articleId, summary, actorId);
    }
}
