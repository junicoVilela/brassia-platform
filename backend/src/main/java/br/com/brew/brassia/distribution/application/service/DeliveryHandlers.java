package br.com.brew.brassia.distribution.application.service;

import br.com.brew.brassia.container.ContainerMovementCommands;
import br.com.brew.brassia.distribution.application.port.outbound.LoadRepository;
import br.com.brew.brassia.distribution.application.port.outbound.ProofRepository;
import br.com.brew.brassia.distribution.domain.CoarseLocation;
import br.com.brew.brassia.distribution.domain.ConsentedMedia;
import br.com.brew.brassia.distribution.domain.DeliveryNotRecordableException;
import br.com.brew.brassia.distribution.domain.DeliveryOutcome;
import br.com.brew.brassia.distribution.domain.LoadStatus;
import br.com.brew.brassia.distribution.domain.LoadStop;
import br.com.brew.brassia.distribution.domain.ProofOfDelivery;
import br.com.brew.brassia.distribution.domain.UnknownLoadException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prova de entrega e coleta (LOG-002).
 *
 * <p><strong>Registrar a entrega move o vasilhame</strong> — e é isso que faz o estoque contar certo sem
 * ninguém digitar duas vezes. O que desceu vai para o cliente; o que foi recolhido volta como
 * {@code RETURNED}, sujo, com o período do lote fechado.
 */
public class DeliveryHandlers {

    private final LoadRepository loads;
    private final ProofRepository proofs;
    private final ContainerMovementCommands containers;

    public DeliveryHandlers(LoadRepository loads, ProofRepository proofs,
            ContainerMovementCommands containers) {
        this.loads = Objects.requireNonNull(loads);
        this.proofs = Objects.requireNonNull(proofs);
        this.containers = Objects.requireNonNull(containers);
    }

    @Transactional
    public UUID record(UUID breweryId, UUID loadId, UUID stopId, DeliveryOutcome outcome,
            Instant occurredAt, UUID actor, List<UUID> delivered, List<UUID> collected, String note,
            ConsentedMedia media, CoarseLocation location) {
        var load = loads.find(breweryId, loadId).orElseThrow(() -> new UnknownLoadException(loadId));
        if (load.status() != LoadStatus.IN_ROUTE) {
            // Uma entrega registrada antes da saída é um registro do que não aconteceu.
            throw DeliveryNotRecordableException.loadNotOnTheRoad();
        }
        var stop = load.route().stream().filter(s -> s.id().equals(stopId)).findFirst()
                .orElseThrow(DeliveryNotRecordableException::notInStop);
        if (proofs.originalOf(breweryId, stopId).isPresent()) {
            // O duplo clique do celular no meio da rua viraria duas entregas para o mesmo cliente.
            throw DeliveryNotRecordableException.alreadyRecorded();
        }
        if (!stop.containerIds().containsAll(delivered)) {
            throw DeliveryNotRecordableException.notInStop();
        }

        var proof = ProofOfDelivery.record(UUID.randomUUID(), stopId, outcome, occurredAt, actor,
                delivered, collected, note, media, location, foraDaJanela(stop, occurredAt));
        proofs.record(breweryId, proof);
        moveVasilhames(breweryId, proof);
        return proof.id();
    }

    /**
     * Corrige uma prova — sem apagar a anterior.
     *
     * <p><strong>O movimento de vasilhame não é refeito aqui.</strong> Um keg que foi marcado como
     * entregue e na verdade voltou precisa ser movido por quem o tem na mão, e adivinhar a transição a
     * partir da correção produziria estados que ninguém observou. A correção conserta o <em>registro</em>;
     * o vasilhame se conserta no ciclo dele.
     */
    @Transactional
    public UUID correct(UUID breweryId, UUID stopId, DeliveryOutcome outcome, Instant occurredAt,
            UUID actor, List<UUID> delivered, List<UUID> collected, String reason) {
        var original = proofs.originalOf(breweryId, stopId)
                .orElseThrow(DeliveryNotRecordableException::noOriginal);
        var correcao = ProofOfDelivery.correcting(UUID.randomUUID(), original, outcome, occurredAt,
                actor, delivered, collected, reason);
        proofs.record(breweryId, correcao);
        return correcao.id();
    }

    @Transactional(readOnly = true)
    public List<ProofOfDelivery> ofStop(UUID breweryId, UUID stopId) {
        return proofs.ofStop(breweryId, stopId);
    }

    @Transactional(readOnly = true)
    public List<ProofOfDelivery> ofLoad(UUID breweryId, UUID loadId) {
        loads.find(breweryId, loadId).orElseThrow(() -> new UnknownLoadException(loadId));
        return proofs.ofLoad(breweryId, loadId);
    }

    /**
     * A carga saiu: tudo o que está nela vai para a rua.
     *
     * <p>É aqui que o {@code DEB-LOG-001} se fecha — um vasilhame na rua não está mais {@code FILLED},
     * e o próprio ciclo passa a impedir que ele entre numa segunda carga.
     */
    @Transactional
    public void depart(UUID breweryId, UUID loadId) {
        var load = loads.find(breweryId, loadId).orElseThrow(() -> new UnknownLoadException(loadId));
        load.depart();
        loads.update(load);
        containers.dispatch(breweryId, load.allContainers());
    }

    private void moveVasilhames(UUID breweryId, ProofOfDelivery proof) {
        if (!proof.deliveredContainerIds().isEmpty()) {
            containers.deliver(breweryId, proof.deliveredContainerIds());
        }
        if (!proof.collectedContainerIds().isEmpty()) {
            containers.collect(breweryId, proof.collectedContainerIds());
        }
    }

    private static boolean foraDaJanela(LoadStop stop, Instant quando) {
        return stop.window().map(w -> w.missedAt(quando)).orElse(false);
    }
}
