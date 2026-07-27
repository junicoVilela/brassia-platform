package br.com.brew.brassia.production.domain;

import java.util.List;

/**
 * Correções determinísticas disponíveis no dia de brassa (PRD-004): diluição,
 * concentração, correção de densidade por temperatura e ajuste de volume. Referem
 * calculadoras versionadas do motor (sais ficam para PRD-004-A). Só pré-visualiza
 * impacto — nada é aplicado sem confirmação (aplicação é CAL-002).
 */
public final class BrewCorrections {

    /** Ids de calculadora permitidos como correção de brassa, na ordem de exibição. */
    public static final List<String> IDS = List.of(
            "dilution-water", "concentration-boiloff", "hydrometer-temp-correction", "volume-topup");

    private BrewCorrections() {
    }

    public static boolean isCorrection(String calculatorId) {
        return IDS.contains(calculatorId);
    }
}
