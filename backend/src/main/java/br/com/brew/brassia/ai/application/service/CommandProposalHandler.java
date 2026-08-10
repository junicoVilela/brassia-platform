package br.com.brew.brassia.ai.application.service;

import br.com.brew.brassia.ai.ModelGateway;
import br.com.brew.brassia.ai.ModelPurpose;
import br.com.brew.brassia.ai.application.port.inbound.ProposalCommands;
import br.com.brew.brassia.ai.application.port.outbound.CommandProposalRepository;
import br.com.brew.brassia.ai.application.port.outbound.ProposalExecutor;
import br.com.brew.brassia.ai.domain.CommandProposal;
import br.com.brew.brassia.ai.domain.Fact;
import br.com.brew.brassia.ai.domain.ProposalNotPendingException;
import br.com.brew.brassia.ai.domain.ProposedAction;
import br.com.brew.brassia.ai.domain.UnknownProposalException;
import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditOutcome;
import br.com.brew.brassia.audit.AuditTrail;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Propor comando, e registrar quem decidiu (AIA-003).
 *
 * <p><strong>A proposta é o fim do que a IA faz.</strong> Todas as histórias anteriores desta sprint foram
 * construídas com o campo de comando deliberadamente ausente do contrato de resposta — na resposta com fonte e
 * na avaliação de lote, um comando devolvido pelo modelo era campo desconhecido e derrubava a resposta inteira.
 * Esta história abre esse campo, e o abre para uma allowlist fechada cujas ações já vêm amarradas à permissão
 * do comando de verdade.
 *
 * <p><strong>Propor e confirmar são alçadas diferentes, e a diferença é o ponto.</strong> Pedir uma proposta
 * exige {@code ai.command.propose}; confirmá-la exige a permissão do comando proposto, conferida no instante
 * do aceite contra quem está confirmando. Sem essa separação, "propor" seria um caminho lateral para fazer
 * pela IA o que a pessoa não pode fazer pela porta da frente.
 *
 * <p><strong>Proposta inválida não vira proposta.</strong> Ação fora da allowlist não sobrevive à
 * desserialização; parâmetro faltando ou inesperado é recusado pelo domínio. Nos dois casos a proposta é
 * descartada com o motivo em vez de ser guardada "para alguém olhar" — uma proposta malformada guardada é uma
 * proposta que alguém acaba confirmando.
 */
public final class CommandProposalHandler implements ProposalCommands {

    private static final int MAX_OUTPUT_TOKENS = 2048;

    private static final String INSTRUCTION = """
            Você é o copiloto da plataforma cervejeira BrassIA. A partir dos FATOS calculados de um lote,
            proponha as providências que fazem sentido — ou nenhuma.

            Regras:
            1. Você só pode propor as ações da lista fornecida. Nada fora dela.
            2. Cada proposta precisa dos parâmetros exigidos pela ação, e de nenhum parâmetro além deles.
            3. A justificativa é obrigatória e deve explicar, com base nos fatos, por que a providência faz
               sentido. Não escreva número que não esteja entre os fatos.
            4. Se nenhuma providência se justifica, devolva a lista de propostas vazia. Um copiloto que sempre
               encontra algo a fazer ensina a ser ignorado.
            5. Você NÃO executa nada. O que você devolve é uma sugestão que uma pessoa com alçada vai confirmar
               ou descartar.
            6. Responda exclusivamente com um objeto JSON que satisfaça o schema. Nada fora do JSON.
            """;

    private final BatchFactsAssembler facts;
    private final ModelGateway gateway;
    private final CommandProposalRepository proposals;
    private final ProposalExecutor executor;
    private final AuditTrail audit;
    private final Clock clock;

