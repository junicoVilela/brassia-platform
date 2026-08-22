package br.com.brew.brassia.container.application.service;

import br.com.brew.brassia.container.application.port.outbound.ContainerRepository;
import br.com.brew.brassia.container.application.port.outbound.FillRepository;
import br.com.brew.brassia.container.domain.Container;
import br.com.brew.brassia.container.domain.ContainerIdentifier;
import br.com.brew.brassia.container.domain.ContainerInspection;
import br.com.brew.brassia.container.domain.ContainerKind;
import br.com.brew.brassia.container.domain.ContainerLocation;
import br.com.brew.brassia.container.domain.ContainerModifiedException;
import br.com.brew.brassia.container.domain.ContainerRetiredException;
import br.com.brew.brassia.container.domain.ContainerState;
import br.com.brew.brassia.container.domain.FillNotAllowedException;
import br.com.brew.brassia.container.domain.IdentifierTechnology;
import br.com.brew.brassia.container.domain.LocationKind;
import br.com.brew.brassia.container.domain.Ownership;
import br.com.brew.brassia.container.domain.UnknownContainerException;
import br.com.brew.brassia.container.domain.UnknownIdentifierException;
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
    private final FillRepository fills;

    public ContainerHandlers(ContainerRepository containers, FillRepository fills) {
        this.containers = Objects.requireNonNull(containers);
        this.fills = Objects.requireNonNull(fills);
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

    /**
     * Aposenta a etiqueta — e recusa quando não havia etiqueta para aposentar (DEB-CON-003 #6).
     *
     * <p>O `UPDATE` já filtrava por cervejaria e por "ainda não aposentada", mas ninguém olhava quantas
     * linhas ele tinha tocado: uma etiqueta inexistente, já aposentada ou de outra cervejaria devolvia
     * {@code 204} e gravava auditoria de sucesso. Quem conferisse a trilha depois leria uma aposentadoria
     * que não existiu — e quem tentou aposentar a etiqueta errada acharia que tinha conseguido.
     */
    @Transactional
    public void retireIdentifier(UUID breweryId, UUID identifierId) {
        if (!containers.retireIdentifier(breweryId, identifierId, Instant.now())) {
            throw new UnknownIdentifierException(identifierId);
        }
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
                // Encher deixou de ser movimento cego (CON-002): sem dizer qual lote entrou, o vasilhame
                // ficaria "cheio de nada" e a genealogia teria um buraco exatamente onde o recall olha.
                case FILLED -> throw FillNotAllowedException.lot("content_required",
                        "Encher exige dizer qual lote entrou — um vasilhame cheio sem conteúdo "
                                + "registrado é um buraco na rastreabilidade.");
                case IN_TRANSIT -> c.dispatch();
                case AT_CUSTOMER -> c.deliver();
                case RETURNED -> {
                    c.collect();
                    // O que volta do cliente volta VAZIO, e o período do lote se fecha aqui. Fechar não
                    // apaga: o intervalo continua ligando este keg àquele lote.
                    fills.empty(breweryId, containerId, Instant.now());
                }
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

    /** A volta do vasilhame dado como perdido: entra como sujo, e não como disponível (DUV-CON-002). */
    @Transactional
    public void recover(UUID breweryId, UUID containerId, String reason) {
        apply(breweryId, containerId, c -> c.recover(reason, Instant.now()));
    }

    /** Baixa por perda: sai do inventário onde quer que estivesse, com o motivo junto. */
    @Transactional
    public void declareLost(UUID breweryId, UUID containerId, String reason) {
        apply(breweryId, containerId, c -> c.declareLost(reason, Instant.now()));
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
        var estadoAntes = container.state();
        acao.accept(container);
        if (!containers.update(container)) {
            // Alguém alterou o mesmo vasilhame entre a leitura e agora. Recusar é o certo: não há como
            // saber qual das duas intenções vale — quem condenou viu uma avaria, quem despachou viu a
            // carga sair —, e escolher uma em silêncio é o que fazia a outra sumir (DEB-CON-003).
            throw new ContainerModifiedException(containerId);
        }
        // A posição acompanha o ciclo sem ninguém digitar: o registro que depende de alguém lembrar é o
        // registro que falta justamente no dia em que o keg some.
        //
        // SÓ QUANDO O ESTADO MUDA (DEB-CON-003 #5). Antes disto toda escrita gravava posição, e inspecionar
        // ou marcar avaria num keg que está no cliente criava uma linha nova dizendo "chegou no cliente
        // agora" — reiniciando o relógio de permanência, que é o número da fila de atrasados da CON-003. O
        // keg não se moveu; alguém só escreveu sobre ele.
        if (container.state() != estadoAntes) {
            posicaoDe(container.state()).ifPresent(kind ->
                    fills.locate(ContainerLocation.at(containerId, kind, null, Instant.now())));
        }
    }

    /**
     * A posição implicada pelo estado, quando ela é inequívoca.
     *
     * <p>Vazio e manutenção ficam de fora de propósito: um keg vazio pode estar em qualquer depósito, e a
     * oficina pode ser da casa ou de terceiro. Inventar a posição desses dois encheria o histórico de
     * linhas que ninguém observou.
     */
    private static java.util.Optional<LocationKind> posicaoDe(ContainerState state) {
        return switch (state) {
            case IN_TRANSIT -> java.util.Optional.of(LocationKind.IN_TRANSIT);
            case AT_CUSTOMER -> java.util.Optional.of(LocationKind.CUSTOMER);
            case RETURNED -> java.util.Optional.of(LocationKind.WAREHOUSE);
            default -> java.util.Optional.empty();
        };
    }

    private Container require(UUID breweryId, UUID containerId) {
        return containers.find(breweryId, containerId)
                .orElseThrow(() -> new UnknownContainerException(containerId));
    }
}
