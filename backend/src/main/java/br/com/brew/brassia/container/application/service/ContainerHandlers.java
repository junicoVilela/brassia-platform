package br.com.brew.brassia.container.application.service;

import br.com.brew.brassia.container.application.port.outbound.ContainerRepository;
import br.com.brew.brassia.container.domain.Container;
import br.com.brew.brassia.container.domain.ContainerIdentifier;
import br.com.brew.brassia.container.domain.ContainerInspection;
import br.com.brew.brassia.container.domain.ContainerKind;
import br.com.brew.brassia.container.domain.ContainerRetiredException;
import br.com.brew.brassia.container.domain.ContainerState;
import br.com.brew.brassia.container.domain.IdentifierTechnology;
import br.com.brew.brassia.container.domain.Ownership;
import br.com.brew.brassia.container.domain.UnknownContainerException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.transaction.annotation.Transactional;

/** Cadastro, etiqueta, inspeção e ciclo do vasilhame (CON-001). */
public class ContainerHandlers {

    private final ContainerRepository containers;

    public ContainerHandlers(ContainerRepository containers) {
        this.containers = Objects.requireNonNull(containers);
    }

    @Transactional
    public UUID register(UUID breweryId, String code, ContainerKind kind, BigDecimal capacityLiters,
            Ownership ownership) {
        var container = Container.register(UUID.randomUUID(), breweryId, code, kind, capacityLiters,
                ownership);
        containers.save(container);
        return container.id();
    }

    @Transactional
    public UUID assignIdentifier(UUID breweryId, UUID containerId, String value,
            IdentifierTechnology technology) {
        var container = require(breweryId, containerId);
        if (container.isRetired()) {
            // Etiquetar um vasilhame baixado colocaria de volta em circulação o que saiu dela.
            throw new ContainerRetiredException();
        }
        var identifier = ContainerIdentifier.assign(UUID.randomUUID(), containerId, value, technology,
                Instant.now());
        containers.assign(identifier);
        return identifier.id();
    }

    @Transactional
    public void retireIdentifier(UUID breweryId, UUID identifierId) {
        containers.retireIdentifier(breweryId, identifierId, Instant.now());
    }

    @Transactional
    public void inspect(UUID breweryId, UUID containerId, Instant performedAt, Instant validUntil,
            UUID inspector, String note) {
        apply(breweryId, containerId,
                c -> c.inspect(new ContainerInspection(performedAt, validUntil, inspector, note)));
    }

    @Transactional
    public void move(UUID breweryId, UUID containerId, ContainerState to) {
        // O ciclo é máquina de estados: a tela manda para onde quer ir, e o agregado diz se dá.
        apply(breweryId, containerId, c -> {
            switch (to) {
                case FILLED -> c.fill(Instant.now());
                case IN_TRANSIT -> c.dispatch();
                case AT_CUSTOMER -> c.deliver();
                case RETURNED -> c.collect();
                // "Vazio" tem dois caminhos, e o estado atual diz qual: quem sai da oficina também
                // recupera a condição; quem volta do cliente só é declarado limpo.
                case EMPTY -> {
                    if (c.state() == ContainerState.IN_MAINTENANCE) {
                        c.returnFromMaintenance();
                    } else {
                        c.releaseToStock();
                    }
                }
                case IN_MAINTENANCE -> c.sendToMaintenance();
                default -> throw new IllegalArgumentException(
                        "Baixa não é movimento de ciclo: ela tem motivo e alçada própria.");
            }
        });
    }

    @Transactional
    public void markDamaged(UUID breweryId, UUID containerId) {
        apply(breweryId, containerId, Container::markDamaged);
    }

    @Transactional
    public void condemn(UUID breweryId, UUID containerId) {
        apply(breweryId, containerId, Container::condemn);
    }

    @Transactional
    public void retire(UUID breweryId, UUID containerId, String reason) {
        apply(breweryId, containerId, c -> c.retire(reason, Instant.now()));
    }

    @Transactional(readOnly = true)
    public Container read(UUID breweryId, UUID containerId) {
        return require(breweryId, containerId);
    }

    @Transactional(readOnly = true)
    public List<Container> list(UUID breweryId, ContainerState state) {
        return containers.list(breweryId, state == null ? null : state.name());
    }

    @Transactional(readOnly = true)
    public List<ContainerIdentifier> identifiers(UUID breweryId, UUID containerId) {
        require(breweryId, containerId);
        return containers.identifiersOf(containerId);
    }

    /**
     * Uma leitura de código vira um contêiner — e nada além disso.
     *
     * <p>Quem chamou continua precisando de alçada para agir sobre o que encontrou: o código lido não
     * concede nada, e é por isso que este método devolve o vasilhame, e não uma sessão.
     */
    @Transactional(readOnly = true)
    public Container resolve(UUID breweryId, String value) {
        return containers.resolve(breweryId, value)
                .orElseThrow(() -> new UnknownContainerException(value));
    }

    private void apply(UUID breweryId, UUID containerId, Consumer<Container> acao) {
        var container = require(breweryId, containerId);
        acao.accept(container);
        containers.update(container);
    }

    private Container require(UUID breweryId, UUID containerId) {
        return containers.find(breweryId, containerId)
                .orElseThrow(() -> new UnknownContainerException(containerId));
    }
}
