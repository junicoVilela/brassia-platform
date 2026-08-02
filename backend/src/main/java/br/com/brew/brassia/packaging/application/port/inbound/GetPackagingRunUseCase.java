package br.com.brew.brassia.packaging.application.port.inbound;

import br.com.brew.brassia.packaging.domain.PackagingRun;
import java.util.Optional;
import java.util.UUID;

/** Execução registrada de um plano de envase (PKG-003). */
public interface GetPackagingRunUseCase {
    Optional<PackagingRun> handle(UUID breweryId, UUID planId);
}
