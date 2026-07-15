package com.scorestv.user;

/**
 * Yeni bir kullanıcı HESABI oluşturulduğunda yayınlanır (e-posta kaydı, Google,
 * Apple — yalnız YENİ kayıt; mevcut hesabı sosyal bağlama DEĞİL). Dinleyiciler
 * {@code @TransactionalEventListener(AFTER_COMMIT)} ile, kayıt commit olduktan
 * sonra çalışır — böylece bonus/karşılama gibi yan işler kaydı bloklamaz.
 */
public record UserRegisteredEvent(Long userId) {}
