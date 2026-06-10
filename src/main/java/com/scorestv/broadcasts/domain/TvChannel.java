package com.scorestv.broadcasts.domain;

import com.scorestv.common.BaseEntity;
import com.scorestv.football.domain.TranslatableName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * TV kanali master kaydi — beIN SPORTS 1, S Sport, TRT Spor vb.
 *
 * <p>Her kanal {@code country_code} ile baglidir; ayni isimli "beIN SPORTS 1"
 * Turkiye'de farkli, Ingiltere'de farkli bir kanaldir. Listelemede
 * {@code sort_order} kullanilir (ana kanal once).
 *
 * <p>{@code TranslatableName} arayuzu ile {@code name_tr} alanini destekler —
 * kanal adi cogunlukla zaten Latin (beIN, S Sport) ama TR ad farkli olabilir
 * (orn. "Türkiye Radyo Televizyon Kurumu Spor" → "TRT Spor").
 */
@Entity
@Table(
        name = "tv_channels",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_tv_channels_name_country",
                columnNames = {"name", "country_code"})
)
@Getter
@Setter
@NoArgsConstructor
public class TvChannel extends BaseEntity implements TranslatableName {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "name_tr", length = 120)
    private String nameTr;

    /** UI dar alanlar icin kisa ad — "BS1", "SS", "TRT". */
    @Column(name = "short_name", length = 20)
    private String shortName;

    @Column(name = "logo_url", columnDefinition = "TEXT")
    private String logoUrl;

    /** "TR" / "GB" / "US" / "DE" / "WORLDWIDE". */
    @Column(name = "country_code", nullable = false, length = 10)
    private String countryCode;

    /** Resmi site — frontend kanal logosuna tiklayinca buraya yonlenir. */
    @Column(name = "streaming_url", columnDefinition = "TEXT")
    private String streamingUrl;

    /** Tabii/EXXEN/DAZN gibi sadece dijital. UI rozetinde gosterilir. */
    @Column(name = "is_streaming_only", nullable = false)
    private boolean streamingOnly = false;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 100;

    @Column(nullable = false)
    private boolean active = true;
}
