package br.com.brew.brassia.packaging.application.port.outbound;

import br.com.brew.brassia.packaging.domain.Shipment;
import java.util.List;
import java.util.UUID;

/** Persistência das expedições (TRC-001-D). */
public interface ShipmentRepository {

    void insert(Shipment shipment);

    List<Shipment> findByLot(UUID breweryId, UUID finishedLotId);

    List<Shipment> findAll(UUID breweryId);

    /**
     * Usada pelo recall via porta publicada: os destinos de um conjunto de lotes, de uma vez.
     *
     * <p><strong>Só expedições vivas.</strong> Uma estornada faria o recall comunicar um destino que
     * nunca recebeu nada — e a cobertura medida sobre ele mediria coisa nenhuma.
     */
    List<Shipment> findByLots(UUID breweryId, List<UUID> finishedLotIds);

    /** Unidades já expedidas do lote, líquidas de estorno — é contra elas que a próxima saída é conferida. */
    int shippedUnits(UUID breweryId, UUID finishedLotId);

    /** Carregada com a linha travada: dois estornos simultâneos não passam os dois. */
    java.util.Optional<Shipment> findForUpdate(UUID breweryId, UUID shipmentId);

    /** Grava só o estorno; destino, unidades e data não se editam — expedição é fato registrado. */
    void updateReversal(Shipment shipment);
}
