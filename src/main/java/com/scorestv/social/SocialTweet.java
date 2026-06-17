package com.scorestv.social;

import java.io.Serializable;
import java.time.Instant;

/**
 * Frontend sağ rayda gösterilen tek bir tweet. SocialData yanıtından sadeleştirilir.
 */
public record SocialTweet(
        String id,
        String handle,
        String name,
        String avatar,
        boolean verified,
        String text,
        Instant createdAt,
        long replies,
        long retweets,
        long likes,
        /** Tweet'in X üzerindeki kalıcı linki. */
        String url
) implements Serializable {}
