package br.com.brew.brassia.planning.application.service;

import br.com.brew.brassia.planning.application.port.inbound.GetBrewOrderUseCase;
import br.com.brew.brassia.planning.application.port.outbound.BrewOrderRepository;
import br.com.brew.brassia.planning.domain.BrewOrder;
import java.util.Objects;
import java.util.UUID;

public final class GetBrewOrderHandler implements GetBrewOrderUseCase {

    private final BrewOrderRepository repository;

    public GetBrewOrderHandler(BrewOrderRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public BrewOrder handle(UUID breweryId, UUID orderId) {
        return repository.findById(breweryId, orderId)
                .orElseThrow(() -> new IllegalArgumentException("ordem de produção inexistente"));
    }
}
