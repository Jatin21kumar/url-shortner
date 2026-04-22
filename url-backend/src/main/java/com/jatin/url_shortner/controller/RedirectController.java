package com.jatin.url_shortner.controller;

import java.net.URI;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.jatin.url_shortner.entity.Url;
import com.jatin.url_shortner.service.UrlService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class RedirectController {

    private final UrlService urlService;

    @GetMapping("/{shortcode}")
    public ResponseEntity<?> redirect(@PathVariable String shortcode, HttpServletRequest request) {
        Optional<Url> urlOptional = urlService.getUrl(shortcode);
        if (urlOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Url url = urlOptional.get();
        if (url.getExpiryDate() != null && url.getExpiryDate().isBefore(LocalDate.now())) {
            return ResponseEntity.status(HttpStatus.GONE).build();
        }

        String ip = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");
        String referrer = request.getHeader("Referer");
        urlService.incrementClick(url, ip, userAgent, referrer);

        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url.getLongUrl())).build();
    }
}
