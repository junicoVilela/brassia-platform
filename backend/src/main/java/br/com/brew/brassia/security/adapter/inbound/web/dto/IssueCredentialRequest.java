package br.com.brew.brassia.security.adapter.inbound.web.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record IssueCredentialRequest(@NotEmpty List<String> scopes) {}
