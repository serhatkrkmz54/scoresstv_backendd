package com.scorestv.comments.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * Mobile/web'e gonderilen yorum yapisı.
 *
 * @param likedByMe geçerli istegin sahibi (giris yapmis kullanici) bu yorumu
 *                  begenmis mi — anonymous istek icin daima false.
 * @param parentId  null ise top-level yorum; dolu ise baska yorumun yaniti.
 * @param replies   bu yorumun yanitlari (yalniz top-level yorumlarda dolu;
 *                  reply'larin replies'i boş).
 */
public record CommentView(
        Long id,
        String content,
        UserRef user,
        Instant createdAt,
        long likeCount,
        boolean likedByMe,
        /** geçerli kullanici bu yorumun sahibi mi — silme butonu icin */
        boolean isMine,
        Long parentId,
        List<CommentView> replies
) implements Serializable {

    public record UserRef(
            Long id,
            String displayName,
            String photo,
            String country
    ) implements Serializable {}
}
