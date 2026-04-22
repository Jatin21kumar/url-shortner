package com.jatin.url_shortner.config;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.data.redis.core.RedisTemplate;
import lombok.RequiredArgsConstructor;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter{
    
    private final RedisTemplate<String, String> redisTemplate;
    
    private static final int MAX_REQUESTS = 10;
    private static final long WINDOW_SIZE = 1; // 1 minute
    private static final TimeUnit WINDOW_UNIT = TimeUnit.MINUTES;
    
    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain) throws ServletException, IOException {

            String ip = request.getRemoteAddr();
            String key = "rate:" + ip;
            
            try {
                String val = redisTemplate.opsForValue().get(key);
                Integer count = val == null ? 0 : Integer.parseInt(val);
                
                if (count == 0) {
                    // First request - set with expiry
                    redisTemplate.opsForValue().set(key, "1", WINDOW_SIZE, WINDOW_UNIT);
                } else if (count >= MAX_REQUESTS) {
                    response.sendError(429, "Rate limit exceeded");
                    return;
                } else {
                    // Increment without touching expiry
                    redisTemplate.opsForValue().increment(key);
                }
            } catch (Exception e) {
                // If Redis fails, allow request (fail-open)
                System.err.println("Rate limiting error: " + e.getMessage());
            }
            
            filterChain.doFilter(request, response);

    }
}
