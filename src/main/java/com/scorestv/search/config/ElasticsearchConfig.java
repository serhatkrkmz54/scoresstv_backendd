package com.scorestv.search.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

import java.time.Duration;

/**
 * Elasticsearch baglantisi + Spring Data ES repository taramasi.
 *
 * <p><b>scorestv.elasticsearch.enabled=false</b> ise bu config butun bean'lerle
 * birlikte hic yuklenmez — uygulama ES'siz de calismaya devam eder
 * (SearchService bos sonuc doner).
 *
 * <p><b>Repository tarama sinirlamasi:</b> JPA ve ES repository'leri ayni
 * Spring Data Repository arayuzunden turedigi icin tarama paketleri
 * birbirinden ayrilmali. ES sadece <code>com.scorestv.search.index</code>
 * paketini, JPA ise diger her yeri (auto-config) tarar.
 *
 * <p><b>Basic-auth:</b> prod'da elastic kullanicisi + sifre verilirse otomatik
 * gelir; local'de bos birakilir (security KAPALI).
 */
@Configuration
@ConditionalOnProperty(prefix = "scorestv.elasticsearch", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@EnableElasticsearchRepositories(basePackages = "com.scorestv.search.index")
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ElasticsearchConfig.class);

    private final ElasticsearchProperties props;

    public ElasticsearchConfig(ElasticsearchProperties props) {
        this.props = props;
        log.info("Elasticsearch yapilandirmasi: url={} indexPrefix={} basicAuth={}",
                props.url(), props.indexPrefix(), props.hasCredentials());
    }

    @Override
    public ClientConfiguration clientConfiguration() {
        // Spring Data ES builder fluent zincirinde tip degisir:
        //   connectedTo() → MaybeSecureClientConfigurationBuilder (usingSsl/withBasicAuth burada)
        //   withConnectTimeout() / withSocketTimeout() → TerminalClientConfigurationBuilder
        // Bu yuzden once SSL+auth, sonra timeout'lar.
        var secure = ClientConfiguration.builder()
                .connectedTo(props.host() + ":" + props.port());

        if ("https".equalsIgnoreCase(props.scheme())) {
            secure.usingSsl();
        }
        if (props.hasCredentials()) {
            secure.withBasicAuth(props.username(), props.password());
        }

        return secure
                .withConnectTimeout(Duration.ofMillis(props.connectTimeout()))
                .withSocketTimeout(Duration.ofMillis(props.socketTimeout()))
                .build();
    }
}
