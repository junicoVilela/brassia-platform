package br.com.brew.brassia.security.application.port.outbound;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface LoginThrottleRepository {
    enum SubjectType { EMAIL, IP }

    record ThrottleState(int failureCount, Instant penaltyUntil) {}

    Optional<ThrottleState> find(String subjectHash, SubjectType type);
    void recordFailure(String subjectHash, SubjectType type, int failureCount, Instant penaltyUntil);
    void reset(String subjectHash, SubjectType type);
}
