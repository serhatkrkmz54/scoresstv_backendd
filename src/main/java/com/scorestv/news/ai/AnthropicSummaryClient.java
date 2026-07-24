package com.scorestv.news.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Anthropic Messages API istemcisi (ham HTTP, projedeki RestClient kalıbıyla —
 * ekstra ağır SDK bağımlılığı yok). Verilen sistem+kullanıcı metninden özet
 * üretir. Hata/kota/refusal durumunda {@code null} döner.
 */
@Component
public class AnthropicSummaryClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicSummaryClient.class);

    private final RestClient http;
    private final NewsAiProperties props;
    private final boolean keyConfigured;

    public AnthropicSummaryClient(NewsAiProperties props) {
        this.props = props;
        this.keyConfigured = props.apiKey() != null && !props.apiKey().isBlank();

        var rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(8));
        rf.setReadTimeout(Duration.ofSeconds(40));   // LLM cevabı biraz sürebilir
        var b = RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(rf)
                .defaultHeader("anthropic-version", props.anthropicVersion())
                .defaultHeader("content-type", "application/json");
        if (keyConfigured) {
            b.defaultHeader("x-api-key", props.apiKey());
        } else {
            log.warn("Anthropic apiKey tanımlı değil — AI özet çağrıları boş döner.");
        }
        this.http = b.build();
    }

    /**
     * {@code POST /v1/messages} — özet üretir. system + tek user mesajı.
     * Hata/refusal/boş → null.
     */
    public String summarize(String systemPrompt, String userText, int maxTokens) {
        if (!keyConfigured) {
            return null;
        }
        Map<String, Object> body = Map.of(
                "model", props.model(),
                "max_tokens", maxTokens,
                "system", systemPrompt,
                "messages", List.of(Map.of("role", "user", "content", userText)));
        try {
            Response resp = http.post()
                    .uri("/v1/messages")
                    .body(body)
                    .retrieve()
                    .body(Response.class);
            if (resp == null || resp.content() == null || resp.content().isEmpty()) {
                return null;
            }
            if ("refusal".equals(resp.stopReason())) {
                log.warn("Anthropic özet reddedildi (refusal).");
                return null;
            }
            String text = resp.content().stream()
                    .filter(c -> "text".equals(c.type()) && c.text() != null)
                    .map(Block::text)
                    .collect(Collectors.joining("\n"))
                    .trim();
            return text.isBlank() ? null : text;
        } catch (Exception e) {
            log.warn("Anthropic özet hatası: {}", e.toString());
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(List<Block> content, @JsonProperty("stop_reason") String stopReason) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Block(String type, String text) {}
}
