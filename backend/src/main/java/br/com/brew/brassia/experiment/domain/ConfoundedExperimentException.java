package br.com.brew.brassia.experiment.domain;

import java.util.List;

/**
 * Mais de um fator difere entre controle e variante (EXP-001).
 *
 * <p><strong>Recusar é o ponto da história.</strong> Com dois fatores diferentes, qualquer resultado tem
 * duas explicações e nenhuma delas pode ser descartada — o experimento produz uma conclusão que parece
 * conhecimento e não é. Aceitar o plano e "avisar depois" seria pior: o aviso se perde e o número fica.
 */
public final class ConfoundedExperimentException extends RuntimeException {

    private final List<String> differingFactors;

    ConfoundedExperimentException(List<String> differingFactors) {
        super("mais de um fator difere entre controle e variante: " + String.join(", ", differingFactors));
        this.differingFactors = List.copyOf(differingFactors);
    }

    public List<String> differingFactors() {
        return differingFactors;
    }
}
