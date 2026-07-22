package br.com.brew.brassia.security.application.port.outbound;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SegregationRuleRepository {
    record RuleView(UUID id, String leftPermissionCode, String rightPermissionCode, String reason, boolean active) {}

    UUID create(UUID breweryId, String left, String right, String reason);
    List<RuleView> listActive(UUID breweryId);
    Optional<RuleView> findById(UUID id);
}
