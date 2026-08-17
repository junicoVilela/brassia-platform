package br.com.brew.brassia.distribution.application.service;

import br.com.brew.brassia.distribution.application.port.outbound.SyncRepository;
import br.com.brew.brassia.distribution.domain.CoarseLocation;
import br.com.brew.brassia.distribution.domain.ConsentedMedia;
import br.com.brew.brassia.distribution.domain.DeliveryNotRecordableException;
import br.com.brew.brassia.distribution.domain.DeliveryOutcome;
import br.com.brew.brassia.distribution.domain.OfflineOperation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A fila que o aparelho acumulou offline (MOB-001).
 *
 * <p><strong>Cada operação entra na própria transação.</strong> Uma delas em conflito não pode desfazer as
 * outras cinco que já entraram — o entregador ficaria com o dia inteiro por sincronizar por causa de uma
 * parada que o escritório tocou.
 *
 * <p><strong>A ordem é a do aparelho</strong>, e não a dos pacotes que chegaram pela rede: aplicar fora
 * dela entregaria antes de despachar.
 */
public class SyncHandlers {

    private final SyncRepository operations;
    private final DeliveryHandlers deliveries;

    public SyncHandlers(SyncRepository operations, DeliveryHandlers deliveries) {
        this.operations = Objects.requireNonNull(operations);
        this.deliveries = Objects.requireNonNull(deliveries);
    }

    /** Sincroniza um lote, e devolve o desfecho de cada item — inclusive os que não entraram. */
    public List<OfflineOperation> sync(UUID breweryId, UUID deviceId, UUID actor,
            List<PendingOperation> batch) {
        var resultado = new ArrayList<OfflineOperation>();
        var emOrdem = batch.stream().sorted(Comparator.comparingInt(PendingOperation::sequence))
                .toList();
        for (var pendente : emOrdem) {
            resultado.add(apply(breweryId, deviceId, actor, pendente));
        }
        return resultado;
    }

    /**
     * Aplica uma operação.
     *
     * <p>Transação própria: ver {@link SyncHandlers}. E a resposta ao reenvio vem <strong>antes</strong>
     * de qualquer tentativa de gravar — é isso que faz o retry automático do aplicativo, enquanto o sinal
     * vai e volta, devolver o mesmo resultado em vez de uma segunda entrega.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public OfflineOperation apply(UUID breweryId, UUID deviceId, UUID actor,
            PendingOperation pendente) {
        var jaProcessada = operations.find(breweryId, deviceId, pendente.clientOperationId());
        if (jaProcessada.isPresent()) {
            var anterior = jaProcessada.get();
            var duplicata = OfflineOperation.received(pendente.clientOperationId(), deviceId,
                    pendente.loadId(), pendente.stopId(), pendente.occurredAt(), Instant.now(),
                    pendente.sequence());
            duplicata.duplicateOf(anterior.resultId().orElse(null));
            // Não grava de novo: a linha da primeira vez é a verdade, e esta resposta só fecha o item
            // na tela do aparelho.
            return duplicata;
        }

        var operacao = OfflineOperation.received(pendente.clientOperationId(), deviceId,
                pendente.loadId(), pendente.stopId(), pendente.occurredAt(), Instant.now(),
                pendente.sequence());
        try {
            var provaId = deliveries.record(breweryId, pendente.loadId(), pendente.stopId(),
                    pendente.outcome(), pendente.occurredAt(), actor, pendente.delivered(),
                    pendente.collected(), pendente.note(), pendente.media(), pendente.location());
            operacao.applied(provaId);
        } catch (DeliveryNotRecordableException recusa) {
            if ("already_recorded".equals(recusa.reasonCode())) {
                // CONFLITO, e não erro: alguém registrou esta parada enquanto o aparelho estava sem
                // sinal. Sobrescrever descartaria em silêncio o registro de quem estava lá — ou o do
                // escritório —, então a decisão fica com gente.
                operacao.conflicted("Esta parada já foi registrada no servidor. " + recusa.getMessage());
            } else {
                operacao.rejected(recusa.getMessage());
            }
        } catch (RuntimeException erro) {
            // Recusada com o motivo, e não perdida: o que o aparelho tentou registrar é parte do que
            // aconteceu naquele dia.
            operacao.rejected(mensagem(erro));
        }
        operations.record(breweryId, operacao);
        return operacao;
    }

    @Transactional(readOnly = true)
    public List<OfflineOperation> conflicts(UUID breweryId) {
        return operations.conflicts(breweryId);
    }

    @Transactional(readOnly = true)
    public List<OfflineOperation> ofLoad(UUID breweryId, UUID loadId) {
        return operations.ofLoad(breweryId, loadId);
    }

    private static String mensagem(RuntimeException erro) {
        var texto = erro.getMessage();
        return texto == null || texto.isBlank() ? erro.getClass().getSimpleName() : texto;
    }

    /**
     * Uma operação como o aparelho a registrou.
     *
     * @param occurredAt a hora do <strong>aparelho</strong> — quando a cerveja desceu, e não quando o
     *                   pacote chegou
     */
    public record PendingOperation(UUID clientOperationId, int sequence, UUID loadId, UUID stopId,
            DeliveryOutcome outcome, Instant occurredAt, List<UUID> delivered, List<UUID> collected,
            String note, ConsentedMedia media, CoarseLocation location) {}
}
