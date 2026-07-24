package br.com.brew.brassia.referencedata.application.port.outbound;

import br.com.brew.brassia.referencedata.domain.ReferenceSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReferenceSourceRepository {

    /** Nome já usado no mesmo escopo (global quando {@code breweryId} nulo). */
    boolean existsByName(UUID breweryId, String name);

    void insert(ReferenceSource source);

    /** Fonte visível à cervejaria: a própria ou uma global. */
    Optional<ReferenceSource> findVisible(UUID breweryId, UUID id);

    /** Fontes globais somadas às da cervejaria, paginadas. */
    List<ReferenceSource> findPage(UUID breweryId, int page, int size);

    long count(UUID breweryId);
}
