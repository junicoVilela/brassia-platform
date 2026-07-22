package br.com.brew.brassia.security.application.port.inbound;

import java.util.UUID;

public interface HasActiveMfaQuery {
    boolean hasActiveTotp(UUID userId);
}
