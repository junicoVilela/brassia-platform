package br.com.brew.brassia.security.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateGroupRequest(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 500) String description,
        @NotNull List<@NotBlank @Size(max = 120) String> permissionCodes,
        long version) {}
