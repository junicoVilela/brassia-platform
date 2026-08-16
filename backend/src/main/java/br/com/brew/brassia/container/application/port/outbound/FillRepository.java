package br.com.brew.brassia.container.application.port.outbound;

import br.com.brew.brassia.container.domain.ContainerFill;
import br.com.brew.brassia.container.domain.ContainerLocation;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FillRepository {

    void record(ContainerFill fill);

    /** Fecha o conteúdo vivo, se houver. Repetir não move a data. */
    void empty(UUID breweryId, UUID containerId, Instant at);

    Optional<ContainerFill> currentOf(UUID breweryId, UUID containerId);

    List<ContainerFill> historyOf(UUID breweryId, UUID containerId);

    /** A consulta do recall: que vasilhames tiveram este lote, e quando. */
    List<ContainerFill> ofLot(UUID breweryId, UUID finishedLotId);

    void locate(ContainerLocation location);

    List<ContainerLocation> locationsOf(UUID breweryId, UUID containerId);
}
