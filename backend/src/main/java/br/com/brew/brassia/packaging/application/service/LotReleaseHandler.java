package br.com.brew.brassia.packaging.application.service;

import br.com.brew.brassia.packaging.application.port.outbound.FinishedLotRepository;
import br.com.brew.brassia.packaging.application.port.outbound.FreshnessRepository;
import br.com.brew.brassia.packaging.FinishedLotReleased;
import br.com.brew.brassia.packaging.application.port.outbound.FinishedLotEventPublisher;
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
    private final FreshnessRepository freshness;
    private final FinishedLotEventPublisher events;

    public LotReleaseHandler(FinishedLotRepository lots, LotReleaseRepository releases,
            FreshnessRepository freshness, FinishedLotEventPublisher events) {
        this.lots = Objects.requireNonNull(lots);
        this.releases = Objects.requireNonNull(releases);
        this.freshness = Objects.requireNonNull(freshness);
        this.events = Objects.requireNonNull(events);
    }

    @Transactional
    public void release(UUID breweryId, UUID finishedLotId, UUID actorId, String note) {
        var lot = lots.findById(breweryId, finishedLotId)
                .orElseThrow(() -> new UnknownFinishedLotException(finishedLotId));
        // A checagem serve para a mensagem dizer quem liberou e quando; a garantia é a chave primária
        // em finished_lot_id, que é o que sobrevive a duas requisições simultâneas.
        releases.find(breweryId, finishedLotId).ifPresent(r -> {
            throw new LotAlreadyReleasedException(r.releasedBy(), r.releasedAt());
        });
        releases.insert(new LotRelease(finishedLotId, breweryId, actorId, Instant.now(), note));
        // A validade viaja no evento porque é ela que decide até quando a oferta faz sentido lá fora.
        // Pode ser nula: um lote liberado sem validade apurada não é vendável ainda (SAL-001-B), e o
        // e-commerce precisa saber disso em vez de anunciar sem prazo.
        var bestBefore = freshness.findByPlan(breweryId, lot.planId())
                .map(f -> f.effectiveBestBefore())
                .orElse(null);
        events.publish(new FinishedLotReleased(breweryId, lot.id(), lot.code(), lot.batchCode(),
                lot.units(), bestBefore, Instant.now()));
    }
}
