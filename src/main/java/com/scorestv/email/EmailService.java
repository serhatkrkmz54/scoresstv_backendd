package com.scorestv.email;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * E-posta gonderimi. SMTP ayarlari application.yml'daki spring.mail.* ile
 * yapilir; saglayici degistiginde (Gmail -> baska SMTP) sadece o ayarlar
 * degisir, bu sinif aynen calismaya devam eder.
 *
 * Sifre sifirlama e-postasi HTML'dir; sablon resources/email/ altinda tutulur
 * (tasarimi koda dokunmadan duzenlenebilir).
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final String RESET_TEMPLATE_PATH = "email/password-reset.html";

    private final JavaMailSender mailSender;
    private final String from;
    private final String resetTemplate;

    public EmailService(JavaMailSender mailSender,
                        @Value("${spring.mail.username:}") String from) {
        this.mailSender = mailSender;
        this.from = from;
        this.resetTemplate = loadTemplate(RESET_TEMPLATE_PATH);
    }

    /** Sifre sifirlama e-postasini arka planda (async) gonderir. */
    @Async
    public void sendPasswordResetEmail(String to, String resetLink) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            if (!from.isBlank()) {
                helper.setFrom(from, "scorestv");
            }
            helper.setTo(to);
            helper.setSubject("scorestv - Şifre Sıfırlama");

            String html = resetTemplate.replace("{{resetLink}}", resetLink);
            String plainText = """
                    Şifreni sıfırlamak için aşağıdaki bağlantıyı aç:
                    %s

                    Bu bağlantı 1 saat geçerlidir. Bu isteği sen yapmadıysan
                    bu e-postayı yok sayabilirsin.

                    scorestv
                    """.formatted(resetLink);
            // Hem duz metin hem HTML gonderilir; HTML desteklemeyen istemci
            // duz metni gosterir.
            helper.setText(plainText, html);

            mailSender.send(message);
            log.info("Sifre sifirlama e-postasi gonderildi: {}", to);
        } catch (Exception e) {
            log.error("Sifre sifirlama e-postasi gonderilemedi ({}): {}", to, e.getMessage());
        }
    }

    private String loadTemplate(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("E-posta sablonu yuklenemedi: " + path, e);
        }
    }
}
