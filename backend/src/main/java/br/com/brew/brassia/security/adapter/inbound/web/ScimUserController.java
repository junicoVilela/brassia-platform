package br.com.brew.brassia.security.adapter.inbound.web;

import br.com.brew.brassia.security.application.port.inbound.ScimProvisioningUseCase;
import br.com.brew.brassia.shared.security.ServicePrincipal;
import br.com.brew.brassia.shared.web.ProblemDetails;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scim/v2")
final class ScimUserController {
    private final ScimProvisioningUseCase scim;

    ScimUserController(ScimProvisioningUseCase scim) {
        this.scim = scim;
    }

    @PostMapping("/Users")
    ResponseEntity<Map<String, Object>> createUser(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal ServicePrincipal principal) {
        principal.requireScope("scim.users.write");
        var result = scim.createUser(new ScimProvisioningUseCase.CreateUserCommand(
                principal.breweryId(),
                null,
                string(body.get("externalId")),
                string(body.get("userName")),
                string(body.getOrDefault("displayName", body.get("userName"))),
                body.get("active") == null || Boolean.TRUE.equals(body.get("active")),
                idempotencyKey,
                ProblemDetails.currentTraceId()));
        return ResponseEntity.status(201).body(result);
    }

    @GetMapping("/Users/{id}")
    Map<String, Object> getUser(@PathVariable UUID id, @AuthenticationPrincipal ServicePrincipal principal) {
        principal.requireScope("scim.users.read");
        return scim.getUser(new ScimProvisioningUseCase.GetUserCommand(id));
    }

    @PatchMapping("/Users/{id}")
    ResponseEntity<Void> patchUser(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal ServicePrincipal principal) {
        principal.requireScope("scim.users.write");
        var active = body.get("active") == null || Boolean.TRUE.equals(body.get("active"));
        scim.patchUser(new ScimProvisioningUseCase.PatchUserCommand(id, active, idempotencyKey, ProblemDetails.currentTraceId()));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/Users/{id}")
    ResponseEntity<Void> deleteUser(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal ServicePrincipal principal) {
        principal.requireScope("scim.users.write");
        scim.deleteUser(new ScimProvisioningUseCase.DeleteUserCommand(id, idempotencyKey, ProblemDetails.currentTraceId()));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/ServiceProviderConfig")
    Map<String, Object> serviceProviderConfig() {
        return Map.of("schemas", new String[] {"urn:ietf:params:scim:schemas:core:2.0:ServiceProviderConfig"},
                "patch", Map.of("supported", true),
                "authenticationSchemes", new Object[] {});
    }

    @PostMapping("/Groups")
    ResponseEntity<Map<String, Object>> createGroup(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal ServicePrincipal principal) {
        principal.requireScope("scim.users.write");
        var result = scim.createGroup(new ScimProvisioningUseCase.CreateGroupCommand(
                null,
                string(body.get("externalId")),
                string(body.getOrDefault("displayName", "group")),
                idempotencyKey));
        return ResponseEntity.status(201).body(result);
    }

    @GetMapping("/Groups/{externalId}")
    Map<String, Object> getGroup(
            @PathVariable String externalId,
            @AuthenticationPrincipal ServicePrincipal principal) {
        principal.requireScope("scim.users.read");
        return scim.getGroup(new ScimProvisioningUseCase.GetGroupCommand(null, externalId));
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }
}
