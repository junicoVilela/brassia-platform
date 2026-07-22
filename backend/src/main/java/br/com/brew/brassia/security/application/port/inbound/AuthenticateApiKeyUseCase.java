package br.com.brew.brassia.security.application.port.inbound;

import br.com.brew.brassia.shared.security.ServicePrincipal;
import java.util.Optional;

public interface AuthenticateApiKeyUseCase {
    Optional<ServicePrincipal> authenticate(String rawKey);
}
