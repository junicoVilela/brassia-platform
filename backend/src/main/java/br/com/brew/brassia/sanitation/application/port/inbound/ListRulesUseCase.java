package br.com.brew.brassia.sanitation.application.port.inbound;

import br.com.brew.brassia.sanitation.domain.CompatibilityRule;
import java.util.List;
import java.util.UUID;

public interface ListRulesUseCase {
    List<CompatibilityRule> handle(UUID breweryId);
}
