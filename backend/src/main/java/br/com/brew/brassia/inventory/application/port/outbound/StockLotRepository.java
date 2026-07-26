package br.com.brew.brassia.inventory.application.port.outbound;

import br.com.brew.brassia.inventory.domain.StockLot;
import java.util.List;
import java.util.UUID;

public interface StockLotRepository {
    void insert(StockLot lot);

    List<StockLot> findAll(UUID breweryId);

    java.util.Optional<StockLot> findById(UUID breweryId, UUID lotId);

    /**
     * Trava o lote para atualização (lock pessimista) dentro da transação corrente,
     * serializando saídas concorrentes do mesmo lote (evita double spend). Retorna
     * o lote travado, ou vazio se inexistente.
     */
    java.util.Optional<StockLot> lockForUpdate(UUID breweryId, UUID lotId);

    /**
     * Lotes candidatos à reserva de um ingrediente, em ordem FEFO (validade mais
     * próxima primeiro), já travados (FOR UPDATE). Exclui lotes bloqueados e
     * vencidos ({@code expiry_date < today}). Serializa a alocação concorrente.
     */
    List<StockLot> lockCandidateLots(UUID breweryId, UUID ingredientId, java.time.LocalDate today);
}
