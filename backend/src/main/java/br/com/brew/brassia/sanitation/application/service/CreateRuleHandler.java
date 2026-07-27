package br.com.brew.brassia.sanitation.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.sanitation.application.port.inbound.CreateRuleUseCase;
import br.com.brew.brassia.sanitation.application.port.outbound.CompatibilityRuleRepository;
import br.com.brew.brassia.sanitation.application.port.outbound.ProcedureRepository;
import br.com.brew.brassia.sanitation.domain.CompatibilityRule;
import br.com.brew.brassia.sanitation.domain.EquipmentMaterial;
import br.com.brew.brassia.sanitation.domain.RiskLevel;
import br.com.brew.brassia.sanitation.domain.SoilingLevel;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Cadastra uma regra da matriz (CLN-002). Valida vocabulários, unicidade da chave
 * (material+sujidade+risco+produto anterior) e, se informado, que o POP referenciado
 * está publicado. Audita.
 */
public final class CreateRuleHandler implements CreateRuleUseCase {

    private final CompatibilityRuleRepository rules;
    private final ProcedureRepository procedures;
    private final AuditTrail audit;

    public CreateRuleHandler(CompatibilityRuleRepository rules, ProcedureRepository procedures, AuditTrail audit) {
        this.rules = Objects.requireNonNull(rules);
        this.procedures = Objects.requireNonNull(procedures);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public UUID handle(Command command) {
        var material = EquipmentMaterial.of(command.material());
        var soiling = SoilingLevel.of(command.soiling());
        var risk = RiskLevel.of(command.risk());
        var previous = CompatibilityRule.normalize(command.previousProduct());

        if (command.procedureCode() != null && !command.procedureCode().isBlank()
                && !procedures.existsPublishedByCode(command.breweryId(), command.procedureCode().trim())) {
            throw new IllegalArgumentException("POP referenciado não está publicado: " + command.procedureCode());
        }
        if (rules.existsKey(command.breweryId(), material, soiling, risk, previous)) {
            throw new IllegalStateException("já existe regra para esta combinação (material/sujidade/risco/produto)");
        }

        var rule = CompatibilityRule.create(command.breweryId(), material, soiling, risk, command.previousProduct(),
                command.procedureCode(), command.method(), command.alternative(), command.restriction());
        rules.insert(rule);

        audit.record(AuditEvent.success(command.breweryId(), command.actorId(), "sanitation.matrix.create",
                "sanitation.matrix", rule.id().toString(),
                Map.of("material", material.name(), "soiling", soiling.name(), "risk", risk.name())));

        return rule.id();
    }
}
