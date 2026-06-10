package com.scorestv.comments.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Yorum olusturma istegi. {@code parentId} dolu ise yanit (reply); null ise
 * top-level yorum.
 */
public record CommentCreateRequest(
        @NotBlank(message = "Yorum bos olamaz.")
        @Size(max = 2000, message = "Yorum 2000 karakteri asamaz.")
        String content,
        Long parentId
) {}
