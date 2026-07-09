package com.scorestv.comments.dto;

import java.io.Serializable;
import java.util.List;

/** Panel yorum moderasyonu sayfa yaniti (news liste deseniyle ayni sekil). */
public record AdminCommentPage(
        List<AdminCommentView> items,
        long totalCount,
        boolean hasNext
) implements Serializable {
}
