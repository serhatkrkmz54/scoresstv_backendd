package com.scorestv.config;

import com.scorestv.security.JwtAuthenticationFilter;
import com.scorestv.security.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ScorestvProperties properties;

    public SecurityConfig(JwtService jwtService, ScorestvProperties properties) {
        this.jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtService);
        this.properties = properties;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/google",
                        "/api/v1/auth/apple",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/logout",
                        "/api/v1/auth/forgot-password",
                        "/api/v1/auth/reset-password",
                        "/api/v1/auth/forgot-password-code",
                        "/api/v1/auth/reset-password-code").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // Diğer TÜM actuator uçları (metrics, prometheus, loggers, env,
                // heapdump, threaddump...) yalnız ADMIN. Aksi halde herhangi bir
                // giriş yapmış USER heapdump/env çekip secret sızdırabilir.
                // NOT: Prometheus scraper varsa admin kimliğiyle veya ağ düzeyinde
                // erişmeli; gerekirse /actuator/prometheus için ayrı permitAll eklenir.
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/v1/fixtures", "/api/v1/fixtures/**").permitAll()
                // Fikstür lookup ucu (sıradaki/önceki maç) — public.
                .requestMatchers("/api/v1/football/fixtures/**").permitAll()
                // Canlı Maç Programı / TV rehberi hub'ı — public (SEO).
                .requestMatchers("/api/v1/football/tv-guide", "/api/v1/football/tv-guide/**").permitAll()
                // Basketbol maclari — public (fikstur + canli skor).
                .requestMatchers("/api/v1/basketball/**").permitAll()
                // Voleybol maclari — public (fikstur + canli skor + detay).
                .requestMatchers("/api/v1/volleyball", "/api/v1/volleyball/**").permitAll()
                // Lig detay sayfasi — public.
                .requestMatchers("/api/v1/leagues", "/api/v1/leagues/**").permitAll()
                .requestMatchers("/api/v1/countries", "/api/v1/countries/**").permitAll()
                // Takim detay sayfasi — public.
                .requestMatchers("/api/v1/teams", "/api/v1/teams/**").permitAll()
                // Oyuncu detay sayfasi — public.
                .requestMatchers("/api/v1/players", "/api/v1/players/**").permitAll()
                // Standings hub — public (puan durumu sayfasi picker).
                .requestMatchers("/api/v1/standings", "/api/v1/standings/**").permitAll()
                // FIFA + UEFA siralamalari — public.
                .requestMatchers("/api/v1/rankings", "/api/v1/rankings/**").permitAll()
                // Mobile-ozel public endpointler (tum public).
                .requestMatchers("/api/v1/mobile", "/api/v1/mobile/**").permitAll()
                // Arama (Elasticsearch) — public. Admin reindex endpoint'i
                // /api/v1/admin/search/** zaten anyRequest().authenticated() altinda
                // ve controller seviyesinde @PreAuthorize("hasRole('ADMIN')") ile korunur.
                .requestMatchers("/api/v1/search", "/api/v1/search/**").permitAll()
                // Yorum listesi — public okuma (POST/DELETE auth gerekli;
                // default authenticated() onlari koruyor).
                .requestMatchers(HttpMethod.GET, "/api/v1/comments/**").permitAll()
                // Maç highlight/özet — public okuma (Highlightly proxy).
                .requestMatchers(HttpMethod.GET, "/api/v1/highlights/**").permitAll()
                // Maç TV yayını — public okuma (TheSportsDB proxy).
                .requestMatchers(HttpMethod.GET, "/api/v1/broadcasts/**").permitAll()
                // Maç sonucu tahmin oylaması — anonim okuma + oy (voterId).
                .requestMatchers("/api/v1/predictions/**").permitAll()

                // Oyun (Scores Coin): GET'ler herkese acik (aktif yarisma, siralama,
                // yarisma detayi). Tahmin (POST) + cuzdan (/wallet GET) auth ister
                // (asagidaki anyRequest().authenticated()). Admin uclari /admin/game
                // altinda + @PreAuthorize("hasRole('ADMIN')").
                .requestMatchers(org.springframework.http.HttpMethod.GET,
                        "/api/v1/game/active",
                        "/api/v1/game/leaderboard",
                        "/api/v1/game/competitions",
                        "/api/v1/game/competitions/**").permitAll()
                // AI Analiz isabet karnesi — public okuma.
                .requestMatchers(org.springframework.http.HttpMethod.GET,
                        "/api/v1/ai/performance").permitAll()
                // Haberler — public okuma (yalniz PUBLISHED). Yonetim
                // /api/v1/admin/news/** ise anyRequest().authenticated() +
                // controller seviyesinde @PreAuthorize ile korunur (EDITOR/ADMIN).
                .requestMatchers(HttpMethod.GET, "/api/v1/news", "/api/v1/news/**").permitAll()
                // Iletisim formu — public POST. Admin listeleme
                // /api/v1/admin/contact ise anyRequest().authenticated() +
                // @PreAuthorize("hasRole('ADMIN')") ile korunur.
                .requestMatchers(HttpMethod.POST, "/api/v1/contact", "/api/v1/contact/report").permitAll()
                // Sitemap listeleme uclari — public (SEO).
                .requestMatchers("/api/v1/sitemap", "/api/v1/sitemap/**").permitAll()
                // Sosyal medya (X/Twitter) tweet akisi — public okuma.
                .requestMatchers(HttpMethod.GET, "/api/v1/social", "/api/v1/social/**").permitAll()
                // Admin SPA — statik dosyalar + SPA route'lari herkese acik
                // (auth UI tarafinda yapilir, API endpointleri zaten JWT korumali).
                .requestMatchers("/admin", "/admin/**").permitAll()
                // STOMP el sıkışması ve WebSocket çerçeveleri (canlı skor) — herkese açık.
                .requestMatchers("/ws/**").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) ->
                        writeError(res, HttpStatus.UNAUTHORIZED, "Kimlik doğrulama gerekli"))
                .accessDeniedHandler((req, res, e) ->
                        writeError(res, HttpStatus.FORBIDDEN, "Bu işlem için yetkiniz yok")))
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Filtre zincirinde (controller'a ulasmadan) olusan 401/403 durumlari icin
     * basit JSON hata govdesi yazar. Mesajlar sabit/kontrollu metinlerdir.
     */
    private void writeError(HttpServletResponse res, HttpStatus status, String message)
            throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write(
                "{\"timestamp\":\"" + Instant.now() + "\","
                        + "\"status\":" + status.value() + ","
                        + "\"message\":\"" + message + "\"}");
    }
}
