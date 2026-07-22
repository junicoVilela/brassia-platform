package br.com.brew.brassia.security.application.port.outbound;

import br.com.brew.brassia.security.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RecoveryCodeRepository {
    void replaceAll(UserId userId, List<String> codeHashes, Instant generatedAt);
    Optional<String> consumeByHash(UserId userId, String codeHash, Instant now);
    int countUnused(UserId userId);
}
