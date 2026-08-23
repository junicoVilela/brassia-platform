package br.com.brew.brassia.container.application.service;

import br.com.brew.brassia.container.application.port.outbound.ContainerRepository;
import br.com.brew.brassia.container.application.port.outbound.FillRepository;
import br.com.brew.brassia.container.domain.Container;
import br.com.brew.brassia.container.domain.ContainerFill;
import br.com.brew.brassia.container.domain.ContainerLocation;
import br.com.brew.brassia.container.domain.ContainerModifiedException;
import br.com.brew.brassia.container.domain.FillNotAllowedException;
import br.com.brew.brassia.container.domain.LocationKind;
import br.com.brew.brassia.container.domain.UnknownContainerException;
import br.com.brew.brassia.packaging.SellableLotLookup;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

/**
 * Conteúdo e posição do vasilhame (CON-002).
 *
 * <p><strong>Encher precede liberar.</strong> Kegs são enchidos na produção, antes de a qualidade
 * assinar — exigir a liberação aqui impediria a operação real de acontecer. Então o enchimento recusa
 * lote <em>vencido</em> ou <em>em quarentena</em>, e aceita lote ainda não liberado. Quem exige a
 * assinatura da qualidade é a saída da casa.
 */
public class FillHandlers {

    /**
     * Impedimentos que barram o enchimento.
     *
     * <p>Note quem <strong>não</strong> está aqui: {@code not_released} (a liberação vem depois do
     * envase) e {@code shelf_life_unknown} (a validade se apura com a evidência de oxigênio, também
     * depois). Barrar por esses dois pararia a linha por um motivo que só existe mais tarde.
     */
    private static final Set<String> BLOQUEIAM_ENCHIMENTO =
            Set.of("expired", "quarantined", "quarantine_suspected");

    private final ContainerRepository containers;
    private final FillRepository fills;
    private final SellableLotLookup lots;

    public FillHandlers(ContainerRepository containers, FillRepository fills,
            SellableLotLookup lots) {
        this.containers = Objects.requireNonNull(containers);
        this.fills = Objects.requireNonNull(fills);
        this.lots = Objects.requireNonNull(lots);
    }

    /**
     * Enche o vasilhame com um lote acabado.
     *
     * <p>Duas recusas diferentes, e a distinção importa: o <em>vasilhame</em> pode não estar apto
     * (avaria, inspeção vencida, estado errado) e o <em>líquido</em> pode não poder entrar (lote vencido,
     * quarentena, volume maior que o contêiner). Misturá-las daria ao operador uma mensagem que não diz o
     * que trocar.
     */
    @Transactional
    public UUID fill(UUID breweryId, UUID containerId, UUID finishedLotId, BigDecimal volumeLiters,
            UUID actor) {
        var container = require(breweryId, containerId);
        var now = Instant.now();
        // Primeiro o vasilhame: se ele não pode receber nada, o lote nem precisa ser consultado.
        container.requireFillableAt(now);
        if (volumeLiters.compareTo(container.nominalCapacityLiters()) > 0) {
            throw FillNotAllowedException.overCapacity();
        }

        // Lote desconhecido é recusa do CONTEÚDO, e não do vasilhame (DEB-CON-003 #7). Devolver
        // `container_not_found` dizia ao operador que o keg na mão dele não existe, quando o keg está
        // ótimo e o problema é o número do lote que ele digitou — as duas famílias de recusa existem
        // justamente para não mandar trocar a coisa errada.
        var status = lots.statusOf(breweryId, finishedLotId, LocalDate.now(ZoneOffset.UTC))
                .orElseThrow(() -> FillNotAllowedException.lot("lot_not_found",
                        "O lote informado não foi encontrado. Sem ele não há o que registrar dentro "
                                + "deste vasilhame."));
        status.blocker().filter(b -> BLOQUEIAM_ENCHIMENTO.contains(b.code()))
                .ifPresent(b -> {
                    throw FillNotAllowedException.lot(b.code(), b.message());
                });

        var fill = ContainerFill.record(UUID.randomUUID(), containerId, finishedLotId, status.code(),
                volumeLiters, now, actor);
        fills.record(fill);
        container.fill(now);
        // A MESMA CONFERÊNCIA DA ESCRITA (DEB-CON-003 #1). Engolir o `false` aqui deixava a transação
        // commitar com a linha de conteúdo gravada e o vasilhame ainda em EMPTY: o keg dizia estar vazio
        // e o histórico dizia ter cerveja dentro. O próximo enchimento legítimo passaria pelo
        // `requireFillableAt` e bateria no índice único parcial, virando 500 no lugar de uma recusa.
        if (!containers.update(container)) {
            throw new ContainerModifiedException(containerId);
        }
        return fill.id();
    }

    /**
     * O conteúdo saiu.
     *
     * <p>Não apaga: fecha o período. É esse intervalo que liga o keg entregue ao lote recolhido — e ele
     * continua respondendo por março depois de o vasilhame já ter passado por abril.
     */
    @Transactional
    public void empty(UUID breweryId, UUID containerId) {
        var container = require(breweryId, containerId);
        fills.empty(breweryId, containerId, Instant.now());
        // E O VASILHAME ACOMPANHA (DEB-CON-003 #2). Fechar o período do conteúdo sem mover o keg o deixava
        // preso em FILLED: não podia ser enchido nem liberado, e o único jeito de tirá-lo de lá era forjar
        // uma viagem inteira ao cliente. Quem foi esvaziado na rua não se move — isso o agregado decide.
        var antes = container.state();
        container.emptyContents();
        if (container.state() != antes && !containers.update(container)) {
            throw new ContainerModifiedException(containerId);
        }
    }

    @Transactional
    public void locate(UUID breweryId, UUID containerId, LocationKind kind, String place) {
        require(breweryId, containerId);
        fills.locate(ContainerLocation.at(containerId, kind, place, Instant.now()));
    }

    @Transactional(readOnly = true)
    public List<ContainerFill> history(UUID breweryId, UUID containerId) {
        require(breweryId, containerId);
        return fills.historyOf(breweryId, containerId);
    }

    @Transactional(readOnly = true)
    public List<ContainerLocation> locations(UUID breweryId, UUID containerId) {
        require(breweryId, containerId);
        return fills.locationsOf(breweryId, containerId);
    }

    private Container require(UUID breweryId, UUID containerId) {
        return containers.find(breweryId, containerId)
                .orElseThrow(() -> new UnknownContainerException(containerId));
    }
}
