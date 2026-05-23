package com.smarturl.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

@Data
public class ShortenRequest {
    @NotBlank(message = "URL cannot be blank")
    @URL(message = "Must be a valid URL")
    private String longUrl;

    @Size(min = 3, max = 10, message = "Custom alias must be 3-10 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_-]*$", message = "Alias can only contain letters, numbers, hyphens, underscores")
    private String customAlias;

    private Integer expiresInDays;
}
