package br.com.brew.brassia.ai.adapter.inbound.web;

import br.com.brew.brassia.ai.adapter.inbound.web.dto.AnswerDtos;
import br.com.brew.brassia.ai.adapter.inbound.web.dto.AssessmentDtos;
import br.com.brew.brassia.ai.application.port.inbound.AnswerCommands;
import br.com.brew.brassia.ai.application.port.inbound.AssessmentCommands;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * O copiloto respondendo com fonte (RAG-002).
 *
 * <p><strong>É POST porque gasta.</strong> Cada pergunta é uma chamada ao modelo, cobrada e registrada no
 * ledger. Um GET convidaria o navegador a repetir a pergunta ao recarregar a página, e cada recarga
 * custaria dinheiro.
 *
 * <p><strong>As permissões de quem pergunta vão para dentro da recuperação.</strong> Duas pessoas podem
 * receber respostas legitimamente diferentes para a mesma pergunta, porque o conjunto de fontes que cada
 * uma pode ver é diferente. Isso é a regra funcionando, não inconsistência.
 *
 * <p>{@code ai.answer.ask} é permissão própria, separada de consultar o gateway e de indexar documento:
 * perguntar ao copiloto gasta dinheiro e usa fontes, e nenhuma das outras duas coisas implica esta.
 */
@RestController
@RequestMapping("/api/v1/ai/copilot")
final class CopilotController {

    private final AnswerCommands answers;
    private final AssessmentCommands assessments;
    private final Clock clock;

    CopilotController(AnswerCommands answers, AssessmentCommands assessments) {
        this.answers = answers;
        this.assessments = assessments;
        this.clock = Clock.systemUTC();
    }

    @PostMapping("/ask")
    AnswerDtos.AnswerView ask(@AuthenticationPrincipal SecurityPrincipal principal,
            @Valid @RequestBody AnswerDtos.AskRequest request) {
        principal.requirePermission("ai.answer.ask");
        var question = new AnswerCommands.Question(principal.userId(), principal.requireBrewery(),
                principal.permissions(), request.question(),
                request.onDate() == null ? LocalDate.now(clock) : request.onDate(),
                request.equipmentId());
        return AnswerDtos.AnswerView.from(answers.ask(question));
    }

    /**
     * Avaliação de risco de um lote (AIA-002).
     *
     * <p>POST pelo mesmo motivo da pergunta: gasta. E alçada própria — {@code ai.assessment.batch} —
     * porque avaliar um lote lê custo, qualidade e produção dele; quem pode perguntar sobre um manual não
     * necessariamente pode ler o custo de um lote.
     */
    @PostMapping("/batches/{batchId}/assessment")
    AssessmentDtos.AssessmentView assess(@AuthenticationPrincipal SecurityPrincipal principal,
            @PathVariable UUID batchId) {
        principal.requirePermission("ai.assessment.batch");
        return AssessmentDtos.AssessmentView.from(
                assessments.assess(principal.userId(), principal.requireBrewery(), batchId));
    }
}
