package br.com.brew.brassia.gas.application.port.outbound;

import br.com.brew.brassia.gas.domain.GasConnection;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GasConnectionRepository {

    void insert(GasConnection connection);

    Optional<GasConnection> findById(UUID breweryId, UUID connectionId);

    /** Carrega a conexão travando a linha (FOR UPDATE) para comandos concorrentes. */
    Optional<GasConnection> findForUpdate(UUID breweryId, UUID connectionId);

    /** Conexões da cervejaria; {@code onlyOpen} exclui as desconectadas. */
    List<GasConnection> findAll(UUID breweryId, boolean onlyOpen);

    boolean update(GasConnection connection, long expectedVersion);

    /** Já existe conexão aberta neste ponto de uso? Um ponto recebe um cilindro por vez. */
    boolean hasOpenConnectionAtPoint(UUID breweryId, UUID pointOfUseEquipmentId);

    /**
     * Conexão aberta no ponto de uso, quando existe. O teto de pressão dela é limite físico real
     * para o balanceamento da linha de serviço (GAS-002).
     */
    Optional<GasConnection> findOpenConnectionAtPoint(UUID breweryId, UUID pointOfUseEquipmentId);

    /** Leitura de pressão: evidência, nunca reescrita. */
    void insertPressureReading(UUID id, UUID breweryId, UUID connectionId, BigDecimal bar, BigDecimal tempC,
            boolean overPressure, UUID actorId, Instant at);

    List<PressureReadingRow> findPressureReadings(UUID breweryId, UUID connectionId);

    /** Consumo de gás da conexão, em massa. */
    void insertConsumption(UUID id, UUID breweryId, UUID connectionId, UUID cylinderId, BigDecimal kg,
            String reason, UUID actorId, Instant at);

    List<ConsumptionRow> findConsumption(UUID breweryId, UUID connectionId);

    record PressureReadingRow(UUID id, BigDecimal bar, BigDecimal tempC, boolean overPressure, Instant at) {}

    record ConsumptionRow(UUID id, BigDecimal kg, String reason, Instant at) {}
}
