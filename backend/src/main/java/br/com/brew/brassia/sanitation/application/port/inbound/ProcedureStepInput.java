package br.com.brew.brassia.sanitation.application.port.inbound;

import java.math.BigDecimal;

/** Entrada de uma etapa do POP (CLN-001), com campos tipados da ficha. */
public record ProcedureStepInput(int sequence, String method, String product, BigDecimal concentrationMinPct,
        BigDecimal concentrationMaxPct, BigDecimal tempMinC, BigDecimal tempMaxC, Integer timeMinutes, String flow,
        String ppe, String alternative, String prohibition, boolean evidenceRequired) {}
