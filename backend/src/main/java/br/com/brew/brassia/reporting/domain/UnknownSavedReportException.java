package br.com.brew.brassia.reporting.domain;

import java.util.UUID;

/** Relatório salvo ou execução inexistente nesta cervejaria. */
public class UnknownSavedReportException extends RuntimeException {

    private final UUID id;

    public UnknownSavedReportException(UUID id) {
        super("relatório salvo inexistente nesta cervejaria: " + id);
        this.id = id;
    }

    public UUID id() {
        return id;
    }
}
