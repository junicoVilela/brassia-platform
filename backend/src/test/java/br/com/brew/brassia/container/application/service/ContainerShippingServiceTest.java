package br.com.brew.brassia.container.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.brew.brassia.container.application.port.outbound.ContainerRepository;
import br.com.brew.brassia.container.application.port.outbound.FillRepository;
import br.com.brew.brassia.container.domain.Container;
import br.com.brew.brassia.container.domain.ContainerFill;
import br.com.brew.brassia.container.domain.ContainerIdentifier;
import br.com.brew.brassia.container.domain.ContainerInspection;
import br.com.brew.brassia.container.domain.ContainerKind;
import br.com.brew.brassia.container.domain.ContainerLocation;
import br.com.brew.brassia.container.domain.ContainerState;
import br.com.brew.brassia.container.domain.Ownership;
import br.com.brew.brassia.packaging.SellableLotLookup;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A saída da casa cobra a assinatura da qualidade (LOG-001).
 *
 * <p>Este teste existe por um achado de review: quando o lote de dentro do vasilhame <strong>não
 * resolvia</strong>, o serviço devolvia {@code shippable: true} sem impedimento nenhum — o keg era
 * embarcado justamente pelo método que existe para impedir que cerveja não liberada saia. A ausência de
 * status era lida como ausência de problema.
 *
 * <p>Fica no nível de unidade porque o caminho não é alcançável pela API: encher já exige um lote que
 * resolve. É defesa em profundidade, e o que se fixa aqui é o <strong>sentido do silêncio</strong> —
 * não saber tem de bloquear, e não liberar.
 */
class ContainerShippingServiceTest {

    private static final UUID BREWERY = UUID.randomUUID();
    private static final UUID KEG = UUID.randomUUID();
    private static final UUID LOTE = UUID.randomUUID();

    @Test
    void loteQueNaoResolveBloqueiaAsaida() {
        var service = new ContainerShippingService(repositorioCom(cheio()), fillsCom(conteudo()),
                lotesQueNaoResolvem());

        var resultado = service.shippable(BREWERY, KEG).orElseThrow();

        assertThat(resultado.shippable())
                .as("não saber se a qualidade liberou não é o mesmo que ter liberado")
                .isFalse();
        assertThat(resultado.blocker()).isPresent();
        assertThat(resultado.blocker().orElseThrow().code()).isEqualTo("lot_not_found");
    }

    @Test
    void loteLiberadoSai() {
        // O contraponto: sem ele o teste acima passaria com um serviço que bloqueia tudo.
        var service = new ContainerShippingService(repositorioCom(cheio()), fillsCom(conteudo()),
                lotesLiberados());

        var resultado = service.shippable(BREWERY, KEG).orElseThrow();

        assertThat(resultado.shippable()).isTrue();
        assertThat(resultado.blocker()).isEmpty();
    }

    private static Container cheio() {
        var container = Container.register(KEG, BREWERY, "KEG-1", ContainerKind.KEG,
                new BigDecimal("50"), Ownership.OWN);
        container.inspect(new ContainerInspection(Instant.now(), Instant.now().plusSeconds(86_400),
                UUID.randomUUID(), null));
        container.fill(Instant.now());
        return container;
    }

    private static ContainerFill conteudo() {
        return ContainerFill.record(UUID.randomUUID(), KEG, LOTE, "OP-2026-0001/1",
                new BigDecimal("50"), Instant.now(), UUID.randomUUID());
    }

    private static ContainerRepository repositorioCom(Container container) {
        return new StubContainerRepository(container);
    }

    private static FillRepository fillsCom(ContainerFill fill) {
        return new StubFillRepository(fill);
    }

    /** O lote sumiu de `packaging`: `statusOf` volta vazio. */
    private static SellableLotLookup lotesQueNaoResolvem() {
        return new StubLots(Optional.empty());
    }

    private static SellableLotLookup lotesLiberados() {
        return new StubLots(Optional.of(new SellableLotLookup.LotSaleStatus(LOTE, "OP-2026-0001/1",
                true, Optional.empty(), LocalDate.now().plusMonths(6))));
    }

    private record StubContainerRepository(Container container) implements ContainerRepository {

        @Override
        public Optional<Container> find(UUID breweryId, UUID containerId) {
            return Optional.of(container);
        }

        @Override
        public void save(Container container) {}

        @Override
        public void update(Container container) {}

        @Override
        public List<Container> list(UUID breweryId, String state) {
            return List.of();
        }

        @Override
        public void assign(ContainerIdentifier identifier) {}

        @Override
        public void retireIdentifier(UUID breweryId, UUID identifierId, Instant at) {}

        @Override
        public List<ContainerIdentifier> identifiersOf(UUID containerId) {
            return List.of();
        }

        @Override
        public Optional<Container> resolve(UUID breweryId, String value) {
            return Optional.empty();
        }
    }

    private record StubFillRepository(ContainerFill fill) implements FillRepository {

        @Override
        public Optional<ContainerFill> currentOf(UUID breweryId, UUID containerId) {
            return Optional.of(fill);
        }

        @Override
        public void record(ContainerFill fill) {}

        @Override
        public void empty(UUID breweryId, UUID containerId, Instant at) {}

        @Override
        public List<ContainerFill> historyOf(UUID breweryId, UUID containerId) {
            return List.of();
        }

        @Override
        public List<ContainerFill> ofLot(UUID breweryId, UUID finishedLotId) {
            return List.of();
        }

        @Override
        public void locate(ContainerLocation location) {}

        @Override
        public List<ContainerLocation> locationsOf(UUID breweryId, UUID containerId) {
            return List.of();
        }
    }

    private record StubLots(Optional<SellableLotLookup.LotSaleStatus> status)
            implements SellableLotLookup {

        @Override
        public List<SellableLot> sellableLots(UUID breweryId, UUID recipeId, UUID containerId,
                LocalDate on) {
            return List.of();
        }

        @Override
        public Optional<LotSaleStatus> statusOf(UUID breweryId, UUID finishedLotId, LocalDate on) {
            return status;
        }
    }
}
