package br.com.brew.brassia.production.application.port.outbound;

import br.com.brew.brassia.production.domain.AppliedCorrection;
import java.util.List;
import java.util.UUID;

public interface AppliedCorrectionRepository {
    void insert(AppliedCorrection correction);

    List<AppliedCorrection> findByBatch(UUID breweryId, UUID batchId);
}
