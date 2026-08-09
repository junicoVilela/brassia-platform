package br.com.brew.brassia.digitaltwin.adapter.inbound.web;

import br.com.brew.brassia.digitaltwin.adapter.inbound.web.dto.ProfileDtos;
import br.com.brew.brassia.digitaltwin.application.port.inbound.ProfileCommands;
import br.com.brew.brassia.digitaltwin.application.port.inbound.ProfileQueries;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Perfil aprendido visto de fora (DTW-001). */
@RestController
@RequestMapping("/api/v1/digital-twin/profiles")
final class LearnedProfileController {

    private final ProfileCommands commands;
    private final ProfileQueries queries;

    LearnedProfileController(ProfileCommands commands, ProfileQueries queries) {
        this.commands = commands;
        this.queries = queries;
    }

    /**
     * Calcula uma versão nova do perfil.
     *
     * <p><strong>Alçada própria, separada da de ler.</strong> Calcular escolhe a amostra, e a amostra
     * decide o número — quem pode escolher quais lotes entram no perfil que vai guiar o planejamento tem
     * poder diferente de quem só consulta o resultado.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ProfileDtos.ProfileView compute(@AuthenticationPrincipal SecurityPrincipal principal,
            @Valid @RequestBody ProfileDtos.ComputeRequest request) {
        principal.requirePermission("digitaltwin.profile.compute");
        return ProfileDtos.ProfileView.from(commands.compute(new ProfileCommands.Request(
                principal.userId(), principal.requireBrewery(), request.recipeId(), request.batchIds())));
    }

    /**
     * A versão vigente do perfil de uma receita.
     *
     * <p>204 quando não há perfil nenhum. Um corpo vazio ou um perfil de zeros diria que a receita foi
     * analisada e não rendeu nada — que é diferente de nunca ter sido analisada.
     */
    @GetMapping("/{recipeId}")
    ResponseEntity<ProfileDtos.ProfileView> latest(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID recipeId) {
        principal.requirePermission("digitaltwin.profile.read");
        return queries.latest(principal.requireBrewery(), recipeId)
                .map(profile -> ResponseEntity.ok(ProfileDtos.ProfileView.from(profile)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** O histórico de versões — é onde se vê o perfil mudar conforme a operação muda. */
    @GetMapping("/{recipeId}/history")
    List<ProfileDtos.ProfileView> history(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID recipeId) {
        principal.requirePermission("digitaltwin.profile.read");
        return ProfileDtos.ProfileView.from(queries.history(principal.requireBrewery(), recipeId));
    }
}
