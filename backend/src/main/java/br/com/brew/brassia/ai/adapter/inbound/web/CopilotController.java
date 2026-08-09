package br.com.brew.brassia.ai.adapter.inbound.web;

import br.com.brew.brassia.ai.adapter.inbound.web.dto.AnswerDtos;
import br.com.brew.brassia.ai.application.port.inbound.AnswerCommands;
import br.com.brew.brassia.shared.security.SecurityPrincipal;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final Clock clock;

    CopilotController(AnswerCommands answers) {
        this.answers = answers;
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
}
