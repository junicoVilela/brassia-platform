package br.com.brew.brassia.ai.config;

import br.com.brew.brassia.ai.domain.ModelPricing;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do gateway de IA (AIA-001).
 *
 * <p><strong>Desligado é o default.</strong> Uma instalação nova não fala com provedor nenhum até que
 * alguém decida que fala, com que chave e sob que teto. Nenhuma cervejaria descobre que tem IA
 * habilitada — e uma conta — por causa de um default.
 *
 * <p><strong>A ordem de {@code models} é o fallback.</strong> O primeiro é o preferido; os seguintes
 * são tentados quando o provedor falha. Preço junto de cada um porque cada modelo custa o seu, e
 * porque preço muda por contrato: cravar no código daria uma conta errada silenciosa no dia da
 * mudança.
 *
 * <p><strong>{@code budgetZone} é débito declarado (DEB-AI-001).</strong> O mês do orçamento devia
 * virar no fuso da cervejaria, mas {@code BreweryRef} não expõe fuso e ampliar a API publicada de outro
 * módulo não pertence a esta história. Enquanto isso o mês vira num fuso configurado por instalação — o
 * erro se limita às horas de virada do mês. Remover quando {@code BreweryRef} passar a expor o fuso.
 */
@ConfigurationProperties("brassia.ai")
public record AiProperties(
        boolean enabled,
        String provider,
        String apiKey,
        String baseUrl,
        Duration timeout,
        String currency,
        BigDecimal monthlyBudget,
        String effort,
        boolean thinking,
        String budgetZone,
        List<ModelSpec> models) {

    public AiProperties {
        provider = blankTo(provider, "anthropic");
        baseUrl = blankTo(baseUrl, "https://api.anthropic.com");
        timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
        currency = blankTo(currency, "USD");
        monthlyBudget = monthlyBudget == null ? new BigDecimal("50.00") : monthlyBudget;
        effort = blankTo(effort, "low");
        budgetZone = blankTo(budgetZone, "America/Sao_Paulo");
        models = models == null ? List.of() : List.copyOf(models);

        // Habilitado sem chave ou sem modelo não é "quase pronto", é configuração pela metade: falharia
        // na primeira chamada, em produção, com uma mensagem de provedor. Falhar no boot é mais barato.
        if (enabled) {
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException(
                        "brassia.ai.enabled=true exige brassia.ai.api-key configurada");
            }
            if (models.isEmpty()) {
                throw new IllegalStateException(
                        "brassia.ai.enabled=true exige ao menos um modelo em brassia.ai.models");
            }
        }
    }

    /**
     * Um modelo e o preço dele, por milhão de tokens.
     *
     * @param id              identificador do modelo no provedor, ex.: {@code claude-opus-5}
     * @param inputPerMillion preço por milhão de tokens de entrada
     * @param outputPerMillion preço por milhão de tokens de saída
     */
    public record ModelSpec(String id, BigDecimal inputPerMillion, BigDecimal outputPerMillion) {

        public ModelSpec {
            if (id == null || id.isBlank()) {
                throw new IllegalStateException("cada modelo precisa de id");
            }
            if (inputPerMillion == null || outputPerMillion == null) {
                throw new IllegalStateException("o modelo " + id + " precisa de preço de entrada e saída");
            }
        }

        public ModelPricing pricingIn(String currency) {
            return new ModelPricing(inputPerMillion, outputPerMillion, currency);
        }
    }

    private static String blankTo(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
