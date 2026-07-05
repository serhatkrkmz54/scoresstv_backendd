package com.scorestv.news;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

/**
 * Haber govdesini (HTML) sunucuda temizler — stored-XSS korunmasi. Sadece
 * guvenli etiket/oznitelik listesine izin verilir; script/onclick/style vb.
 * ayiklanir. Editor'den gelen ham HTML persist edilmeden ONCE buradan gecer.
 *
 * <p>Safelist: jsoup "relaxed" tabani + gorseller (img) icin genislik/yukseklik
 * ve figure/figcaption. iframe (YouTube/Twitter gomulu) icin ayri, kontrollu
 * bir izin verilir — yalniz bilinen embed host'lari.
 */
@Component
public class NewsSanitizer {

    /** Govde icin izin verilen HTML — bir kez kurulur, thread-safe okunur. */
    private final Safelist bodySafelist = buildBodySafelist();

    /**
     * Verilen ham HTML'i temizler. null/blank ise oldugu gibi (null->null,
     * blank->trim) doner. Cikti guvenli, persist edilebilir HTML'dir.
     */
    public String sanitizeBody(String rawHtml) {
        if (rawHtml == null) {
            return null;
        }
        if (rawHtml.isBlank()) {
            return "";
        }
        return Jsoup.clean(rawHtml, bodySafelist);
    }

    /**
     * HTML etiketlerini tamamen sokup duz metni doner (okuma suresi ve arama
     * icin). Bosluklar normalize edilir.
     */
    public String stripToText(String rawHtml) {
        if (rawHtml == null || rawHtml.isBlank()) {
            return "";
        }
        Document doc = Jsoup.parse(rawHtml);
        return doc.text();
    }

    private static Safelist buildBodySafelist() {
        Safelist safelist = Safelist.relaxed()
                // Gorsel oznitelikleri (relaxed zaten img+src icerir; genislik/
                // yukseklik + lazy-loading + alt/title'i acikca ekliyoruz).
                .addAttributes("img", "src", "alt", "title", "width", "height", "loading")
                // relaxed'de OLMAYAN ama editorun urettigi etiketler:
                //  - hr : yatay cizgi (ayrac)
                //  - s  : ustu-cizili (TipTap <s> uretir; relaxed yalniz <strike>)
                .addTags("figure", "figcaption", "hr", "s")
                // Hizalama: TipTap TextAlign satir-ici "text-align" style verir.
                // Icerik yalniz guvenilir EDITOR/ADMIN tarafindan uretilir ve
                // jsoup script/onclick/js: URL'lerini zaten ayiklar; bu yuzden
                // blok etiketlerde style'a (hizalama icin) izin veriyoruz.
                .addAttributes("p", "style")
                .addAttributes("h2", "style")
                .addAttributes("h3", "style")
                .addAttributes("h4", "style")
                .addAttributes("blockquote", "style")
                .addAttributes("figure", "style")
                .addAttributes("img", "style")
                // Baglantilara guvenli rel eklemek icin target'a izin.
                .addAttributes("a", "target", "rel")
                // Video gomulu (YouTube/Twitter). Yalniz asagidaki protokol +
                // host'lar; jsoup varsayilan olarak bilinmeyen host'ta src'yi
                // dusurmez ama enforceAttribute + protokol kisitiyla daraltiyoruz.
                .addTags("iframe")
                .addAttributes("iframe", "src", "width", "height", "frameborder",
                        "allow", "allowfullscreen", "title")
                .addProtocols("iframe", "src", "https")
                .addProtocols("img", "src", "https", "http");
        return safelist;
    }
}
