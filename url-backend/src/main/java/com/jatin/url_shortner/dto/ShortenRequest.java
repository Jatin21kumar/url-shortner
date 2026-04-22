package com.jatin.url_shortner.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ShortenRequest {
    
    @NotBlank
    private String longUrl;
}
