package br.com.brew.brassia.foodsafety.adapter.inbound.web;

import br.com.brew.brassia.foodsafety.adapter.inbound.web.dto.AllergenDtos;
import br.com.brew.brassia.foodsafety.application.port.inbound.AllergenCommands;
import br.com.brew.brassia.foodsafety.application.port.inbound.AllergenQueries;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Matriz de alergênicos (FDS-001): o vocabulário da casa e os três eixos que ele cruza —
 * ingrediente, equipamento e POP de limpeza.
 *
 * <p>Declarar é comando de alçada (permissão {@code foodsafety.allergen.write}, marcada como
 * crítica); ler exige apenas {@code foodsafety.allergen.read}, porque a matriz precisa estar à
 * vista de quem opera a linha.
 */
@RestController
@RequestMapping("/api/v1/food-safety")
final class AllergenController {

    private final AllergenQueries queries;
    private final AllergenCommands.RegisterAllergen registerAllergen;
    private final AllergenCommands.DeclareIngredient declareIngredient;
    private final AllergenCommands.DeclareDedication declareDedication;
    private final AllergenCommands.DeclareProcedureEffectiveness declareProcedure;

    AllergenController(AllergenQueries queries, AllergenCommands.RegisterAllergen registerAllergen,
            AllergenCommands.DeclareIngredient declareIngredient,
            AllergenCommands.DeclareDedication declareDedication,
            AllergenCommands.DeclareProcedureEffectiveness declareProcedure) {
        this.queries = queries;
        this.registerAllergen = registerAllergen;
        this.declareIngredient = declareIngredient;
        this.declareDedication = declareDedication;
        this.declareProcedure = declareProcedure;
    }

    @GetMapping("/allergens")
    List<AllergenDtos.AllergenView> allergens(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("foodsafety.allergen.read");
        return AllergenDtos.AllergenView.from(queries.allergens(principal.requireBrewery()));
    }

    @PostMapping("/allergens")
    @ResponseStatus(HttpStatus.CREATED)
    AllergenDtos.AllergenView register(@AuthenticationPrincipal SecurityPrincipal principal,
            @Valid @RequestBody AllergenDtos.RegisterAllergenRequest request) {
        principal.requirePermission("foodsafety.allergen.write");
        return AllergenDtos.AllergenView.from(registerAllergen.handle(principal.userId(),
                principal.requireBrewery(), request.code(), request.name()));
    }

    @GetMapping("/matrix")
    AllergenDtos.MatrixView matrix(@AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("foodsafety.allergen.read");
        return AllergenDtos.MatrixView.from(queries.matrix(principal.requireBrewery()));
    }

    @PutMapping("/ingredients/{ingredientId}/allergens")
    void declareIngredient(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID ingredientId, @RequestBody AllergenDtos.DeclareRequest request) {
        principal.requirePermission("foodsafety.allergen.write");
        declareIngredient.handle(principal.userId(), principal.requireBrewery(), ingredientId,
                codes(request));
    }

    /** Declarar dedicação com lista vazia é a linha livre de alergênicos, e é intencional. */
    @PutMapping("/equipment/{equipmentId}/dedication")
    void dedicate(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID equipmentId,
            @RequestBody AllergenDtos.DeclareRequest request) {
        principal.requirePermission("foodsafety.allergen.write");
        declareDedication.handle(principal.userId(), principal.requireBrewery(), equipmentId, codes(request));
    }

    /** Remover a dedicação devolve o equipamento ao compartilhado, onde a troca volta a ser checada. */
    @DeleteMapping("/equipment/{equipmentId}/dedication")
    void share(@AuthenticationPrincipal SecurityPrincipal principal, @PathVariable UUID equipmentId) {
        principal.requirePermission("foodsafety.allergen.write");
        declareDedication.handle(principal.userId(), principal.requireBrewery(), equipmentId, null);
    }

    @PutMapping("/procedures/{procedureCode}/allergens")
    void declareProcedure(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable String procedureCode, @RequestBody AllergenDtos.DeclareRequest request) {
        principal.requirePermission("foodsafety.allergen.write");
        declareProcedure.handle(principal.userId(), principal.requireBrewery(), procedureCode, codes(request));
    }

    @GetMapping("/batches/{batchId}/allergens")
    AllergenDtos.ProfileView batchProfile(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID batchId) {
        principal.requirePermission("foodsafety.allergen.read");
        return AllergenDtos.ProfileView.from(queries.batchProfile(principal.requireBrewery(), batchId));
    }

    /**
     * Simula a troca antes de agendá-la. O envase já recusa a reserva insegura por conta própria
     * (PKG-001); esta consulta existe para que se possa perguntar <em>antes</em>, que é quando ainda
     * dá para escolher outra linha ou outro POP.
     */
    @GetMapping("/changeover")
    AllergenDtos.ChangeoverView changeover(@AuthenticationPrincipal SecurityPrincipal principal,
            @RequestParam UUID equipmentId, @RequestParam UUID incomingBatchId,
            @RequestParam(required = false) UUID previousBatchId,
            @RequestParam(required = false) Instant previousUseAt,
            @RequestParam(required = false) Instant at) {
        principal.requirePermission("foodsafety.allergen.read");
        return AllergenDtos.ChangeoverView.from(queries.changeover(principal.requireBrewery(), equipmentId,
                incomingBatchId, previousBatchId, previousUseAt, at == null ? Instant.now() : at));
    }

    /** {@code null} no corpo é ausência de lista; distinguir de lista vazia importa na dedicação. */
    private static java.util.Set<String> codes(AllergenDtos.DeclareRequest request) {
        return request == null || request.allergens() == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(request.allergens());
    }
}
