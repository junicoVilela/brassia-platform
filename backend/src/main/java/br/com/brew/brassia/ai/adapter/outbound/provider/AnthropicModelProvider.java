package br.com.brew.brassia.ai.adapter.outbound.provider;

import br.com.brew.brassia.ai.application.port.outbound.ModelProvider;
import br.com.brew.brassia.ai.config.AiProperties;
import br.com.brew.brassia.ai.domain.TokenUsage;
import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.anthropic.models.messages.ThinkingConfigDisabled;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * O provedor real, via SDK oficial da Anthropic (AIA-001).
 *
 * <p><strong>Este adapter transporta; não julga.</strong> Ele pede a saída no formato, converte o
 * resultado para JSON cru e consumo de tokens, e traduz qualquer problema para {@link ProviderFailure}.
 * Validar contrato, contar custo e decidir fallback é do caso de uso — se essa decisão morasse aqui,
 * cada provedor novo traria a sua versão dela.
 *
 * <p><strong>Duas barreiras para a forma da resposta.</strong> Aqui o schema vai no
 * {@code output_config.format}, para que o provedor já restrinja o que gera; do lado do caso de uso a
 * resposta é validada de novo. A primeira barreira é promessa de terceiro, e promessa de terceiro não é
 * invariante nossa.
 *
 * <p><strong>Recusa por política é falha, não resposta.</strong> Quando o modelo declina
 * ({@code stop_reason: refusal}) não existe conteúdo para validar; tratar como falha faz o gateway cair
 * para o modelo seguinte, que é a providência certa. Truncamento por teto de tokens também é falha: uma
 * resposta cortada no meio é JSON inválido, e chamá-la de resposta só empurraria o erro para frente.
 *
 * <p><strong>Raciocínio desligado por default.</strong> Uma chamada que só precisa devolver um objeto
 * pequeno não precisa pensar, e {@code maxOutputTokens} é teto de saída <em>somada</em> — com raciocínio
 * ligado o teto pedido para a resposta seria consumido pensando, e a resposta chegaria truncada. As
 * histórias que precisarem de raciocínio ligam {@code brassia.ai.thinking} e sobem o teto junto.
 */
final class AnthropicModelProvider implements ModelProvider {

    private static final ObjectMapper SCHEMA_MAPPER = new ObjectMapper();

    private final AnthropicClient client;
    private final AiProperties properties;
    private final List<ModelChoice> chain;

    AnthropicModelProvider(AnthropicClient client, AiProperties properties) {
        this.client = client;
        this.properties = properties;
        this.chain = properties.models().stream()
                .map(spec -> new ModelChoice(spec.id(), spec.pricingIn(properties.currency())))
                .toList();
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public String name() {
        return properties.provider();
    }

    @Override
    public List<ModelChoice> chain() {
        return chain;
    }

    @Override
    public Duration timeout() {
        return properties.timeout();
    }

    @Override
    public String currency() {
        return properties.currency();
    }

    @Override
    public Completion send(Call call) {
        Message message;
        try {
            message = client.messages().create(paramsFor(call));
        } catch (AnthropicServiceException failure) {
            // A mensagem do provedor pode ecoar o prompt; só o tipo do erro atravessa.
            throw new ProviderFailure("o provedor de IA recusou a chamada ("
                    + failure.getClass().getSimpleName() + ")", TokenUsage.NONE, failure);
        } catch (RuntimeException failure) {
            throw new ProviderFailure("falha de comunicação com o provedor de IA ("
                    + failure.getClass().getSimpleName() + ")", TokenUsage.NONE, failure);
        }

        var usage = new TokenUsage(message.usage().inputTokens(), message.usage().outputTokens());
        var stopReason = message.stopReason().map(Object::toString).orElse("");

        if ("refusal".equalsIgnoreCase(stopReason)) {
            throw new ProviderFailure("o modelo declinou a chamada por política de segurança", usage, null);
        }
        if ("max_tokens".equalsIgnoreCase(stopReason)) {
            throw new ProviderFailure(
                    "a resposta do modelo foi truncada pelo teto de tokens", usage, null);
        }

        var json = message.content().stream()
                .flatMap(block -> textOf(block).stream())
                .reduce("", String::concat)
                .trim();
        if (json.isEmpty()) {
            throw new ProviderFailure("o modelo respondeu sem conteúdo textual", usage, null);
        }
        return new Completion(json, usage);
    }

    private MessageCreateParams paramsFor(Call call) {
        var output = OutputConfig.builder()
                .effort(OutputConfig.Effort.of(properties.effort()))
                .format(JsonOutputFormat.builder().schema(schemaOf(call.responseSchema())).build())
                .build();

        var params = MessageCreateParams.builder()
                .model(call.model())
                .maxTokens(call.maxOutputTokens())
                .system(call.instruction())
                .addUserMessage(call.untrustedInput() == null ? "" : call.untrustedInput())
                .outputConfig(output);

        if (properties.thinking()) {
            params.thinking(ThinkingConfigAdaptive.builder().build());
        } else {
            params.thinking(ThinkingConfigDisabled.builder().build());
        }
        return params.build();
    }

    /** O schema é texto na configuração do prompt e mapa no SDK; a conversão é aqui, na borda. */
    private static JsonOutputFormat.Schema schemaOf(String json) {
        Map<String, Object> parsed;
        try {
            parsed = SCHEMA_MAPPER.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException malformed) {
            // Schema é nosso, não do modelo: se está inválido, é bug de programação, não falha externa.
            throw new IllegalStateException("o JSON Schema declarado para a resposta é inválido", malformed);
        }
        var schema = JsonOutputFormat.Schema.builder();
        parsed.forEach((key, value) -> schema.putAdditionalProperty(key, JsonValue.from(value)));
        return schema.build();
    }

    private static java.util.Optional<String> textOf(ContentBlock block) {
        return block.text().map(TextBlock::text);
    }
}
