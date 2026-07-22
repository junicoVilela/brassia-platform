package br.com.brew.brassia.security.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DecideAccessReviewItemRequest(
        @NotBlank @Pattern(regexp = "KEEP|REMOVE") String decision,
        String justification) {}
