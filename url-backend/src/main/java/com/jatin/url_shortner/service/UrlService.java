package com.jatin.url_shortner.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jatin.url_shortner.entity.Click;
import com.jatin.url_shortner.entity.Url;
import com.jatin.url_shortner.entity.User;
import com.jatin.url_shortner.repository.ClickRepository;
import com.jatin.url_shortner.repository.UrlRepository;
import com.jatin.url_shortner.util.Base62Util;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UrlService {
    
    private final UrlRepository urlRepository;
    private final ClickRepository clickRepository;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Url shortenUrl(String longUrl, User user){
        
        Optional<Url> existing = urlRepository.findByLongUrlAndUser(longUrl, user);
        if(existing.isPresent()){
            return existing.get();
        }

        Url url = new Url();
        url.setLongUrl(longUrl);
        url.setCreatedAt(LocalDateTime.now());
        url.setExpiryDate(LocalDateTime.now().plusDays(30).toLocalDate());
        url.setClickCount(0);
        url.setUser(user);

        urlRepository.save(url);

        String shortCode = Base62Util.toBase62(url.getUrlId());

        url.setShortCode(shortCode);
        return urlRepository.save(url);
    }

    public Optional<Url> getUrl(String shortCode){
        return urlRepository.findByShortCode(shortCode);
    }

    public Optional<Url> getUrlById(Long id) {
        return urlRepository.findById(id);
    }

    public List<Url> getUrlsByUser(User user) {
        return urlRepository.findAllByUser(user);
    }

    public void deleteUrl(Url url) {
        urlRepository.delete(url);
    }

    public void incrementClick(Url url, String ip, String userAgent, String reffereer){
        Click click = new Click();
        String country = resolveCountryFromIp(ip);

        click.setCountry(country);
        click.setDeviceType(parseDeviceType(userAgent));
        click.setIpAddress(ip);
        click.setReferrer(reffereer);
        click.setClickedAt(LocalDateTime.now());
        click.setUrl(url);
        clickRepository.save(click);

        url.setClickCount(url.getClickCount() + 1);
        urlRepository.save(url);
    }

    private String resolveCountryFromIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return "Unknown";
        }

        try {
            String encodedIp = URLEncoder.encode(ip, StandardCharsets.UTF_8);
            String lookupUrl = "http://ip-api.com/json/" + encodedIp + "?fields=status,country";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(lookupUrl))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return "Unknown";
            }

            JsonNode json = objectMapper.readTree(response.body());
            if (!"success".equalsIgnoreCase(json.path("status").asText())) {
                return "Unknown";
            }

            String country = json.path("country").asText();
            return country == null || country.isBlank() ? "Unknown" : country;
        } catch (Exception ex) {
            return "Unknown";
        }
    }

    private String parseDeviceType(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown";
        }

        String ua = userAgent.toLowerCase(Locale.ROOT);

        if (ua.contains("ipad") || ua.contains("tablet")) {
            return "Tablet";
        }

        if (ua.contains("mobi") || ua.contains("android") || ua.contains("iphone")) {
            return "Mobile";
        }

        if (ua.contains("windows") || ua.contains("macintosh") || ua.contains("linux") || ua.contains("x11")) {
            return "Desktop";
        }

        return "Unknown";
    }
}
