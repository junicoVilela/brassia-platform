package br.com.brew.brassia.container.application.service;

import br.com.brew.brassia.container.ContainerShippingLookup;
import br.com.brew.brassia.container.application.port.outbound.ContainerRepository;
import br.com.brew.brassia.container.application.port.outbound.FillRepository;
import br.com.brew.brassia.container.domain.ContainerState;
import br.com.brew.brassia.packaging.SellableLotLookup;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compõe as condições de saída do vasilhame (LOG-001).
 *
 * <p><strong>Aqui a promessa da CON-002 se cumpre.</strong> Encher precede liberar: o keg é enchido na
 * produção, antes de a qualidade assinar. Quem exige a assinatura é a saída — e é este serviço que a
 * cobra. A ordem dos impedimentos segue a mesma lógica do {@code SellableLotLookup}: primeiro o que não
 * tem o que fazer hoje, depois o que é ação de alguém.
 */
public class ContainerShippingService implements ContainerShippingLookup {

    private final ContainerRepository containers;
    private final FillRepository fills;
    private final SellableLotLookup lots;

    public ContainerShippingService(ContainerRepository containers, FillRepository fills,
            SellableLotLookup lots) {
        this.containers = Objects.requireNonNull(containers);
        this.fills = Objects.requireNonNull(fills);
        this.lots = Objects.requireNonNull(lots);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ShippableContainer> shippable(UUID breweryId, UUID containerId) {
        return containers.find(breweryId, containerId).map(container -> {
            var conteudo = fills.currentOf(breweryId, containerId).orElse(null);
            if (conteudo == null) {
                // Carga é o que sai cheio. Um keg vazio na rota é engano de leitura de etiqueta.
                return new ShippableContainer(containerId, container.code(), null, null, false,
                        Optional.of(new Blocker("container_empty",
                                "O vasilhame está vazio. Encha antes de pôr na carga.")));
            }
            if (container.state() != ContainerState.FILLED) {
                // Já está na rua, no cliente ou na oficina: pô-lo numa carga prometeria o que não está
                // no depósito.
                return new ShippableContainer(containerId, container.code(), conteudo.lotCode(),
                        conteudo.volumeLiters(), false,
                        Optional.of(new Blocker("wrong_state",
                                "O vasilhame está em " + container.state() + " — só sai o que está "
                                        + "cheio e no depósito.")));
            }
            // LOTE QUE NÃO RESOLVE É BLOQUEIO, E NÃO SILÊNCIO. Antes disto o `orElse(null)` deixava o
            // bloqueio nulo, e `shippable = blocker == null` devolvia **true** sem motivo nenhum: um
            // vasilhame cujo lote sumiu de `packaging` era embarcado justamente pelo método que existe
            // para cobrar a assinatura da qualidade. `FillHandlers` já tratava o mesmo status ausente
            // como erro — as duas pontas discordavam, e a que errava era a da saída.
            var status = lots.statusOf(breweryId, conteudo.finishedLotId(),
                    LocalDate.now(ZoneOffset.UTC));
            if (status.isEmpty()) {
                return new ShippableContainer(containerId, container.code(), conteudo.lotCode(),
                        conteudo.volumeLiters(), false,
                        Optional.of(new Blocker("lot_not_found",
                                "O lote que está dentro deste vasilhame não foi encontrado. Sem ele "
                                        + "não há como saber se a qualidade liberou.")));
            }
            var blocker = status.orElseThrow().blocker()
                    .map(b -> new Blocker(b.code(), b.message())).orElse(null);
            return new ShippableContainer(containerId, container.code(), conteudo.lotCode(),
                    conteudo.volumeLiters(), blocker == null, Optional.ofNullable(blocker));
        });
    }
}
