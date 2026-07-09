package com.scorestv.news.dto;

import java.io.Serializable;

/**
 * Toplu islem sonucu — kac haber islendi ({@code processed}) ve kac tanesi
 * bulunamayip atlandi ({@code skipped}).
 */
public record BulkResult(int processed, int skipped) implements Serializable {
}
