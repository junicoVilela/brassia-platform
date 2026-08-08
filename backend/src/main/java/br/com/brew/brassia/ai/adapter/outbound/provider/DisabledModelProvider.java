package br.com.brew.brassia.ai.adapter.outbound.provider;

import br.com.brew.brassia.ai.application.port.outbound.ModelProvider;
import java.time.Duration;
import java.util.List;

/**
 * O provedor de uma instalação que não fala com IA (AIA-001).
 *
 * <p><strong>Não é um mock nem um stub de teste</strong> — é a implementação de produção do caso "esta
 * cervejaria não contratou IA", que é o default do produto. Existir como classe, e não como um
 * {@code if (provider == null)} espalhado pelos casos de uso, é o que faz o critério "provedor
 * desabilitado não quebra fluxo" ser verdade por construção: não há caminho onde o gateway receba nulo.
 *
 * <p>{@link #send} nunca é chamado — o gateway recusa antes, ao ver {@code enabled() == false} e a
 * cadeia vazia. Lançar aqui é a última linha de defesa: se alguém encontrar um caminho até este método,
 * é bug de composição, e falhar alto é melhor do que devolver resposta inventada.
 */
final class DisabledModelProvider implements ModelProvider {

    private final String configuredName;
    private final Duration timeout;
    private final String currency;

    DisabledModelProvider(String configuredName, Duration timeout, String currency) {
        this.configuredName = configuredName;
        this.timeout = timeout;
        this.currency = currency;
    }

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public String name() {
        return configuredName;
    }

    @Override
    public List<ModelChoice> chain() {
        return List.of();
    }

    @Override
    public Duration timeout() {
        return timeout;
    }

    @Override
    public String currency() {
        return currency;
    }

    @Override
    public Completion send(Call call) {
        throw new IllegalStateException("o provedor de IA está desabilitado nesta instalação");
    }
}
