package com.scorestv.comments;

import com.scorestv.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Bir maca yazilmis kullanici yorumu. Soft-delete (deleted=true) ile silinir
 * — kayit kalir; cevap niteliklerinde silinen yorumlar gizlenir.
 */
@Entity
@Table(name = "fixture_comments")
@Getter
@Setter
@NoArgsConstructor
public class FixtureComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Maç id'si — futbolda Fixture id, basketbolda BasketballGame id. Artık
     * doğrudan Fixture'a FK DEĞİL (sport ile birlikte hangi maça ait olduğu
     * belirlenir). Kolon adı geriye uyum için "fixture_id" kaldı.
     */
    @Column(name = "fixture_id", nullable = false)
    private Long matchId;

    /** Hangi spor: "FOOTBALL" veya "BASKETBALL". */
    @Column(name = "sport", nullable = false, length = 20)
    private String sport;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Üst yorum — null ise top-level (feed'de gosterilir), dolu ise yanit
     * (parent'in altinda nested gosterilir).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private FixtureComment parent;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private boolean deleted = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
