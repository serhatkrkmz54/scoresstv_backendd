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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Bir yorum-beğeni iliskisi. (comment_id, user_id) unique — ayni kullanici
 * ayni yorumu yalniz bir kez begenir; tekrar = unlike (DELETE).
 */
@Entity
@Table(name = "fixture_comment_likes",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_comment_like_user",
                columnNames = {"comment_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class FixtureCommentLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comment_id", nullable = false)
    private FixtureComment comment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
