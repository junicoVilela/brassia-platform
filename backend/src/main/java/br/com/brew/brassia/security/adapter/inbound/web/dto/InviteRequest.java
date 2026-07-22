package br.com.brew.brassia.security.adapter.inbound.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InviteRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 160) String displayName) {}
