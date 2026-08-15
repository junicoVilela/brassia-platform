package br.com.brew.brassia.costing.application.port.outbound;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * O custo da hora de trabalho da casa (CST-001-A).
 *
 * <p>Vazio é estado legítimo e comum: uma cervejaria que nunca definiu a taxa não tem mão de obra no
 * custo, e o custeio diz isso como lacuna em vez de somar zero.
 */
public interface LaborRateRepository {

    Optional<BigDecimal> find(UUID breweryId);

    void save(UUID breweryId, BigDecimal costPerHour, UUID actorId);
}
