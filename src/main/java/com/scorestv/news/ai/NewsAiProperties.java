package com.scorestv.news.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * AI haber özeti (isteğe bağlı) yapılandırması. Editör/admin bir DRAFT haberin
 * KAYNAK linkini çekip Claude ile özgün bir özet üretebilir — otomatik DEĞİL,
 * yalnız elle tetiklenir (bkz. {@code NewsAiService}).
 *
 * <p>Kapalı (varsayılan). Açmak için: {@code NEWS_AI_ENABLED=true} +
 * {@code ANTHROPIC_API_KEY=...}. Model: ucuz/hızlı {@code claude-haiku-4-5}.
 */
@ConfigurationProperties(prefix = "scorestv.news-ai")
public record NewsAiProperties(
        @DefaultValue("false") boolean enabled,
        /** Anthropic API anahtarı (yalnız sunucuda, .env). */
        String apiKey,
        @DefaultValue("https://api.anthropic.com") String baseUrl,
        @DefaultValue("2023-06-01") String anthropicVersion,
        /** Özet modeli — özet için ucuz/hızlı Haiku ideal. */
        @DefaultValue("claude-haiku-4-5") String model,
        /** LLM'e gönderilecek makale metni üst sınırı (token/maliyet kontrolü). */
        @DefaultValue("12000") int maxInputChars,
        /** Ayıklanan metin bu kadar karakterin altındaysa "çıkarılamadı" say. */
        @DefaultValue("600") int minExtractedChars
) {}
