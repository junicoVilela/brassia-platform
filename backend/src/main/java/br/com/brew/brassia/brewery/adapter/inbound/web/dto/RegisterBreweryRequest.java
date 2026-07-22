package br.com.brew.brassia.brewery.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterBreweryRequest(
        @NotBlank @Size(max = 40) String code,
        @NotBlank @Size(max = 160) String name,
        @NotBlank @Size(max = 80) String timezone) {}
