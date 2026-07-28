package br.com.brew.brassia.fermentation.application.port.inbound;

import java.math.BigDecimal;

/** Entrada de um estágio do perfil (FER-001); validação no domínio. */
public record StageInput(int sequence, String name, BigDecimal targetTempC, Integer rampHours,
        BigDecimal pressurePsi, String condition, Integer conditionDays, BigDecimal targetGravity,
        boolean requiresConfirmation) {}
