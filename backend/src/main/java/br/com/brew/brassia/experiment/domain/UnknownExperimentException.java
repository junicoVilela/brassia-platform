package br.com.brew.brassia.experiment.domain;

import java.util.UUID;

/** Experimento que não existe nesta cervejaria. */
public final class UnknownExperimentException extends RuntimeException {

    public UnknownExperimentException(UUID experimentId) {
        super("experimento desconhecido: " + experimentId);
    }
}
