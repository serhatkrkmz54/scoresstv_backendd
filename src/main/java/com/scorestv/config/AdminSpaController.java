package com.scorestv.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Admin SPA forwarding controller.
 *
 * <p>React Router client-side route'lar (orn. {@code /admin/broadcasts/channels})
 * Spring tarafindan bilinmez. Bu controller {@code /admin/**} yollarini
 * {@code /admin/index.html}'e forward eder, oradan React Router devralir.
 *
 * <p>Statik dosyalar ({@code .js}, {@code .css}, asset'ler)
 * {@code src/main/resources/static/admin/} dizininden Spring'in default
 * static resource handler'i tarafindan sunulur — controller'a hic ugramaz.
 */
@Controller
public class AdminSpaController {

    /**
     * /admin yolu icin SPA giris noktasi.
     */
    @GetMapping("/admin")
    public String adminRoot() {
        return "forward:/admin/index.html";
    }

    /**
     * /admin/{path}/{path}/... seklindeki tum derinlikteki route'lar.
     * Pattern {@code [^.]*} ile dot iceren request'leri (asset dosyalari)
     * dislariz — onlari static resource handler servisi alir.
     */
    @GetMapping("/admin/{path:[^.]*}/**")
    public String adminSubpath() {
        return "forward:/admin/index.html";
    }
}
