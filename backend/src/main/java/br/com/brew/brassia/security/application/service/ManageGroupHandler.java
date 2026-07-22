package br.com.brew.brassia.security.application.service;

import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditTrail;
import br.com.brew.brassia.security.application.port.inbound.ManageGroupUseCase;
import br.com.brew.brassia.security.application.port.outbound.SecurityGroupRepository;
import br.com.brew.brassia.security.application.port.outbound.SecurityGroupRepository.GroupRecord;
import br.com.brew.brassia.security.application.port.outbound.SecurityGroupRepository.NewGroup;
import br.com.brew.brassia.shared.security.ForbiddenException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Administração de grupos customizados. Grupo de sistema não é alterável; o ator
 * só pode atribuir permissões que já possui (anti-autoelevação).
 */
public final class ManageGroupHandler implements ManageGroupUseCase {
    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{1,79}$");
    private static final String RESOURCE = "security_group";

    private final SecurityGroupRepository groups;
    private final AuditTrail audit;

    public ManageGroupHandler(SecurityGroupRepository groups, AuditTrail audit) {
        this.groups = Objects.requireNonNull(groups);
        this.audit = Objects.requireNonNull(audit);
    }

    @Override
    public Result create(CreateCommand command) {
        var code = normalizeCode(command.code());
        var name = requireName(command.name());
        var description = normalizeDescription(command.description());
        var permissionCodes = normalizePermissions(command.permissionCodes());
        assertAssignable(command.actorPermissions(), permissionCodes);
        if (groups.existsByCode(command.breweryId(), code)) {
            throw new IllegalStateException("código de grupo já existe nesta cervejaria");
        }
        var permissionIds = groups.resolveActivePermissionIds(permissionCodes);
        var id = groups.insert(new NewGroup(command.breweryId(), code, name, description));
        groups.replacePermissions(id, permissionIds);
        audit.record(AuditEvent.success(
                command.breweryId(), command.actorId(), "security.group.create", RESOURCE, id.toString(),
                Map.of("code", code, "permissions", String.join(",", permissionCodes))));
        return toResult(groups.findById(id).orElseThrow(), permissionCodes);
    }

    @Override
    public Result update(UpdateCommand command) {
        var group = groups.findById(command.groupId())
                .orElseThrow(() -> new IllegalArgumentException("grupo inexistente"));
        assertEditable(group, command.breweryId());
        var name = requireName(command.name());
        var description = normalizeDescription(command.description());
        var permissionCodes = normalizePermissions(command.permissionCodes());
        assertAssignable(command.actorPermissions(), permissionCodes);
        var permissionIds = groups.resolveActivePermissionIds(permissionCodes);
        if (!groups.update(group.id(), name, description, command.version())) {
            throw new IllegalStateException("versão do grupo divergiu");
        }
        groups.replacePermissions(group.id(), permissionIds);
        audit.record(AuditEvent.success(
                command.breweryId(), command.actorId(), "security.group.update", RESOURCE, group.id().toString(),
                Map.of("code", group.code(), "permissions", String.join(",", permissionCodes))));
        var refreshed = groups.findById(group.id()).orElseThrow();
        return toResult(refreshed, groups.permissionCodesOf(group.id()));
    }

    private static void assertEditable(GroupRecord group, UUID breweryId) {
        if (group.systemGroup()) {
            throw new ForbiddenException("grupo de sistema é imutável");
        }
        if (!breweryId.equals(group.breweryId())) {
            throw new ForbiddenException("grupo de outra cervejaria");
        }
        if (!group.active()) {
            throw new IllegalStateException("grupo inativo");
        }
    }

    private static void assertAssignable(Set<String> actorPermissions, List<String> requested) {
        for (String code : requested) {
            if (!actorPermissions.contains(code)) {
                throw new ForbiddenException("não pode conceder permissão que não possui");
            }
        }
    }

    private static String normalizeCode(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("código obrigatório");
        }
        var code = raw.trim().toUpperCase(Locale.ROOT);
        if (!CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("código inválido");
        }
        return code;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("nome obrigatório");
        }
        var trimmed = name.trim();
        if (trimmed.length() > 160) {
            throw new IllegalArgumentException("nome muito longo");
        }
        return trimmed;
    }

    private static String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        var trimmed = description.trim();
        if (trimmed.length() > 500) {
            throw new IllegalArgumentException("descrição muito longa");
        }
        return trimmed;
    }

    private static List<String> normalizePermissions(List<String> codes) {
        if (codes == null) {
            throw new IllegalArgumentException("permissões obrigatórias");
        }
        var unique = new LinkedHashSet<String>();
        for (String code : codes) {
            if (code == null || code.isBlank()) {
                throw new IllegalArgumentException("código de permissão inválido");
            }
            unique.add(code.trim());
        }
        return List.copyOf(unique);
    }

    private static Result toResult(GroupRecord group, List<String> permissions) {
        return new Result(
                group.id(), group.code(), group.name(), group.description(), group.breweryId(),
                group.systemGroup(), group.active(), group.version(), List.copyOf(permissions));
    }
}
