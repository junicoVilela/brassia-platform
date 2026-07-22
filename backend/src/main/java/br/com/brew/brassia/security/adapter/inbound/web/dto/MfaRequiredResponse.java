package br.com.brew.brassia.security.adapter.inbound.web.dto;

import java.util.List;

public record MfaRequiredResponse(String status, List<String> methods) {
    public static MfaRequiredResponse totp() {
        return new MfaRequiredResponse("MFA_REQUIRED", List.of("TOTP", "RECOVERY_CODE"));
    }
}
