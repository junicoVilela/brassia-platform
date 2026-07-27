package br.com.brew.brassia.sanitation.adapter.inbound.web.dto;

import br.com.brew.brassia.sanitation.domain.CompatibilityRule;
import java.util.UUID;

public record RuleView(
        UUID id, String material, String soiling, String risk, String previousProduct, String procedureCode,
        String method, String alternative, String restriction) {

    public static RuleView from(CompatibilityRule r) {
        return new RuleView(r.id(), r.material().name(), r.soiling().name(), r.risk().name(), r.previousProduct(),
                r.procedureCode(), r.method(), r.alternative(), r.restriction());
    }
}
