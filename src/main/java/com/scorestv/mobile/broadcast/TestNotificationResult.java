package com.scorestv.mobile.broadcast;

/**
 * Test bildirimi sonucu — panele geri doner.
 *
 * @param email       hedeflenen hesap
 * @param deviceCount hesaba bagli, bildirimi acik cihaz sayisi
 * @param sent        FCM'e basariyla iletilen sayi (0 ise FCM kapali olabilir)
 * @param fcmEnabled  sunucuda FCM aktif mi
 */
public record TestNotificationResult(String email, int deviceCount, int sent,
                                     boolean fcmEnabled) {
}
