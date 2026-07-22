package br.com.brew.brassia.security.adapter.inbound.web.dto;

public record EnrollTotpResponse(String secret, String otpauthUri) {}
