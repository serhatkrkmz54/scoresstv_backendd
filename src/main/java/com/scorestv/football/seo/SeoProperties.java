package com.scorestv.football.seo;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * application.yml içindeki "scorestv.seo.*" ayarları.
 */
@ConfigurationProperties(prefix = "scorestv.seo")
public record SeoProperties(

        /** Public frontend temel adresi — canonical URL'ler bununla kurulur. */
        @DefaultValue("http://localhost:3000") String siteUrl,

        /** SEO tarih gösteriminin yapılacağı saat dilimi. */
        @DefaultValue("Europe/Istanbul") String timezone
) {
}
