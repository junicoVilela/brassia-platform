package br.com.brew.brassia.production;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Porta que o dia de brassa usa para transformar a reserva da OP em consumo (TRC-001-C), declarada
 * aqui por inversão de dependência: o estoque a implementa, mantendo o sentido inventory →
 * production e evitando que a produção conheça lotes ou ledger.
 *
 * <p><strong>Por que isto existe.</strong> Até aqui, a única ligação entre insumo e lote era a
 * reserva — que registra o que foi <em>separado</em> para a ordem, não o que foi ao moinho. Num
 * recall, tratar intenção como fato é recolher o lote errado; num custo, é somar o preço do lote
 * errado. A reserva vira fato quando alguém que estava lá confirma o que usou.
 *
 * <p>O gateway <strong>propõe</strong> a reserva e registra o que foi confirmado. O padrão é a
 * proposta — na maioria dos dias a brassagem usa o que foi separado —, mas o brewer que trocou de
 * lote porque o reservado acabou consegue dizer isso, e é essa divergência que o registro existe
 * para capturar.
 */
public interface ProductionStockGateway {

    /** O que a OP tem reservado hoje, lote a lote: a proposta de consumo. */
    List<ReservedLot> reservedFor(UUID breweryId, UUID orderId);

    /** Se a OP já teve consumo registrado — é o que impede lançar duas vezes. */
    boolean alreadyConsumed(UUID breweryId, UUID orderId);

    /**
     * Converte a reserva em consumo, all-or-nothing: ou o dia de brassa inteiro é registrado, ou
     * nada é. Um consumo pela metade daria um custo pela metade e uma genealogia pela metade.
     */
    Outcome consume(UUID breweryId, UUID orderId, UUID actorId, List<ConsumedLot> lines);

    /**
     * @param supplierLotCode o lote do fornecedor, que é como o operador reconhece o saco no chão
     * @param reserved        quanto a OP segura deste lote, na unidade do lote
     */
    record ReservedLot(UUID lotId, UUID ingredientId, String ingredientName, String supplierLotCode,
            BigDecimal reserved, String unit) {}

    record ConsumedLot(UUID lotId, BigDecimal quantity, String unit) {}

    /**
     * @param consumed   falso quando algum lote não tinha o quanto foi declarado; nada foi consumido
     * @param shortfalls o que faltou, lote a lote
     */
    record Outcome(boolean consumed, List<Shortfall> shortfalls) {}

    record Shortfall(UUID lotId, BigDecimal requested, BigDecimal available, String unit) {}
}
