package br.com.brew.brassia.ai.adapter.inbound.web.dto;

import br.com.brew.brassia.ai.application.port.inbound.AssessmentCommands.Assessment;
import br.com.brew.brassia.ai.application.port.inbound.AssessmentCommands.Risk;
import br.com.brew.brassia.ai.domain.Fact;
import java.math.BigDecimal;
import java.util.List;

/** Contratos HTTP da avaliação de lote (AIA-002). */
public final class AssessmentDtos {

    private AssessmentDtos() {
    }

    /**
     * A avaliação.
     *
     * <p>{@code facts} vem junto e não é anexo: é o que permite conferir cada afirmação sem sair da tela. Uma
     * avaliação sem os números ao lado pediria confiança; com eles, pede leitura.
     *
     * @param discarded afirmações descartadas por número inventado ou fato inexistente. Chega ao cliente de
     *                  propósito — é o sinal de que o modelo tentou calcular, e esconder deixaria uma
     *                  avaliação enfraquecida com a mesma aparência de uma íntegra.
     */
    public record AssessmentView(
            boolean usable,
            String summary,
            List<RiskView> risks,
            List<String> assumptions,
            List<FactView> facts,
            List<String> discarded) {

        public static AssessmentView from(Assessment assessment) {
            return new AssessmentView(assessment.usable(), assessment.summary(),
                    assessment.risks().stream().map(RiskView::from).toList(),
                    assessment.assumptions(),
                    assessment.facts().stream().map(FactView::from).toList(),
                    assessment.discarded());
        }
    }

    /**
     * @param severity juízo do modelo, não cálculo — a tela diz isso, para que ninguém leia como medição
     */
    public record RiskView(String statement, String severity, List<String> factRefs) {

        public static RiskView from(Risk risk) {
            return new RiskView(risk.statement(), risk.severity(), risk.factRefs());
        }
    }

    /**
     * Um número calculado pelo domínio.
     *
     * @param value  nulo quando o fato é ausência — e ausência não é zero
     * @param source o serviço que calculou. Viaja até a tela porque é o que faz "cálculos referenciam
     *               serviço de domínio" ser verificável por quem lê, e não uma promessa de arquitetura
     */
    public record FactView(String id, String label, BigDecimal value, String unit, String source,
            boolean available) {

        public static FactView from(Fact fact) {
            return new FactView(fact.id(), fact.label(), fact.value(), fact.unit(), fact.source(),
                    fact.known());
        }
    }
}
