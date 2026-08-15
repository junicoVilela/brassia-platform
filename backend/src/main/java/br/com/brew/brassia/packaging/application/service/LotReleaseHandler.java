package br.com.brew.brassia.packaging.application.service;

import br.com.brew.brassia.packaging.application.port.outbound.FinishedLotRepository;
import br.com.brew.brassia.packaging.application.port.outbound.LotReleaseRepository;
import br.com.brew.brassia.packaging.domain.LotAlreadyReleasedException;
import br.com.brew.brassia.packaging.domain.LotRelease;
import br.com.brew.brassia.packaging.domain.UnknownFinishedLotException;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Liberar lote acabado para venda (SAL-001-B). */
public class LotReleaseHandler {

    private final FinishedLotRepository lots;
    private final LotReleaseRepository releases;

    public LotReleaseHandler(FinishedLotRepository lots, LotReleaseRepository releases) {
        this.lots = Objects.requireNonNull(lots);
        this.releases = Objects.requireNonNull(releases);
    }

    @Transactional
    public void release(UUID breweryId, UUID finishedLotId, UUID actorId, String note) {
        lots.findById(breweryId, finishedLotId)
                .orElseThrow(() -> new UnknownFinishedLotException(finishedLotId));
        // A checagem serve para a mensagem dizer quem liberou e quando; a garantia é a chave primária
        // em finished_lot_id, que é o que sobrevive a duas requisições simultâneas.
        releases.find(breweryId, finishedLotId).ifPresent(r -> {
            throw new LotAlreadyReleasedException(r.releasedBy(), r.releasedAt());
        });
        releases.insert(new LotRelease(finishedLotId, breweryId, actorId, Instant.now(), note));
    }
}
