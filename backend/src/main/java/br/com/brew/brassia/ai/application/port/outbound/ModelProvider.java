package br.com.brew.brassia.ai.application.port.outbound;

import br.com.brew.brassia.ai.domain.ModelPricing;
import br.com.brew.brassia.ai.domain.TokenUsage;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * A fronteira com o provedor de modelos (AIA-001).
 *
 * <p>A porta existe por três motivos concretos, não por simetria: o provedor é externo e pode falhar,
 * existe mais de uma implementação real de saída (um provedor de verdade e um desligado) e o caso de
 * uso precisa ser testável sem rede. É o critério de porta do projeto atendido três vezes.
 *
 * <p><strong>Devolve JSON cru, não objeto pronto.</strong> É de propósito: validar o contrato é
 * decisão de domínio, e se o provedor já entregasse o objeto tipado a recusa de resposta inválida
 * viraria detalhe do adapter — impossível de testar sem simular o provedor, e diferente em cada
 * implementação. Aqui o adapter transporta; quem julga é o caso de uso.
 */
public interface ModelProvider {

    /** Falso quando não há provedor configurado. Estado normal: o sistema opera sem IA. */
    boolean enabled();

    /** Identificação do provedor para o registro de custo, ex.: {@code anthropic}. */
    String name();

    /**
     * Os modelos a tentar, em ordem: o primeiro é o preferido, os seguintes são o fallback.
     *
     * <p>Ordem, não conjunto — fallback só significa alguma coisa se houver uma primeira escolha.
     * Vazia quando o provedor está desligado.
     */
    List<ModelChoice> chain();

    /** Prazo máximo de uma chamada. Serve para a borda declarar o que promete a quem espera. */
    Duration timeout();

    /**
     * Moeda em que este provedor cobra.
     *
     * <p>Existe separada do preço porque uma recusa antes da chamada — provedor desligado, orçamento
     * estourado — não tem modelo escolhido e portanto não tem preço, mas ainda precisa registrar uma
     * linha de custo zero em alguma moeda. Sem isto a moeda dessas linhas seria um palpite no código.
     */
    String currency();

    /**
     * Faz a chamada.
     *
     * @throws ProviderFailure quando o provedor recusa, erra ou estoura o prazo
     */
    Completion send(Call call);

    /** Um modelo e o preço dele. Preço junto do modelo porque cada modelo custa o seu. */
    record ModelChoice(String model, ModelPricing pricing) {
        public ModelChoice {
            Objects.requireNonNull(model, "model");
            Objects.requireNonNull(pricing, "pricing");
        }
    }

    /**
     * @param model          modelo desta tentativa
     * @param instruction    instrução do sistema, confiável
     * @param untrustedInput conteúdo sobre o qual raciocinar, não confiável
     * @param responseSchema JSON Schema que a resposta deve satisfazer
     * @param maxOutputTokens teto de saída
     */
    record Call(String model, String instruction, String untrustedInput, String responseSchema,
            int maxOutputTokens) {
    }

    /**
     * @param json  a resposta, ainda sem validação
     * @param usage tokens consumidos, para a conta
     */
    record Completion(String json, TokenUsage usage) {
        public Completion {
            Objects.requireNonNull(json, "json");
            Objects.requireNonNull(usage, "usage");
        }
    }

    /**
     * Falha do provedor, com os tokens que ele chegou a cobrar.
     *
     * <p>Carrega o consumo porque uma chamada pode falhar depois de gerar — e ter gerado é ter
     * custado. Zerado quando não houve geração nenhuma.
     */
    final class ProviderFailure extends RuntimeException {

        private final TokenUsage usage;

        public ProviderFailure(String message, TokenUsage usage, Throwable cause) {
            super(message, cause);
            this.usage = usage == null ? TokenUsage.NONE : usage;
        }

        public ProviderFailure(String message) {
            this(message, TokenUsage.NONE, null);
        }

        public TokenUsage usage() {
            return usage;
        }
    }
}
