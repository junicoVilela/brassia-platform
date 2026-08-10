package br.com.brew.brassia.optimization.domain;

import java.util.UUID;

/** Corrida que não existe nesta cervejaria. */
public final class UnknownOptimizationRunException extends RuntimeException {

    public UnknownOptimizationRunException(UUID runId) {
        super("corrida de otimização desconhecida: " + runId);
    }
}
