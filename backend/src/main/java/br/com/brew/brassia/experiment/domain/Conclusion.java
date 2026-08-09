package br.com.brew.brassia.experiment.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A leitura do experimento, com o que ela não pode afirmar (EXP-001).
 *
 * <p><strong>Não existe construtor que aceite uma lista de limitações vazia</strong>, e não existe caminho
 * em que quem conclui as forneça. Elas vêm de {@link Limitation#values()} aplicadas ao plano — o que torna
 * "concluir sem registrar limitações" algo que não se pode escrever, em vez de algo que se pede na revisão.
 *
 * @param supported se o observado é compatível com a hipótese. Não é "provado": um par de lotes não prova
 *                  nada, e o campo se chama assim para não virar certeza no relatório.
 */
public record Conclusion(
        boolean supported,
        String observation,
        List<Limitation> limitations,
        UUID concludedBy,
        Instant concludedAt) {

    public Conclusion {
        Objects.requireNonNull(observation, "observation");
        Objects.requireNonNull(concludedBy, "concludedBy");
        Objects.requireNonNull(concludedAt, "concludedAt");
        if (observation.trim().isEmpty()) {
            throw new IllegalArgumentException("a observação não pode ser vazia");
        }
        limitations = List.copyOf(Objects.requireNonNull(limitations, "limitations"));
        // Um lote dividido sempre tem ao menos SINGLE_PAIR. Lista vazia aqui significa que alguém
        // construiu a conclusão por fora do plano — e é exatamente o caminho que esta história fecha.
        if (limitations.isEmpty()) {
            throw new IllegalArgumentException("uma conclusão de lote dividido sempre carrega limitações");
        }
        observation = observation.trim();
    }
}
