package br.com.brew.brassia.packaging.domain;

import java.util.List;
import java.util.Objects;

/**
 * O rótulo não pode ser impresso: falta campo obrigatório. Os campos faltantes acompanham o erro
 * separados por causa — sem valor resolvido, ou exigido pela regra e sequer desenhado pelo layout —
 * porque a correção é diferente em cada caso.
 */
public final class LabelNotPrintableException extends RuntimeException {

    private final transient List<LabelField> missingRequired;
    private final transient List<LabelField> requiredNotDrawn;

    public LabelNotPrintableException(List<LabelField> missingRequired, List<LabelField> requiredNotDrawn) {
        super("rótulo incompleto");
        this.missingRequired = List.copyOf(Objects.requireNonNull(missingRequired));
        this.requiredNotDrawn = List.copyOf(Objects.requireNonNull(requiredNotDrawn));
    }

    /** Exigidos pela regra e desenhados no layout, mas sem valor resolvido de nenhuma fonte. */
    public List<LabelField> missingRequired() {
        return missingRequired;
    }

    /** Exigidos pela regra que o layout sequer desenha: o template precisa mudar. */
    public List<LabelField> requiredNotDrawn() {
        return requiredNotDrawn;
    }
}
