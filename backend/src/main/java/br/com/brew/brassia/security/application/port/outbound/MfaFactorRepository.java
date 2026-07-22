package br.com.brew.brassia.security.application.port.outbound;

import br.com.brew.brassia.security.domain.MfaFactor;
import br.com.brew.brassia.security.domain.UserId;
import java.util.Optional;

public interface MfaFactorRepository {
    void save(MfaFactor factor);
    Optional<MfaFactor> findActiveTotpByUserId(UserId userId);
    Optional<MfaFactor> findPendingTotpByUserId(UserId userId);
    void revokeAllTotp(UserId userId);
}
