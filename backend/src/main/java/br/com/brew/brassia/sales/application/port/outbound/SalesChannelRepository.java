package br.com.brew.brassia.sales.application.port.outbound;

import br.com.brew.brassia.sales.domain.SalesChannel;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalesChannelRepository {

    void insert(SalesChannel channel, UUID actorId);

    void update(SalesChannel channel);

    Optional<SalesChannel> find(UUID breweryId, UUID id);

    List<SalesChannel> list(UUID breweryId, boolean onlyActive);

    boolean codeTaken(UUID breweryId, String code);
}
