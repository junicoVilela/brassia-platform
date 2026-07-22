package br.com.brew.brassia.security.application.port.inbound;

import br.com.brew.brassia.security.application.port.outbound.SegregationRuleRepository;
import java.util.List;
import java.util.UUID;

public interface ManageSegregationUseCase {
    UUID createRule(CreateRuleCommand command);
    List<SegregationRuleRepository.RuleView> listRules(UUID breweryId);

    record CreateRuleCommand(UUID breweryId, UUID actorId, String leftPermissionCode,
            String rightPermissionCode, String reason) {}
}
