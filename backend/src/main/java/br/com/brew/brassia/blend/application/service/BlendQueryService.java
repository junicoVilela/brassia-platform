package br.com.brew.brassia.blend.application.service;

import br.com.brew.brassia.blend.application.port.inbound.BlendQueries;
import br.com.brew.brassia.blend.application.port.outbound.BlendRepository;
import br.com.brew.brassia.blend.domain.BlendOperation;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class BlendQueryService implements BlendQueries {

    private final BlendRepository operations;

    public BlendQueryService(BlendRepository operations) {
        this.operations = Objects.requireNonNull(operations, "operations");
    }

    @Override
    public Optional<BlendOperation> find(UUID breweryId, UUID operationId) {
        return operations.find(breweryId, operationId);
    }

    @Override
    public List<BlendOperation> list(UUID breweryId) {
        return operations.list(breweryId);
    }
}
