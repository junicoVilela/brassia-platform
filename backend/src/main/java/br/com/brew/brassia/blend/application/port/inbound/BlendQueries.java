package br.com.brew.brassia.blend.application.port.inbound;

import br.com.brew.brassia.blend.domain.BlendOperation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BlendQueries {

    Optional<BlendOperation> find(UUID breweryId, UUID operationId);

    List<BlendOperation> list(UUID breweryId);
}
