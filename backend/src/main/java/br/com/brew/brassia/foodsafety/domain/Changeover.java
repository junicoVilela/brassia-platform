package br.com.brew.brassia.foodsafety.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * A regra da troca de produto (FDS-001): o que precisa sair do equipamento antes que o próximo
 * produto entre, e o que prova que saiu.
 *
 * <p>Função pura sobre os três eixos da matriz — o que o ingrediente declara, se o equipamento é
 * compartilhado e o que a limpeza remove. Nada aqui lê banco, e é o que permite testar o caso do
 * "POP liberado que remove o alergênico errado" sem montar uma cervejaria inteira.
 *
 * <p><strong>A carga residual é a diferença, não o conjunto anterior.</strong> Se o lote que sai e
 * o que entra têm o mesmo alergênico, não há troca a fazer: exigir limpeza ali seria transformar
 * segurança de alimentos em burocracia, e um POP exigido sem motivo é um POP que se aprende a
 * ignorar.
 */
public final class Changeover {

    private Changeover() {
    }

    /**
     * @param incoming    perfil do produto que vai entrar no equipamento
     * @param previous    perfil do último produto que ocupou o equipamento; {@code null} se nunca
     *                    houve uso — equipamento novo não tem o que trocar
     * @param previousUse quando o uso anterior começou; {@code null} junto com {@code previous}
     * @param dedication  dedicação declarada do equipamento; {@code null} é equipamento compartilhado
     * @param evidence    a última liberação de limpeza e o que aquele POP declara remover
     * @param at          instante de referência (início planejado do uso que entra)
     */
    public static ChangeoverVerdict assess(AllergenProfile incoming, AllergenProfile previous, Instant previousUse,
            EquipmentDedication dedication, CleaningEvidence evidence, Instant at) {
        Objects.requireNonNull(incoming, "perfil do produto que entra");
        Objects.requireNonNull(at, "instante de referência");

        if (dedication != null) {
            if (!incoming.complete()) {
                return ChangeoverVerdict.undeclared(
                        "Há ingrediente sem declaração de alergênico no produto que vai entrar, e o "
                                + "equipamento é dedicado; sem a declaração não dá para dizer se ele cabe ali.",
                        incoming.gaps());
            }
            var rejected = dedication.rejected(incoming);
            if (rejected.isEmpty()) {
                return ChangeoverVerdict.allowed(ChangeoverVerdict.Outcome.DEDICATED,
                        "Equipamento dedicado que comporta o perfil do produto.", incoming.allergens());
            }
            // Dedicação não se resolve com limpeza: a garantia é o alergênico nunca ter entrado.
            return ChangeoverVerdict.blocked(ChangeoverVerdict.Outcome.DEDICATION_VIOLATED,
                    "O equipamento é dedicado e não admite " + join(rejected)
                            + "; use outro equipamento — limpeza não substitui dedicação.",
                    rejected);
        }

        // Sem uso anterior não há troca a avaliar, e a lacuna de declaração não muda isso: a
        // ignorância só bloqueia onde a resposta mudaria o veredito. Quem guarda a verdade do que
        // a cerveja contém é o rótulo (PKG-004), que continua sem imprimir o campo sem declaração.
        if (previous == null) {
            return ChangeoverVerdict.allowed(ChangeoverVerdict.Outcome.CLEAR,
                    "Não há uso anterior no equipamento: nada a trocar.", incoming.allergens());
        }

        if (!incoming.complete()) {
            return ChangeoverVerdict.undeclared(
                    "Há ingrediente sem declaração de alergênico no produto que vai entrar; "
                            + "sem a declaração não é possível afirmar que a troca é segura.",
                    incoming.gaps());
        }

        if (!previous.complete()) {
            return ChangeoverVerdict.undeclared(
                    "Há ingrediente sem declaração de alergênico no produto anterior deste equipamento; "
                            + "sem saber o que ficou, não é possível afirmar que a troca é segura.",
                    previous.gaps());
        }

        var residue = previous.residueAgainst(incoming);
        if (residue.isEmpty()) {
            return ChangeoverVerdict.allowed(ChangeoverVerdict.Outcome.CLEAR,
                    "O produto anterior não deixa alergênico que este não aceite.", incoming.allergens());
        }

        if (evidence == null) {
            return ChangeoverVerdict.blocked(ChangeoverVerdict.Outcome.CHANGEOVER_REQUIRED,
                    "A troca exige limpeza liberada com POP que remova " + join(residue) + ".", residue);
        }
        if (previousUse != null && !evidence.releasedAt().isAfter(previousUse)) {
            // Liberação anterior ao uso é evidência do estado antigo: ela não viu o alergênico entrar.
            return ChangeoverVerdict.blocked(ChangeoverVerdict.Outcome.CHANGEOVER_REQUIRED,
                    "A última limpeza liberada é anterior ao uso que deixou " + join(residue)
                            + "; ela não comprova a troca.", residue);
        }
        if (evidence.releasedAt().isAfter(at)) {
            return ChangeoverVerdict.blocked(ChangeoverVerdict.Outcome.CHANGEOVER_REQUIRED,
                    "A limpeza que removeria " + join(residue) + " foi liberada depois do início planejado.",
                    residue);
        }

        var pending = new TreeSet<>(residue);
        pending.removeAll(evidence.removes());
        if (pending.isEmpty()) {
            return ChangeoverVerdict.allowed(ChangeoverVerdict.Outcome.CLEANED,
                    "O POP " + evidence.procedureCode() + " liberado remove " + join(residue) + ".",
                    incoming.allergens());
        }
        return ChangeoverVerdict.blocked(ChangeoverVerdict.Outcome.CHANGEOVER_REQUIRED,
                "O POP " + evidence.procedureCode() + " liberado não declara remover " + join(pending) + ".",
                pending);
    }

    private static String join(Set<AllergenCode> codes) {
        return codes.stream().map(AllergenCode::value).reduce((a, b) -> a + ", " + b).orElse("");
    }

    /**
     * A limpeza que existe e o que ela declara remover.
     *
     * @param procedureCode POP do ciclo liberado
     * @param releasedAt    quando o ciclo foi liberado
     * @param removes       alergênicos que a casa declara que aquele POP remove — vazio é um POP
     *                      que limpa sujidade e não responde por alergênico, que é o padrão até
     *                      alguém afirmar o contrário
     */
    public record CleaningEvidence(String procedureCode, Instant releasedAt, Set<AllergenCode> removes) {
        public CleaningEvidence {
            Objects.requireNonNull(procedureCode, "POP do ciclo liberado");
            Objects.requireNonNull(releasedAt, "instante da liberação");
            removes = removes == null ? Set.of() : Set.copyOf(removes);
        }
    }
}
