package br.com.brew.brassia.ai.adapter.outbound.provider;

import br.com.brew.brassia.ai.application.port.outbound.ModelProvider;
import br.com.brew.brassia.ai.config.AiProperties;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;

/**
 * Escolhe o provedor a partir da configuração (AIA-001).
 *
 * <p>A escolha é feita uma vez, no boot, e não a cada chamada: "há provedor?" é decisão de instalação,
 * não de requisição. O resto do sistema recebe sempre um {@link ModelProvider} — desligado é um deles.
 *
 * <p>O timeout entra na construção do cliente porque é ali que ele tem efeito: um prazo que só existisse
 * como número num relatório de status não protegeria ninguém de uma chamada pendurada.
 */
public final class ModelProviders {

    private ModelProviders() {
    }

    public static ModelProvider from(AiProperties properties) {
        if (!properties.enabled()) {
            return new DisabledModelProvider(properties.provider(), properties.timeout(),
                    properties.currency());
        }
        var client = AnthropicOkHttpClient.builder()
                .apiKey(properties.apiKey())
                .baseUrl(properties.baseUrl())
                .timeout(properties.timeout())
                .build();
        return new AnthropicModelProvider(client, properties);
    }
}
