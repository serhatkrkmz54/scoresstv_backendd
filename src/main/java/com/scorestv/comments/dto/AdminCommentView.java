package com.scorestv.comments.dto;

import java.io.Serializable;

/**
 * Panel yorum moderasyonu satiri — bir yorumun ozeti (kim, hangi mac/spor,
 * icerik, silinmis mi, ne zaman). Panel listeler; sil/geri-al aksiyonlari
 * AdminCommentController uzerinden yapilir.
 */
public record AdminCommentView(
        Long id,
        String sport,
        Long matchId,
        String content,
        boolean deleted,
        String createdAt,
        Long userId,
        String userName,
        String country,
        Long parentId
) implements Serializable {
}
