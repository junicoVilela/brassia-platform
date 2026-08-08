package br.com.brew.brassia.ai.application.service;

import br.com.brew.brassia.ai.application.port.inbound.GatewayQueries;
import br.com.brew.brassia.ai.application.port.outbound.AiBudgetRepository;
import br.com.brew.brassia.ai.application.port.outbound.ModelInvocationLedger;
import br.com.brew.brassia.ai.application.port.outbound.ModelProvider;
import br.com.brew.brassia.ai.application.port.outbound.ModelProvider.ModelChoice;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Monta o retrato do gateway para quem opera (AIA-001). */
public final class GatewayStatusService implements GatewayQueries {

    /** Quantas chamadas recentes mostrar: o suficiente para ver um padrão, não um relatório. */
    private static final int RECENT_LIMIT = 20;

    private final ModelProvider provider;
    private final AiBudgetRepository budgets;
    private final ModelInvocationLedger ledger;

    public GatewayStatusService(ModelProvider provider, AiBudgetRepository budgets,
            ModelInvocationLedger ledger) {
        this.provider = Objects.requireNonNull(provider);
        this.budgets = Objects.requireNonNull(budgets);
        this.ledger = Objects.requireNonNull(ledger);
    }

    @Override
    public GatewayStatus of(UUID breweryId) {
        Objects.requireNonNull(breweryId, "breweryId");
        return new GatewayStatus(provider.name(), provider.enabled(),
                provider.chain().stream().map(ModelChoice::model).toList(), provider.timeout(),
                budgets.currentOf(breweryId), List.copyOf(ledger.recent(breweryId, RECENT_LIMIT)));
    }
}
