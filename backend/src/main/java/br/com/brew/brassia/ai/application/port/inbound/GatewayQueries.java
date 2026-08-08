package br.com.brew.brassia.ai.application.port.inbound;

import br.com.brew.brassia.ai.domain.AiBudget;
import br.com.brew.brassia.ai.domain.ModelInvocation;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * O estado do gateway, para quem opera (AIA-001).
 *
 * <p>Quem depende do copiloto precisa saber três coisas antes de confiar nele: se há provedor, quanto
 * do mês já foi e o que aconteceu nas últimas chamadas. Sem isso, "a IA não respondeu" é um mistério
 * em vez de um diagnóstico.
 */
public interface GatewayQueries {

    GatewayStatus of(UUID breweryId);

    /**
     * @param providerName  provedor configurado, ou {@code disabled}
     * @param enabled       se há provedor ativo — falso é estado normal, não incidente
     * @param models        modelos na ordem em que serão tentados; vazia quando desligado
     * @param timeout       prazo máximo de uma chamada
     * @param budget        teto do mês e quanto já foi
     * @param recent        últimas chamadas, sucesso e falha
     */
    record GatewayStatus(
            String providerName,
            boolean enabled,
            List<String> models,
            Duration timeout,
            AiBudget budget,
            List<ModelInvocation> recent) {
    }
}
