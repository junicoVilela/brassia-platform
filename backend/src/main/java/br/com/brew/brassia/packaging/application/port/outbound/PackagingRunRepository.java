package br.com.brew.brassia.packaging.application.port.outbound;

import br.com.brew.brassia.packaging.domain.PackagingRun;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface PackagingRunRepository {

    void insert(PackagingRun run);

    Optional<PackagingRun> findByPlan(UUID breweryId, UUID planId);

    /**
     * Volume já envasado do lote, somando as execuções anteriores. É o que impede um lote de
     * render mais cerveja do que existiu no tanque, quando ele é dividido em vários envases.
     */
    BigDecimal totalInputVolumeOfBatch(UUID breweryId, UUID batchId);
}
