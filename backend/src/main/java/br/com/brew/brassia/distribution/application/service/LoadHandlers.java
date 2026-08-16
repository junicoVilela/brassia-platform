package br.com.brew.brassia.distribution.application.service;

import br.com.brew.brassia.container.ContainerShippingLookup;
import br.com.brew.brassia.distribution.application.port.outbound.LoadRepository;
import br.com.brew.brassia.distribution.domain.ContainerNotShippableException;
import br.com.brew.brassia.distribution.domain.DeliveryWindow;
import br.com.brew.brassia.distribution.domain.Load;
import br.com.brew.brassia.distribution.domain.LoadStop;
import br.com.brew.brassia.distribution.domain.UnknownLoadException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.transaction.annotation.Transactional;

/**
 * Montar, conferir e despachar a carga (LOG-001).
 *
 * <p><strong>A separação de deveres tem duas metades.</strong> O agregado recusa que a mesma pessoa
 * planeje e libere; a alçada {@code distribution.load.release}, separada de {@code plan}, é a outra. Só a
 * primeira seria contornável dando as duas permissões a todo mundo; só a segunda seria contornável por
 * quem tem as duas. Juntas, elas exigem duas pessoas de verdade.
 */
public class LoadHandlers {

    private final LoadRepository loads;
    private final ContainerShippingLookup containers;

    public LoadHandlers(LoadRepository loads, ContainerShippingLookup containers) {
        this.loads = Objects.requireNonNull(loads);
        this.containers = Objects.requireNonNull(containers);
    }

    @Transactional
    public UUID plan(UUID breweryId, String code, LocalDate scheduledFor, BigDecimal capacityLiters,
            UUID planner) {
        var load = Load.plan(UUID.randomUUID(), breweryId, code, scheduledFor, capacityLiters, planner);
        loads.save(load);
        return load.id();
    }

    @Transactional
    public UUID addStop(UUID breweryId, UUID loadId, UUID customerId, String customerName,
            int sequence, Instant from, Instant to) {
        var stopId = UUID.randomUUID();
        var window = from == null && to == null ? null : new DeliveryWindow(from, to);
        apply(breweryId, loadId,
                load -> load.addStop(LoadStop.create(stopId, customerId, customerName, sequence,
                        window)));
        return stopId;
    }

    @Transactional
    public void removeStop(UUID breweryId, UUID loadId, UUID stopId) {
        apply(breweryId, loadId, load -> load.removeStop(stopId));
    }

    /**
     * Põe um vasilhame numa parada — e é aqui que a saída cobra a qualidade.
     *
     * <p>A checagem acontece <strong>na montagem</strong>, e não só na liberação, porque descobrir na
     * conferência que metade da carga não pode sair joga fora o trabalho de montá-la. A liberação confere
     * de novo, pelo motivo oposto: entre montar e sair, um lote pode ter entrado em quarentena.
     */
    @Transactional
    public void loadContainer(UUID breweryId, UUID loadId, UUID stopId, UUID containerId) {
        var podeSair = containers.shippable(breweryId, containerId)
                .orElseThrow(() -> new ContainerNotShippableException("container_not_found",
                        "Vasilhame não encontrado."));
        podeSair.blocker().ifPresent(b -> {
            throw new ContainerNotShippableException(b.code(), b.message());
        });
        loads.openLoadWith(breweryId, containerId, loadId).ifPresent(outra -> {
            // O engano do dia a dia: o mesmo keg em duas rotas. Uma das duas entregas vai faltar.
            throw new ContainerNotShippableException("already_loaded",
                    "O vasilhame já está na carga " + outra + ".");
        });
        apply(breweryId, loadId,
                load -> load.load(stopId, containerId, podeSair.volumeLiters()));
    }

    @Transactional
    public void unloadContainer(UUID breweryId, UUID loadId, UUID containerId) {
        apply(breweryId, loadId, load -> load.unload(containerId));
    }

    @Transactional
    public void assign(UUID breweryId, UUID loadId, UUID driverId, String vehicle) {
        apply(breweryId, loadId, load -> load.assign(driverId, vehicle));
    }

    /**
     * Confere e libera.
     *
     * <p>A carga é revalidada inteira antes de sair: entre montar e liberar pode ter passado um dia, e um
     * lote pode ter vencido ou entrado em quarentena nesse meio-tempo. Confiar na checagem da montagem
     * seria confiar num retrato de ontem.
     */
    @Transactional
    public void release(UUID breweryId, UUID loadId, UUID releasedBy) {
        var load = require(breweryId, loadId);
        for (var containerId : load.allContainers()) {
            var podeSair = containers.shippable(breweryId, containerId).orElseThrow(
                    () -> new ContainerNotShippableException("container_not_found",
                            "Vasilhame não encontrado."));
            podeSair.blocker().ifPresent(b -> {
                throw new ContainerNotShippableException(b.code(),
                        podeSair.code() + ": " + b.message());
            });
        }
        load.release(releasedBy, Instant.now());
        loads.update(load);
    }

    @Transactional
    public void reopen(UUID breweryId, UUID loadId) {
        apply(breweryId, loadId, Load::reopen);
    }

    @Transactional
    public void depart(UUID breweryId, UUID loadId) {
        apply(breweryId, loadId, Load::depart);
    }

    @Transactional
    public void close(UUID breweryId, UUID loadId) {
        apply(breweryId, loadId, Load::close);
    }

    @Transactional
    public void cancel(UUID breweryId, UUID loadId) {
        apply(breweryId, loadId, Load::cancel);
    }

    @Transactional(readOnly = true)
    public Load read(UUID breweryId, UUID loadId) {
        return require(breweryId, loadId);
    }

    @Transactional(readOnly = true)
    public List<Load> list(UUID breweryId, LocalDate day) {
        return loads.list(breweryId, day);
    }

    private void apply(UUID breweryId, UUID loadId, Consumer<Load> acao) {
        var load = require(breweryId, loadId);
        acao.accept(load);
        loads.update(load);
    }

    private Load require(UUID breweryId, UUID loadId) {
        return loads.find(breweryId, loadId).orElseThrow(() -> new UnknownLoadException(loadId));
    }
}
