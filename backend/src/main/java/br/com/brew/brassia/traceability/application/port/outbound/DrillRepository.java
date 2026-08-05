package br.com.brew.brassia.traceability.application.port.outbound;

import br.com.brew.brassia.traceability.domain.RecallDrill;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistência do simulado (FDS-004). Só o resultado medido é gravado; o escopo não. */
public interface DrillRepository {

    void insert(RecallDrill drill);

    Optional<RecallDrill> findById(UUID breweryId, UUID id);

    Optional<RecallDrill> findForUpdate(UUID breweryId, UUID id);

    List<RecallDrill> findAll(UUID breweryId);

    void finish(RecallDrill drill);

    long nextSequence(UUID breweryId, int year);
}
