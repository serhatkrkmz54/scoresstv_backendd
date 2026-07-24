package com.scorestv.news.ai;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;

/**
 * Bir haber URL'sinden ana makale metnini ayıklar (readability heuristiği,
 * jsoup ile). Tarayıcı User-Agent'ı ile çeker; en çok paragraf METNİ barındıran
 * kabı (article/main/div) seçer, o kabın paragraflarını döner.
 *
 * <p>Paywall / JS-render / anti-bot / garip layout durumunda {@code null} döner
 * — çağıran (servis) NewsData özetine düşer ya da editörü uyarır. Tam metin
 * SAKLANMAZ; yalnız AI'a özet çıkarttırmak için geçici kullanılır.
 */
@Component
public class ArticleTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(ArticleTextExtractor.class);
    private static final int MIN_PARAGRAPH = 40;   // bundan kısa <p>'ler menü/etiket sayılır
    private static final int MAX_HTML_BYTES = 4 * 1024 * 1024;

    private final RestClient http;

    public ArticleTextExtractor() {
        var rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(8));
        rf.setReadTimeout(Duration.ofSeconds(15));
        this.http = RestClient.builder()
                .requestFactory(rf)
                .defaultHeader("User-Agent",
                        "Mozilla/5.0 (compatible; ScorestvBot/1.0; +https://scorestv.com)")
                .defaultHeader("Accept", "text/html,application/xhtml+xml")
                .build();
    }

    /** Ana makale metni (paragraflar \n\n ile ayrık); çıkarılamazsa null. */
    public String extract(String url) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            return null;
        }
        final String html;
        try {
            html = http.get().uri(URI.create(url)).retrieve().body(String.class);
        } catch (Exception e) {
            log.debug("Makale çekilemedi ({}): {}", url, e.toString());
            return null;
        }
        if (html == null || html.isBlank() || html.length() > MAX_HTML_BYTES) {
            return null;
        }
        try {
            Document doc = Jsoup.parse(html, url);
            // Gövde dışı gürültüyü at.
            doc.select("script,style,noscript,nav,header,footer,aside,form,figure,"
                    + "figcaption,iframe,button,svg,[role=navigation],[class*=comment],"
                    + "[class*=share],[class*=related],[class*=ad-],[id*=comment]").remove();

            // En çok paragraf metni barındıran kabı seç (readability mantığı).
            Element best = null;
            int bestScore = -1;
            for (Element el : doc.select("article, main, [role=main], div, section")) {
                int score = 0;
                for (Element p : el.select("p")) {
                    int len = p.text().length();
                    if (len >= MIN_PARAGRAPH) {
                        score += len;
                    }
                }
                if ("article".equalsIgnoreCase(el.tagName())) {
                    score = (int) (score * 1.3);   // gerçek <article> kabına öncelik
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = el;
                }
            }
            if (best == null) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (Element p : best.select("p")) {
                String t = p.text().trim();
                if (t.length() >= MIN_PARAGRAPH) {
                    sb.append(t).append("\n\n");
                }
            }
            String text = sb.toString().trim();
            return text.isBlank() ? null : text;
        } catch (Exception e) {
            log.debug("Makale ayıklanamadı ({}): {}", url, e.toString());
            return null;
        }
    }
}
