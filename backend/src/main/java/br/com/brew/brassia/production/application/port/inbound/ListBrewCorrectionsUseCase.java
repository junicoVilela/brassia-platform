package br.com.brew.brassia.production.application.port.inbound;

import br.com.brew.brassia.calculator.CalculatorEngine;
import java.util.List;

/** Lista as correções determinísticas disponíveis no dia de brassa (PRD-004). */
public interface ListBrewCorrectionsUseCase {
    List<CalculatorEngine.CalculatorInfo> handle();
}
