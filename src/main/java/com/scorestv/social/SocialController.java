package com.scorestv.social;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public uç — sağ raydaki tweet akışı. Bellek cache'inden okur, SocialData'ya
 * istek ATMAZ (maliyet job tarafında, ziyaretçiden bağımsız).
 */
@RestController
@RequestMapping("/api/v1/social")
public class SocialController {

    private final SocialTweetsService service;

    public SocialController(SocialTweetsService service) {
        this.service = service;
    }

    @GetMapping("/tweets")
    public List<SocialTweet> tweets() {
        return service.getAll();
    }
}
