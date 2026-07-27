package br.com.brew.brassia.sanitation.application.service;

import br.com.brew.brassia.sanitation.application.port.inbound.RecommendUseCase;
import br.com.brew.brassia.sanitation.application.port.outbound.CompatibilityRuleRepository;
import br.com.brew.brassia.sanitation.domain.CompatibilityRule;
import br.com.brew.brassia.sanitation.domain.EquipmentMaterial;
import br.com.brew.brassia.sanitation.domain.RiskLevel;
import br.com.brew.brassia.sanitation.domain.SoilingLevel;
import java.util.Objects;

/**
 * Recomenda o método/POP (CLN-002) por correspondência exata de material — sem
 * herança (madeira/plástico não herdam inox). Entre as candidatas (material +
 * sujidade + risco), prefere a regra específica do produto anterior; senão, a
 * genérica (produto anterior nulo). Sem candidata → sem recomendação.
 */
public final class RecommendHandler implements RecommendUseCase {

    private final CompatibilityRuleRepository rules;

    public RecommendHandler(CompatibilityRuleRepository rules) {
        this.rules = Objects.requireNonNull(rules);
    }

    @Override
    public CompatibilityRule handle(Command command) {
        var material = EquipmentMaterial.of(command.material());
        var soiling = SoilingLevel.of(command.soiling());
        var risk = RiskLevel.of(command.risk());
        var previous = CompatibilityRule.normalize(command.previousProduct());

        var candidates = rules.findCandidates(command.breweryId(), material, soiling, risk);
        CompatibilityRule specific = null;
        CompatibilityRule generic = null;
        for (var rule : candidates) {
            if (previous != null && previous.equals(rule.previousProduct())) {
                specific = rule;
            } else if (rule.previousProduct() == null) {
                generic = rule;
            }
        }
        var match = specific != null ? specific : generic;
        if (match == null) {
            throw new IllegalArgumentException(
                    "sem recomendação para o material/contexto (não há herança de material)");
        }
        return match;
    }
}
