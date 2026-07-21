package br.com.brew.brassia.security.application.port.outbound;

import br.com.brew.brassia.security.domain.PasswordCredential;
import br.com.brew.brassia.security.domain.UserId;
import java.util.Optional;

public interface PasswordCredentialRepository {
    void save(PasswordCredential credential);
    Optional<PasswordCredential> findByUserId(UserId userId);
}
