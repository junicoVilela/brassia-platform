package br.com.brew.brassia.recipe.adapter.inbound.web;

import br.com.brew.brassia.recipe.adapter.inbound.web.dto.AssistRequest;
import br.com.brew.brassia.recipe.domain.AttributeGuidance;
import br.com.brew.brassia.recipe.domain.FormulationAssistant;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Assistente de formulação (REC-009): compara metas com faixas alvo e devolve
 * orientações determinísticas. Somente orientação — não altera receita.
 */
@RestController
@RequestMapping("/api/v1/recipes/formulation")
final class FormulationController {

    private final FormulationAssistant assistant;

    FormulationController(FormulationAssistant assistant) {
        this.assistant = assistant;
    }

    @PostMapping("/assist")
    List<AttributeGuidance> assist(
            @RequestBody AssistRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal) {
        principal.requirePermission("recipe.read");
        return assistant.assess(request.targetsOrEmpty(), request.toRanges());
    }
}
