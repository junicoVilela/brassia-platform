package br.com.brew.brassia.forecast.application.service;

import br.com.brew.brassia.equipment.EquipmentSummaryLookup;
import br.com.brew.brassia.forecast.application.port.outbound.TankCycleRepository;
import br.com.brew.brassia.forecast.domain.ProductionCapacity;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * A capacidade do período, a partir do que a casa declarou (DUV-FCST-001).
 *
 * <p><strong>O tanque só entra na conta se existir e tiver capacidade.</strong> Um ciclo declarado para um
 * equipamento que foi apagado, ou que nunca teve volume, sairia da soma em silêncio se ninguém checasse —
 * e a capacidade cairia sem explicação.
 */
public class CapacityService {

    private final TankCycleRepository cycles;
    private final EquipmentSummaryLookup equipment;

    public CapacityService(TankCycleRepository cycles, EquipmentSummaryLookup equipment) {
        this.cycles = Objects.requireNonNull(cycles);
        this.equipment = Objects.requireNonNull(equipment);
    }

    @Transactional(readOnly = true)
    public ProductionCapacity of(UUID breweryId, YearMonth month) {
        var tanks = cycles.cycles(breweryId).stream()
                .map(c -> equipment.find(breweryId, c.equipmentId())
                        .map(e -> new ProductionCapacity.Tank(e.code(), e.capacityLiters(),
                                c.cycleDays()))
                        .orElse(null))
                .filter(Objects::nonNull)
                .toList();
        return new ProductionCapacity(tanks, month.lengthOfMonth());
    }

    @Transactional
    public void declare(UUID breweryId, UUID equipmentId, int cycleDays, String note, UUID actor) {
        // O equipamento precisa existir e ter volume: declarar ciclo para o que não existe criaria uma
        // linha que nunca entra na conta, e a casa acharia que declarou.
        equipment.find(breweryId, equipmentId)
                .filter(e -> e.capacityLiters() != null && e.capacityLiters().signum() > 0)
                .orElseThrow(() -> new IllegalArgumentException(
                        "equipamento não encontrado ou sem capacidade cadastrada"));
        cycles.save(breweryId, equipmentId, cycleDays, note, actor);
    }

    @Transactional
    public void remove(UUID breweryId, UUID equipmentId) {
        cycles.remove(breweryId, equipmentId);
    }

    /** O que a tela mostra: quanto cabe, quanto a demanda ocupa, e se falta. */
    public record CapacityView(boolean known, BigDecimal capacityLiters, BigDecimal demandLiters,
            Boolean fits, BigDecimal headroomLiters, BigDecimal utilizationPercent,
            java.util.List<String> tanks) {}
}
