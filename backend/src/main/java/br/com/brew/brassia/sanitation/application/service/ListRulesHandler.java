package br.com.brew.brassia.sanitation.application.service;

import br.com.brew.brassia.sanitation.application.port.inbound.ListRulesUseCase;
import br.com.brew.brassia.sanitation.application.port.outbound.CompatibilityRuleRepository;
import br.com.brew.brassia.sanitation.domain.CompatibilityRule;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ListRulesHandler implements ListRulesUseCase {

    private final CompatibilityRuleRepository rules;

    public ListRulesHandler(CompatibilityRuleRepository rules) {
        this.rules = Objects.requireNonNull(rules);
    }

    @Override
    public List<CompatibilityRule> handle(UUID breweryId) {
        return rules.findAll(breweryId);
    }
}
