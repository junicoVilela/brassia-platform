package br.com.brew.brassia.experiment.domain;

/**
 * Os lotes escolhidos não servem para o experimento (EXP-001).
 *
 * <p>Um lote de outra receita como "controle" faria a comparação medir a diferença entre duas receitas e
 * atribuí-la ao fator isolado — um resultado errado que parece certo.
 */
public final class InvalidExperimentSubjectException extends RuntimeException {

    public InvalidExperimentSubjectException(String message) {
        super(message);
    }
}
