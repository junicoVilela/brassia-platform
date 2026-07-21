package br.com.brew.brassia.security.application.port.outbound;

import br.com.brew.brassia.security.domain.AccountToken;

public interface AccountTokenRepository {
    void save(AccountToken token);
}
