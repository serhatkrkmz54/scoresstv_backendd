package com.scorestv.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * application.yml > scorestv.elasticsearch.* ayarlari.
 *
 * <p><b>Tasarim kararlari:</b>
 * <ul>
 *   <li>Tum baglanti detaylari env-driven — ayri sunucuya tasinirken sadece
 *       <code>ELASTICSEARCH_HOST</code>/<code>_PORT</code>/<code>_PASSWORD</code>
 *       env'lerini set etmek yeterli, kod hicbir yerde host hardcoded degil.</li>
 *   <li><code>enabled=false</code> ise <code>SearchService</code> no-op olarak
 *       calisir; backend baglantisiz da ayaga kalkar (kuruluş asamasinda kolaylik).</li>
 *   <li><code>indexPrefix</code> sayesinde ayni ES cluster'ini baska bir proje
 *       icin tekrar kullanmak istenirse index isim catismasi olmaz
 *       (ornek: <code>scorestv_teams</code>, <code>scorestv_fixtures</code> ...).</li>
 * </ul>
 *
 * <p><b>Index isimleri</b> (prefix uygulandiktan sonra):
 * <pre>
 *   {prefix}_teams       — takim adi, slug, ulke, lig
 *   {prefix}_leagues     — lig adi, slug, ulke, tip
 *   {prefix}_fixtures    — ev/dep takim, lig, tarih, status
 *   {prefix}_players     — oyuncu adi, slug, takim, milliyet
 *   {prefix}_countries   — ulke adi, kod, slug
 * </pre>
 */
@ConfigurationProperties(prefix = "scorestv.elasticsearch")
public record ElasticsearchProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("localhost") String host,
        @DefaultValue("9200") int port,
        @DefaultValue("http") String scheme,
        @DefaultValue("") String username,
        @DefaultValue("") String password,
        @DefaultValue("scorestv") String indexPrefix,
        @DefaultValue("3000") int connectTimeout,
        @DefaultValue("10000") int socketTimeout
) {

    /** Tam URL — RestClientBuilder beslemek icin. */
    public String url() {
        return scheme + "://" + host + ":" + port;
    }

    /** "scorestv" + "_" + name → "scorestv_teams". */
    public String indexFor(String name) {
        return indexPrefix + "_" + name;
    }

    /** Basic-auth gerekli mi? (username + password ikisi de set ise) */
    public boolean hasCredentials() {
        return username != null && !username.isBlank()
                && password != null && !password.isBlank();
    }
}
