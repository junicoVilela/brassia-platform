package br.com.brew.brassia.security.application.service;

import br.com.brew.brassia.security.application.port.outbound.EffectivePermissionsRepository;
import br.com.brew.brassia.security.application.port.outbound.GroupPermissionRepository;
import br.com.brew.brassia.security.application.port.outbound.SegregationRuleRepository;
import br.com.brew.brassia.security.domain.UserId;
import br.com.brew.brassia.shared.security.ForbiddenException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Verifica regras de segregação de funções antes de conceder grupo (SEC-013). */
public final class SegregationChecker {
    private final SegregationRuleRepository rules;
    private final EffectivePermissionsRepository effectivePermissions;
    private final GroupPermissionRepository groupPermissions;

    public SegregationChecker(
            SegregationRuleRepository rules,
            EffectivePermissionsRepository effectivePermissions,
            GroupPermissionRepository groupPermissions) {
        this.rules = Objects.requireNonNull(rules);
        this.effectivePermissions = Objects.requireNonNull(effectivePermissions);
        this.groupPermissions = Objects.requireNonNull(groupPermissions);
    }

    public void assertGrantAllowed(UUID breweryId, UserId userId, UUID groupId) {
        var userPerms = effectivePermissions.findByUserId(userId, breweryId);
        var groupPerms = groupPermissions.findPermissionCodesByGroupId(groupId);
        for (var rule : rules.listActive(breweryId)) {
            boolean userHasLeft = userPerms.contains(rule.leftPermissionCode());
            boolean userHasRight = userPerms.contains(rule.rightPermissionCode());
            boolean groupGrantsLeft = groupPerms.contains(rule.leftPermissionCode());
            boolean groupGrantsRight = groupPerms.contains(rule.rightPermissionCode());
            if ((userHasLeft && groupGrantsRight) || (userHasRight && groupGrantsLeft)) {
                throw new ForbiddenException("segregação de funções violada: " + rule.reason());
            }
        }
    }
}
