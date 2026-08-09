package br.com.brew.brassia.ai.application.service;

import br.com.brew.brassia.ai.ModelGateway;
import br.com.brew.brassia.ai.ModelPurpose;
import br.com.brew.brassia.ai.application.port.inbound.AssessmentCommands;
import br.com.brew.brassia.ai.domain.Fact;
import br.com.brew.brassia.ai.domain.FactGrounding;
import br.com.brew.brassia.ai.domain.UnknownBatchException;
import br.com.brew.brassia.audit.AuditEvent;
import br.com.brew.brassia.audit.AuditOutcome;
import br.com.brew.brassia.audit.AuditTrail;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Avaliar um lote: o domínio calcula, o modelo explica (AIA-002).
 *
 * <p><strong>A inversão é a história.</strong> O modelo não recebe o lote — recebe uma lista de números que o
 * domínio calculou, cada um com nome, unidade, origem e identificador. O que se pede dele é interpretação:
 * qual desses números indica risco, e por quê. Ele não soma, não divide e não estima, porque tudo que
 * precisaria ser somado, dividido ou estimado já veio pronto de quem responde por aquele cálculo.
 *
 * <p><strong>E o que o modelo escrever é conferido.</strong> Cada afirmação passa pela varredura de números:
 * um número que não corresponde a fato calculado derruba aquela afirmação. Não é desconfiança de estilo — é
 * que numa avaliação de lote a diferença entre 12% e 22% de perda é a diferença entre normal e investigar, e
 * quem lê não tem como distinguir sem ir conferir.
 *
 * <p><strong>Avaliação não é recomendação de comando.</strong> O schema não tem campo de ação, e é
 * deliberado: propor comando é AIA-003, com confirmação humana e nova autorização. Aqui o produto é
 * entendimento, não instrução.
 */
public final class BatchAssessmentHandler implements AssessmentCommands {

    /** Teto de saída: uma avaliação com riscos e ressalvas, não um laudo. */
    private static final int MAX_OUTPUT_TOKENS = 2048;

    /**
     * A instrução. A regra de número vem primeiro porque é a que não pode ser negociada.
     */
    private static final String INSTRUCTION = """
            Você é o copiloto da plataforma cervejeira BrassIA, avaliando um lote de produção.

            Você recebe FATOS já calculados pelos serviços de domínio do sistema. Regras:
            1. NÃO ESCREVA NENHUM NÚMERO que não esteja exatamente entre os valores dos fatos. Não some, não
               divida, não calcule porcentagem, não estime e não converta unidade. Se um número que você
               precisaria citar não está nos fatos, descreva a situação em palavras.
            2. Todo número que você escrever tem de vir de um fato que a PRÓPRIA afirmação cita em
               "factRefs". Um número certo citando o fato errado é recusado: se você escreve "perdeu 10 L",
               cite perda_transferencia, não volume_planejado.
            3. Afirmação sem "factRefs" NÃO PODE conter número nenhum — inclusive as suposições, que devem ser
               escritas só em palavras.
            4. Fato marcado como "não disponível" é ausência de informação, não zero. "Ninguém mediu" não é
               "tudo dentro da faixa"; "não houve transferência" não é "transferiu zero litro". Trate a
               ausência como risco de desconhecimento quando ela importar.
            5. O que você supôs e não está nos fatos vai em "assumptions".
            6. Não proponha comandos nem ações a executar no sistema. Descreva o risco; a decisão é humana.
            7. Responda exclusivamente com um objeto JSON que satisfaça o schema. Nada fora do JSON.
            """;

    private static final String SCHEMA = """
            {
              "type": "object",
              "properties": {
                "summary": {
                  "type": "object",
                  "properties": {
                    "text": {"type": "string", "maxLength": 2000},
                    "factRefs": {"type": "array", "maxItems": 8, "items": {"type": "string"}}
                  },
                  "required": ["text", "factRefs"],
                  "additionalProperties": false
                },
                "risks": {
                  "type": "array",
                  "maxItems": 10,
                  "items": {
                    "type": "object",
                    "properties": {
                      "statement": {"type": "string", "maxLength": 1000},
                      "severity": {"type": "string", "enum": ["LOW", "MEDIUM", "HIGH"]},
                      "factRefs": {"type": "array", "maxItems": 8, "items": {"type": "string"}}
                    },
                    "required": ["statement", "severity", "factRefs"],
                    "additionalProperties": false
                  }
                },
                "assumptions": {"type": "array", "maxItems": 8, "items": {"type": "string"}}
              },
              "required": ["summary", "risks", "assumptions"],
              "additionalProperties": false
            }
            """;

    private final BatchFactsAssembler facts;
    private final ModelGateway gateway;
    private final AuditTrail audit;
    private final Clock clock;

