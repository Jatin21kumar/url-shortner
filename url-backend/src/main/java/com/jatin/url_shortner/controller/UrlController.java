package com.jatin.url_shortner.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jatin.url_shortner.dto.ShortenRequest;
import com.jatin.url_shortner.dto.ShortenResponse;
import com.jatin.url_shortner.dto.UrlResponse;
import com.jatin.url_shortner.entity.Url;
import com.jatin.url_shortner.entity.User;
import com.jatin.url_shortner.service.UrlService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;


@RestController
@RequestMapping("/url")
@RequiredArgsConstructor
public class UrlController {
    
    private final UrlService urlService;

    @Value("${app.base-url}")
    private String baseUrl;

    @GetMapping("/my")
    public ResponseEntity<List<UrlResponse>> getMyUrls(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<Url> urls = urlService.getUrlsByUser(user);
        List<UrlResponse> responses = urls.stream()
                .map(this::mapToUrlResponse)
                .toList();
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/shorten")
    public ResponseEntity<ShortenResponse> shortenUrl(
            @Valid @RequestBody ShortenRequest shortenRequest,
            @AuthenticationPrincipal User user) {
        Url url = urlService.shortenUrl(shortenRequest.getLongUrl(), user);
        String shortUrl = baseUrl + "/" + url.getShortCode();

        ShortenResponse response = ShortenResponse.builder()
                .id(url.getUrlId())
                .originalUrl(url.getLongUrl())
                .shortUrl(shortUrl)
                .shortCode(url.getShortCode())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUrl(@PathVariable Long id, @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Optional<Url> urlOptional = urlService.getUrlById(id);
        if (urlOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Url url = urlOptional.get();
        if (url.getUser() == null || !url.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        urlService.deleteUrl(url);
        return ResponseEntity.noContent().build();
    }

    private UrlResponse mapToUrlResponse(Url url) {
        String shortUrl = baseUrl + "/" + url.getShortCode();
        
        return UrlResponse.builder()
                .id(url.getUrlId())
                .originalUrl(url.getLongUrl())
                .shortUrl(shortUrl)
                .shortCode(url.getShortCode())
                .clickCount(url.getClickCount())
                .build();
    }
}
