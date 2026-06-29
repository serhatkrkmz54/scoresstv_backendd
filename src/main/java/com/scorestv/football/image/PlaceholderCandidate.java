package com.scorestv.football.image;

/**
 * Placeholder kesfi sonucu — birden cok varlikta AYNI cikan bir gorsel hash'i.
 *
 * @param sha256     gorselin SHA-256 hex hash'i (kucuk harf)
 * @param count      ornekleme icinde bu hash'in kac varlikta tekrar ettigi
 * @param exampleUrl bu hash'e sahip bir ornek kaynak URL (manuel dogrulama icin)
 */
public record PlaceholderCandidate(String sha256, int count, String exampleUrl) {
}
