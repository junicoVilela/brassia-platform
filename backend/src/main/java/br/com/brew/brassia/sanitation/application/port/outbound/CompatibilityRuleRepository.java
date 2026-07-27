package br.com.brew.brassia.sanitation.application.port.outbound;

import br.com.brew.brassia.sanitation.domain.CompatibilityRule;
import br.com.brew.brassia.sanitation.domain.EquipmentMaterial;
import br.com.brew.brassia.sanitation.domain.RiskLevel;
import br.com.brew.brassia.sanitation.domain.SoilingLevel;
import java.util.List;
import java.util.UUID;

public interface CompatibilityRuleRepository {
    void insert(CompatibilityRule rule);

    boolean existsKey(UUID breweryId, EquipmentMaterial material, SoilingLevel soiling, RiskLevel risk,
            String previousProduct);

    List<CompatibilityRule> findAll(UUID breweryId);

    /** Candidatas por material/sujidade/risco (mesmo material — sem herança). */
    List<CompatibilityRule> findCandidates(UUID breweryId, EquipmentMaterial material, SoilingLevel soiling,
            RiskLevel risk);
}
