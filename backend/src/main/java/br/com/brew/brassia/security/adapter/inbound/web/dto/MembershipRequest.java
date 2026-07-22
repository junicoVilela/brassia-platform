package br.com.brew.brassia.security.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record MembershipRequest(@NotNull UUID groupId) {}
