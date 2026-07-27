package br.com.brew.brassia.production.application.service;

import br.com.brew.brassia.calculator.CalculatorEngine;
import br.com.brew.brassia.production.application.port.inbound.ListBrewCorrectionsUseCase;
import br.com.brew.brassia.production.domain.BrewCorrections;
import java.util.Objects;

public final class ListBrewCorrectionsHandler implements ListBrewCorrectionsUseCase {

    private final CalculatorEngine engine;

    public ListBrewCorrectionsHandler(CalculatorEngine engine) {
        this.engine = Objects.requireNonNull(engine);
    }

    @Override
    public java.util.List<CalculatorEngine.CalculatorInfo> handle() {
        // Ordena conforme BrewCorrections.IDS, filtrando o catálogo do motor.
        return BrewCorrections.IDS.stream()
                .map(id -> engine.catalog().stream().filter(c -> c.id().equals(id)).findFirst().orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }
}
