package com.scorestv.football.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Otomatik çeviri cache'ine erişim.
 *
 * <p>Okuma tek-satır (unique üçlü) ile; yazma yarış-güvenli ON CONFLICT DO
 * NOTHING ile yapılır (aynı metni iki istek aynı anda çözerse ikinci INSERT
 * sessizce yutulur, batch abort olmaz).
 */
public interface TranslationCacheRepository extends JpaRepository<TranslationCache, Long> {

    Optional<TranslationCache> findByCategoryAndTargetLangAndSourceText(
            String category, String targetLang, String sourceText);

    /**
     * Yarış-güvenli ekleme. Aynı (category, source_text, target_lang) zaten
     * varsa hiçbir şey yapmaz — çakışma 23505 fırlatmaz.
     */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO translation_cache "
            + "(category, source_text, target_lang, translated, provider, created_at) "
            + "VALUES (:category, :source, :lang, :translated, :provider, now()) "
            + "ON CONFLICT (category, source_text, target_lang) DO NOTHING",
            nativeQuery = true)
    void insertIfAbsent(@Param("category") String category,
                        @Param("source") String source,
                        @Param("lang") String lang,
                        @Param("translated") String translated,
                        @Param("provider") String provider);
}
