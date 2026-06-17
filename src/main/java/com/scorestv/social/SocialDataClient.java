package com.scorestv.social;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * SocialData.tools arama ucundan ({@code /twitter/search?query=from:HANDLE})
 * bir hesabın son tweet'lerini çeker ve {@link SocialTweet}'e sadeleştirir.
 *
 * <p>Arama ucu "from:USERNAME -filter:replies" operatörüyle kullanıcının kendi
 * tweet'lerini döndürür (user-tweets ucu ID ister ve "Limited Access"). Hata,
 * 402 (bakiye yok) veya erişim kısıtı durumunda BOŞ liste döner — akış patlamaz,
 * sadece o tur tweet gelmez.
 */
@Component
public class SocialDataClient {

    private static final Logger log = LoggerFactory.getLogger(SocialDataClient.class);

    private final RestClient http;
    private final ObjectMapper mapper = new ObjectMapper();
    private final SocialDataProperties props;

    public SocialDataClient(SocialDataProperties props) {
        this.props = props;
        var rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(8));
        rf.setReadTimeout(Duration.ofSeconds(15));
        this.http = RestClient.builder().requestFactory(rf).build();
    }

    /** Tek hesabın son tweet'leri (en fazla {@code maxPerAccount}). Hata → boş. */
    public List<SocialTweet> fetchForAccount(String handle) {
        if (props.apiKey() == null || props.apiKey().isBlank()
                || handle == null || handle.isBlank()) {
            return List.of();
        }
        StringBuilder q = new StringBuilder("from:").append(handle);
        if (props.excludeReplies()) q.append(" -filter:replies");
        if (props.excludeRetweets()) q.append(" -filter:retweets");

        String url = props.baseUrl() + "/twitter/search?type=Latest&query="
                + UriUtils.encode(q.toString(), StandardCharsets.UTF_8);

        final String body;
        try {
            body = http.get().uri(url)
                    .header("Authorization", "Bearer " + props.apiKey())
                    .header("Accept", "application/json")
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.warn("SocialData fetch başarısız ({}): {}", handle, e.toString());
            return List.of();
        }
        if (body == null || body.isBlank()) return List.of();

        try {
            JsonNode root = mapper.readTree(body);
            JsonNode tweets = root.path("tweets");
            if (!tweets.isArray()) return List.of();
            List<SocialTweet> out = new ArrayList<>();
            for (JsonNode t : tweets) {
                SocialTweet st = parse(t);
                if (st != null) out.add(st);
                if (out.size() >= props.maxPerAccount()) break;
            }
            return out;
        } catch (Exception e) {
            log.warn("SocialData parse başarısız ({}): {}", handle, e.toString());
            return List.of();
        }
    }

    private SocialTweet parse(JsonNode t) {
        // Retweet ise (query'de filtrelense de) ele.
        if (props.excludeRetweets()
                && t.has("retweeted_status") && t.get("retweeted_status").isObject()) {
            return null;
        }
        String id = text(t, "id_str");
        String full = text(t, "full_text");
        if (id == null || full == null) return null;

        JsonNode u = t.path("user");
        String handle = text(u, "screen_name");
        String name = text(u, "name");
        String avatar = text(u, "profile_image_url_https");
        if (avatar != null) {
            // _normal (48px) yerine _bigger (73px) — sağ ray avatarı için daha net.
            avatar = avatar.replace("_normal", "_bigger");
        }
        boolean verified = u.path("verified").asBoolean(false);
        Instant created = parseInstant(text(t, "tweet_created_at"));
        long replies = t.path("reply_count").asLong(0);
        long retweets = t.path("retweet_count").asLong(0);
        long likes = t.path("favorite_count").asLong(0);
        String url = (handle != null)
                ? "https://x.com/" + handle + "/status/" + id
                : "https://x.com/i/status/" + id;

        return new SocialTweet(id, handle, name, avatar, verified,
                cleanText(full), created, replies, retweets, likes, url);
    }

    /** Sondaki t.co medya/alıntı linkini temizle — sidebar metni sade dursun. */
    private static String cleanText(String s) {
        return s.replaceAll("\\s*https?://t\\.co/\\S+\\s*$", "").trim();
    }

    private static Instant parseInstant(String s) {
        if (s == null) return Instant.EPOCH;
        try {
            return Instant.parse(s);
        } catch (Exception e) {
            return Instant.EPOCH;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }
}
