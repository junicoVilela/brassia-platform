package br.com.brew.brassia.ai.application.service;

import br.com.brew.brassia.ai.application.port.inbound.BudgetCommands;
import br.com.brew.brassia.ai.application.port.outbound.AiBudgetRepository;
import br.com.brew.brassia.ai.domain.AiBudget;
import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Redefinição do teto de gasto com IA (AIA-001). */
public final class BudgetHandler implements BudgetCommands {

    private final AiBudgetRepository budgets;
    private final AuditTrail audit;
    private final Clock clock;

    public BudgetHandler(AiBudgetRepository budgets, AuditTrail audit, Clock clock) {
        this.budgets = Objects.requireNonNull(budgets);
        this.audit = Objects.requireNonNull(audit);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public AiBudget redefine(UUID actorId, UUID breweryId, BigDecimal monthlyLimit, long expectedVersion) {
        var current = budgets.currentOf(breweryId);
        var saved = budgets.save(current.redefine(monthlyLimit, actorId, clock.instant()), expectedVersion);

        audit.record(AuditEvent.success(breweryId, actorId, "ai.budget.redefine", "ai_model_budget",
                breweryId.toString(),
                Map.of("previousLimit", current.monthlyLimit().toPlainString(),
                        "newLimit", saved.monthlyLimit().toPlainString(),
                        "currency", saved.currency())));
        return saved;
    }
}
