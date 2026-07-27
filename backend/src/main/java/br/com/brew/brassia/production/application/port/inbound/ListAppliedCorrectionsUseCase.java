package br.com.brew.brassia.production.application.port.inbound;

import br.com.brew.brassia.production.domain.AppliedCorrection;
import java.util.List;
import java.util.UUID;

public interface ListAppliedCorrectionsUseCase {
    List<AppliedCorrection> handle(UUID breweryId, UUID batchId);
}