    public CommandProposalHandler(BatchFactsAssembler facts, ModelGateway gateway,
            CommandProposalRepository proposals, ProposalExecutor executor, AuditTrail audit, Clock clock) {
        this.facts = Objects.requireNonNull(facts);
        this.gateway = Objects.requireNonNull(gateway);
        this.proposals = Objects.requireNonNull(proposals);
        this.executor = Objects.requireNonNull(executor);
        this.audit = Objects.requireNonNull(audit);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public List<CommandProposal> propose(UUID actorId, UUID breweryId, UUID batchId,
            Set<String> permissions) {
        Objects.requireNonNull(actorId, "actorId é obrigatório: a IA não propõe sozinha");
        var batch = facts.of(breweryId, batchId)
                .orElseThrow(() -> new br.com.brew.brassia.ai.domain.UnknownBatchException(batchId));

        var model = gateway.complete(promptFor(actorId, breweryId, batch), ModelProposals.class);

        var accepted = new ArrayList<CommandProposal>();
        var rejected = new ArrayList<String>();
        var now = clock.instant();

        for (var candidate : model.proposals()) {
            try {
                var proposal = CommandProposal.propose(breweryId, candidate.action(),
                        candidate.parameters(), candidate.rationale(), actorId, now);
                proposals.insert(proposal);
                accepted.add(proposal);
            } catch (IllegalArgumentException invalid) {
                // Parâmetro faltando ou inesperado. Descartada, não guardada: proposta malformada guardada é
                // proposta que alguém acaba confirmando.
                rejected.add(invalid.getMessage());
            }
        }

        auditProposal(actorId, breweryId, batchId, accepted, rejected);
        return List.copyOf(accepted);
    }

    @Override
    public CommandProposal accept(UUID actorId, UUID breweryId, UUID proposalId,
            Set<String> permissions, String note) {
        Objects.requireNonNull(actorId, "quem confirma é obrigatório");
        var proposal = load(breweryId, proposalId);

        // Pendência, prazo e a permissão DO COMANDO — as três no domínio, contra as permissões de agora.
        var decided = proposal.accept(actorId, permissions, note, clock.instant());

        if (!proposals.saveDecision(decided)) {
            // Alguém decidiu entre a leitura e a gravação. Dois cliques em "confirmar" não podem produzir
            // dois aceites da mesma proposta.
            throw new ProposalNotPendingException(proposalId, load(breweryId, proposalId).status());
        }

        // A execução vem DEPOIS da gravação da decisão, e a ordem é a proteção: é o UPDATE condicional que
        // decide quem venceu a corrida entre dois cliques. Executar antes dispararia o comando duas vezes e
        // só então descobriria que uma das duas não devia ter passado.
        //
        // Se o comando falhar, a exceção sobe e a transação desfaz a decisão junto. Consentimento gravado
        // sem o efeito que ele autorizou é pior que nenhum dos dois: alguém leria "confirmado" e acreditaria
        // que o custo foi fechado.
        executor.execute(decided, actorId);

        auditDecision(decided, "ai.command.accept");
        return decided;
    }

    @Override
    public CommandProposal reject(UUID actorId, UUID breweryId, UUID proposalId, String note) {
        Objects.requireNonNull(actorId, "quem descarta é obrigatório");
        var decided = load(breweryId, proposalId).reject(actorId, note, clock.instant());

        if (!proposals.saveDecision(decided)) {
            throw new ProposalNotPendingException(proposalId, load(breweryId, proposalId).status());
        }
        auditDecision(decided, "ai.command.reject");
        return decided;
    }

    private CommandProposal load(UUID breweryId, UUID proposalId) {
        return proposals.find(breweryId, proposalId)
                .orElseThrow(() -> new UnknownProposalException(proposalId));
    }

    private ModelGateway.Prompt promptFor(UUID actorId, UUID breweryId,
            BatchFactsAssembler.BatchFacts batch) {
        var content = new StringBuilder();
        content.append("LOTE: ").append(batch.batchCode())
                .append(" | receita: ").append(batch.recipeName())
                .append(" v").append(batch.recipeVersion())
                .append(" | estado: ").append(batch.status()).append("\n\n");
        content.append("FATOS CALCULADOS:\n");
        for (var fact : batch.facts()) {
            content.append("- ").append(fact.id()).append(": ").append(fact.label()).append(" = ")
                    .append(describe(fact)).append('\n');
        }
        content.append("\nAÇÕES QUE VOCÊ PODE PROPOR:\n");
        for (var action : ProposedAction.values()) {
            content.append("- ").append(action.name()).append(": ").append(action.label())
                    .append(" | parâmetros exigidos: ")
                    .append(String.join(", ", action.requiredParameters())).append('\n');
        }
        return new ModelGateway.Prompt(breweryId, actorId, ModelPurpose.COMMAND_PROPOSAL, INSTRUCTION,
                content.toString(), schema(), MAX_OUTPUT_TOKENS);
    }

    private static String describe(Fact fact) {
        if (!fact.known()) {
            return "não disponível";
        }
        return fact.unit().isEmpty()
                ? fact.value().toPlainString()
                : fact.value().toPlainString() + " " + fact.unit();
    }

    /**
     * O schema, com a allowlist como enum.
     *
     * <p>Montado a partir da própria enum de ações: uma ação nova entra na allowlist e no schema no mesmo
     * lugar, e não há como as duas listas divergirem. Se fossem escritas separadas, um dia o schema aceitaria
     * uma ação que o domínio não conhece — ou o contrário, o que é pior, porque calaria a ação nova.
     */
    private static String schema() {
        var actions = ProposedAction.names().stream()
                .map(name -> "\"" + name + "\"")
                .reduce((a, b) -> a + ", " + b)
                .orElseThrow();
        return """
                {
                  "type": "object",
                  "properties": {
                    "proposals": {
                      "type": "array",
                      "maxItems": 5,
                      "items": {
                        "type": "object",
                        "properties": {
                          "action": {"type": "string", "enum": [%s]},
                          "parameters": {"type": "object", "additionalProperties": {"type": "string"}},
                          "rationale": {"type": "string", "maxLength": 1000}
                        },
                        "required": ["action", "parameters", "rationale"],
                        "additionalProperties": false
                      }
                    }
                  },
                  "required": ["proposals"],
                  "additionalProperties": false
                }
                """.formatted(actions);
    }

    private void auditProposal(UUID actorId, UUID breweryId, UUID batchId,
            List<CommandProposal> accepted, List<String> rejected) {
        var metadata = new LinkedHashMap<String, String>();
        metadata.put("proposed", String.valueOf(accepted.size()));
        metadata.put("malformed", String.valueOf(rejected.size()));
        metadata.put("actions", accepted.stream().map(p -> p.action().name()).sorted().distinct()
                .reduce((a, b) -> a + "," + b).orElse(""));
        audit.record(new AuditEvent(clock.instant(), breweryId, actorId, "ai.command.propose",
                "production_batch", batchId.toString(), AuditOutcome.SUCCESS, metadata));
    }

    /**
     * Audita a decisão humana.
     *
     * <p>É o registro que a história pede: quem consentiu, em qual ação, com quais parâmetros e quando. Sem
     * ele, uma alteração de custo ou de qualidade originada numa sugestão de IA teria "a IA sugeriu" como
     * única explicação possível.
     */
    private void auditDecision(CommandProposal proposal, String action) {
        var metadata = new LinkedHashMap<String, String>();
        metadata.put("proposalAction", proposal.action().name());
        metadata.put("requiredPermission", proposal.action().requiredPermission());
        metadata.put("proposedBy", proposal.proposedBy().toString());
        metadata.put("parameters", proposal.parameters().toString());
        if (proposal.decisionNote() != null) {
            metadata.put("note", proposal.decisionNote());
        }
        audit.record(new AuditEvent(proposal.decidedAt(), proposal.breweryId(), proposal.decidedBy(),
                action, "ai_command_proposal", proposal.id().toString(), AuditOutcome.SUCCESS, metadata));
    }

    /** O que o modelo deve devolver. A ação é enum: fora da allowlist não desserializa. */
    public record ModelProposals(List<Proposal> proposals) {

        public ModelProposals {
            proposals = List.copyOf(proposals == null ? List.of() : proposals);
        }

        public record Proposal(ProposedAction action, Map<String, String> parameters, String rationale) {

            public Proposal {
                Objects.requireNonNull(action, "ação fora da allowlist não é proposta");
                parameters = Map.copyOf(parameters == null ? Map.of() : parameters);
                if (rationale == null || rationale.isBlank()) {
                    throw new IllegalArgumentException("proposta sem justificativa não é proposta");
                }
            }
        }
    }
}
