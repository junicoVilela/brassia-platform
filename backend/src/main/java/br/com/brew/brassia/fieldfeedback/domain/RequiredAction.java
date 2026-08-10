package br.com.brew.brassia.fieldfeedback.domain;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * O que esta reclamação <strong>exige</strong> antes de poder ser encerrada (FLD-001).
 *
 * <p><strong>Derivada da severidade e da categoria, não escolhida.</strong> É a diferença entre um sistema
 * que sugere e um que impede: sugestão depende de alguém concordar no dia em que está com pressa, e o dia
 * em que se está com pressa é exatamente o dia em que uma reclamação de corpo estranho é encerrada como
 * "cliente contatado".
 */
public enum RequiredAction {

    /**
     * Quarentena do lote.
     *
     * <p>Exigida quando há risco à saúde: corpo estranho, alegação de doença ou severidade SAFETY. Se um
     * exemplar tem corpo estranho, os outros do mesmo lote são suspeitos até que se mostre o contrário —
     * e a hora de bloquear é antes de investigar, não depois.
     */
    QUARANTINE("Quarentenar o lote enquanto se investiga"),

    /**
     * Investigação de causa (CAPA).
     *
     * <p>Exigida quando o desvio sugere falha de processo. Um problema sistêmico tratado exemplar a
     * exemplar reaparece no lote seguinte, e o registro individual não deixa rastro de que reapareceu.
     */
    ROOT_CAUSE_ANALYSIS("Abrir investigação de causa (CAPA)");

    private final String description;

    RequiredAction(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    /**
     * O que este caso exige.
     *
     * <p>Categoria pesa junto com severidade porque as duas erram de formas diferentes: quem registra pode
     * classificar um corpo estranho como QUALITY por não querer alarmar, mas dificilmente vai escolher a
     * categoria errada. Uma exigência que dependesse só da severidade cairia junto com essa classificação.
     */
    public static List<RequiredAction> of(Severity severity, ComplaintCategory category) {
        var actions = EnumSet.noneOf(RequiredAction.class);
        if (severity == Severity.SAFETY || RISK_CATEGORIES.contains(category)) {
            actions.add(QUARANTINE);
            actions.add(ROOT_CAUSE_ANALYSIS);
        } else if (severity == Severity.SYSTEMIC) {
            actions.add(ROOT_CAUSE_ANALYSIS);
        }
        return List.copyOf(actions);
    }

    private static final Set<ComplaintCategory> RISK_CATEGORIES =
            Set.of(ComplaintCategory.FOREIGN_BODY, ComplaintCategory.ILLNESS);
}
