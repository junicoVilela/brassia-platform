package br.com.brew.brassia.security.application.port.outbound;

import br.com.brew.brassia.security.domain.AccountToken;
import java.util.Optional;

public interface AccountTokenRepository {
    void save(AccountToken token);
    Optional<AccountToken> findInvitationByHash(String tokenHash);
}