    public BatchAssessmentHandler(BatchFactsAssembler facts, ModelGateway gateway, AuditTrail audit,
            Clock clock) {
        this.facts = Objects.requireNonNull(facts);
        this.gateway = Objects.requireNonNull(gateway);
        this.audit = Objects.requireNonNull(audit);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public Assessment assess(UUID actorId, UUID breweryId, UUID batchId) {
        Objects.requireNonNull(actorId, "actorId é obrigatório: a IA não avalia sem quem pediu");
        var batch = facts.of(breweryId, batchId).orElseThrow(() -> new UnknownBatchException(batchId));

        var model = gateway.complete(promptFor(actorId, breweryId, batch), ModelAssessment.class);

        // Resumo e suposições passam pela mesma varredura que os riscos: um número inventado no resumo é tão
        // grave quanto num risco, e é o que a pessoa lê primeiro.
        var claims = new ArrayList<FactGrounding.Claim>();
        claims.add(new FactGrounding.Claim(model.summary().text(), model.summary().factRefs()));
        for (var risk : model.risks()) {
            claims.add(new FactGrounding.Claim(risk.statement(), risk.factRefs()));
        }
        for (var assumption : model.assumptions()) {
            claims.add(new FactGrounding.Claim(assumption, List.of()));
        }

        var verification = FactGrounding.verify(batch.facts(), claims);
        var accepted = verification.accepted();

        var summary = accepted.contains(model.summary().text()) ? model.summary().text() : "";
        var risks = model.risks().stream()
                .filter(risk -> accepted.contains(risk.statement()))
                .map(risk -> new Risk(risk.statement(), risk.severity(), risk.factRefs()))
                .toList();
        var assumptions = model.assumptions().stream().filter(accepted::contains).toList();

        var usable = !summary.isEmpty() || !risks.isEmpty();
        var assessment = new Assessment(usable, summary, risks, assumptions, batch.facts(),
                verification.rejected());
        auditOf(actorId, breweryId, batchId, assessment);
        return assessment;
    }

    /**
     * Monta o prompt.
     *
     * <p>Os fatos vão como lista rotulada, com identificador, valor, unidade e origem. A origem viaja porque
     * ela muda a leitura: "o motor de receita calculou 5,4% de ABV" e "alguém digitou 5,4%" são afirmações
     * diferentes sobre o mesmo número.
     */
    private ModelGateway.Prompt promptFor(UUID actorId, UUID breweryId,
            BatchFactsAssembler.BatchFacts batch) {
        var content = new StringBuilder();
        content.append("LOTE: ").append(batch.batchCode())
                .append(" | receita: ").append(batch.recipeName())
                .append(" v").append(batch.recipeVersion())
                .append(" | estado: ").append(batch.status()).append("\n\n");
        content.append("FATOS CALCULADOS (a única origem legítima de número):\n");
        for (var fact : batch.facts()) {
            content.append("- ").append(fact.id()).append(": ").append(fact.label()).append(" = ");
            if (fact.known()) {
                content.append(fact.value().toPlainString());
                if (!fact.unit().isEmpty()) {
                    content.append(' ').append(fact.unit());
                }
            } else {
                content.append("não disponível");
            }
            content.append(" [calculado por: ").append(fact.source()).append("]\n");
        }
        return new ModelGateway.Prompt(breweryId, actorId, ModelPurpose.BATCH_ASSESSMENT,
                INSTRUCTION, content.toString(), SCHEMA, MAX_OUTPUT_TOKENS);
    }

    private void auditOf(UUID actorId, UUID breweryId, UUID batchId, Assessment assessment) {
        var metadata = new LinkedHashMap<String, String>();
        metadata.put("usable", String.valueOf(assessment.usable()));
        metadata.put("facts", String.valueOf(assessment.facts().size()));
        metadata.put("risks", String.valueOf(assessment.risks().size()));
        // Afirmação descartada é o sinal mais direto de que o prompt está deixando o modelo calcular.
        metadata.put("discardedClaims", String.valueOf(assessment.discarded().size()));
        audit.record(new AuditEvent(clock.instant(), breweryId, actorId, "ai.assessment.batch",
                "production_batch", batchId.toString(),
                assessment.usable() ? AuditOutcome.SUCCESS : AuditOutcome.FAILURE, metadata));
    }

    /** O que o modelo deve devolver. Sem campo de ação: propor comando é AIA-003. */
    public record ModelAssessment(Summary summary, List<Risk> risks, List<String> assumptions) {

        public ModelAssessment {
            summary = summary == null ? new Summary("", List.of()) : summary;
            risks = List.copyOf(risks == null ? List.of() : risks);
            assumptions = List.copyOf(assumptions == null ? List.of() : assumptions);
        }

        /**
         * O resumo é uma afirmação como qualquer outra, e por isso declara os fatos que usa.
         *
         * <p>Não era assim na primeira versão, e um teste mostrou por que precisa ser: "o lote perdeu 45 L"
         * passava porque 45 era o IBU previsto da receita — número existente, assunto errado, afirmação errada
         * por 35 litros. O resumo é o que a pessoa lê primeiro; deixá-lo fora da amarração seria deixar de
         * fora justamente a frase mais lida.
         */
        public record Summary(String text, List<String> factRefs) {

            public Summary {
                text = text == null ? "" : text.strip();
                factRefs = List.copyOf(factRefs == null ? List.of() : factRefs);
            }
        }

        public record Risk(String statement, String severity, List<String> factRefs) {

            public Risk {
                if (statement == null || statement.isBlank()) {
                    throw new IllegalArgumentException("risco sem afirmação não é risco");
                }
                statement = statement.strip();
                if (severity == null || !severity.matches("LOW|MEDIUM|HIGH")) {
                    throw new IllegalArgumentException("severidade deve ser LOW, MEDIUM ou HIGH");
                }
                factRefs = List.copyOf(factRefs == null ? List.of() : factRefs);
            }
        }
    }
}
