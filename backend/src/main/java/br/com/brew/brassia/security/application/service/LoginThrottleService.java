package br.com.brew.brassia.security.application.service;

import br.com.brew.brassia.security.application.port.outbound.LoginThrottleRepository;
import br.com.brew.brassia.security.application.port.outbound.SecurityAlertRepository;
import br.com.brew.brassia.security.application.port.outbound.TokenHasher;
import br.com.brew.brassia.shared.security.TooManyRequestsException;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Limitação progressiva de login por e-mail e IP (SEC-012). */
public final class LoginThrottleService {
    private static final int MAX_FAILURES = 5;
    private static final int PENALTY_SECONDS = 60;

    private final LoginThrottleRepository throttle;
    private final SecurityAlertRepository alerts;
    private final TokenHasher tokenHasher;

    public LoginThrottleService(LoginThrottleRepository throttle, SecurityAlertRepository alerts, TokenHasher tokenHasher) {
        this.throttle = Objects.requireNonNull(throttle);
        this.alerts = Objects.requireNonNull(alerts);
        this.tokenHasher = Objects.requireNonNull(tokenHasher);
    }

    public void checkAllowed(String email, String ip) {
        var now = Instant.now();
        checkSubject(hash(email), LoginThrottleRepository.SubjectType.EMAIL, now);
        if (ip != null) {
            checkSubject(hash(ip), LoginThrottleRepository.SubjectType.IP, now);
        }
    }

    public void recordFailure(String email, String ip, UUID userId, UUID breweryId) {
        var now = Instant.now();
        recordSubject(hash(email), LoginThrottleRepository.SubjectType.EMAIL, now, userId, breweryId);
        if (ip != null) {
            recordSubject(hash(ip), LoginThrottleRepository.SubjectType.IP, now, userId, breweryId);
        }
    }

    public void recordSuccess(String email, String ip) {
        throttle.reset(hash(email), LoginThrottleRepository.SubjectType.EMAIL);
        if (ip != null) {
            throttle.reset(hash(ip), LoginThrottleRepository.SubjectType.IP);
        }
    }

    private void checkSubject(String hash, LoginThrottleRepository.SubjectType type, Instant now) {
        throttle.find(hash, type).ifPresent(state -> {
            if (state.penaltyUntil() != null && state.penaltyUntil().isAfter(now)) {
                throw new TooManyRequestsException("penalidade ativa");
            }
        });
    }

    private void recordSubject(String hash, LoginThrottleRepository.SubjectType type, Instant now,
            UUID userId, UUID breweryId) {
        var current = throttle.find(hash, type).map(LoginThrottleRepository.ThrottleState::failureCount).orElse(0);
        var failures = current + 1;
        Instant penalty = null;
        if (failures >= MAX_FAILURES) {
            penalty = now.plusSeconds(PENALTY_SECONDS);
            alerts.create(breweryId, userId, "LOGIN_THROTTLE", "HIGH", Map.of(
                    "subjectType", type.name(),
                    "failureCount", failures));
        }
        throttle.recordFailure(hash, type, failures, penalty);
    }

    private String hash(String value) {
        return tokenHasher.hash(value == null ? "" : value.trim().toLowerCase());
    }
}
