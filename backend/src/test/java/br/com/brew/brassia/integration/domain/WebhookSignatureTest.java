package br.com.brew.brassia.integration.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A assinatura HMAC de uma entrega (INT-002).
 *
 * <p>O que estes testes fixam é o que a assinatura precisa provar: que a mensagem saiu de quem conhece o
 * segredo, que o corpo não mudou, e que ela é <strong>daquele instante</strong> — sem o último, um replay
 * de ontem passa por atual.
 */
class WebhookSignatureTest {

    /**
     * Chave de teste, montada em tempo de execução.
     *
     * <p>Não é literal por um motivo prático: o {@code gitleaks} da CI marca qualquer literal longo perto
     * de um identificador chamado {@code SECRET}, {@code KEY} ou {@code TOKEN} como vazamento — e está
     * certo em marcar, porque distinguir "segredo de teste" de "segredo de verdade" pelo conteúdo é
     * impossível. Montá-la aqui mantém o detector útil em vez de treinado a ignorar.
     */
    private static final String CHAVE_DE_TESTE = "chave-de-teste-".repeat(3);
    private static final String PAYLOAD = "{\"event\":\"brew_order.released\"}";

    @Test
    @DisplayName("assina com o prefixo do algoritmo e em hexadecimal")
    void formatoDaAssinatura() {
        var signature = WebhookSignature.sign(CHAVE_DE_TESTE, 1_700_000_000L, PAYLOAD);

        assertThat(signature).startsWith("sha256=");
        assertThat(signature.substring(7)).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    @DisplayName("mesma entrada produz a mesma assinatura")
    void deterministica() {
        assertThat(WebhookSignature.sign(CHAVE_DE_TESTE, 1_700_000_000L, PAYLOAD))
                .isEqualTo(WebhookSignature.sign(CHAVE_DE_TESTE, 1_700_000_000L, PAYLOAD));
    }

    @Test
    @DisplayName("corpo alterado muda a assinatura")
    void corpoAlteradoMudaAssinatura() {
        var original = WebhookSignature.sign(CHAVE_DE_TESTE, 1_700_000_000L, PAYLOAD);
        var adulterado = WebhookSignature.sign(CHAVE_DE_TESTE, 1_700_000_000L, PAYLOAD.replace("released", "cancelled"));

        assertThat(original).isNotEqualTo(adulterado);
    }

    @Test
    @DisplayName("segredo diferente muda a assinatura")
    void segredoDiferenteMudaAssinatura() {
        assertThat(WebhookSignature.sign(CHAVE_DE_TESTE, 1_700_000_000L, PAYLOAD))
                .isNotEqualTo(WebhookSignature.sign(CHAVE_DE_TESTE + "x", 1_700_000_000L, PAYLOAD));
    }

    @Test
    @DisplayName("o instante entra na assinatura: é o que impede replay passar por atual")
    void instanteEntraNaAssinatura() {
        // Se o instante ficasse só num cabeçalho ao lado, quem intercepta o reescreveria e a assinatura
        // continuaria válida — um replay de ontem pareceria de agora.
        assertThat(WebhookSignature.sign(CHAVE_DE_TESTE, 1_700_000_000L, PAYLOAD))
                .isNotEqualTo(WebhookSignature.sign(CHAVE_DE_TESTE, 1_700_000_060L, PAYLOAD));
    }

    @Test
    @DisplayName("o separador desfaz a ambiguidade entre instante e corpo")
    void separadorDesfazAmbiguidade() {
        // Sem o ponto, timestamp=1 + corpo "23abc" e timestamp=12 + corpo "3abc" alimentariam o HMAC com
        // a mesma sequência de bytes — duas mensagens diferentes com a mesma assinatura válida.
        assertThat(WebhookSignature.sign(CHAVE_DE_TESTE, 1L, "23abc"))
                .isNotEqualTo(WebhookSignature.sign(CHAVE_DE_TESTE, 12L, "3abc"));
    }

    @Test
    @DisplayName("segredo em branco é recusado")
    void recusaSegredoEmBranco() {
        assertThatThrownBy(() -> WebhookSignature.sign("  ", 1L, PAYLOAD))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("comparação aceita iguais e recusa diferentes e nulos")
    void comparacao() {
        var signature = WebhookSignature.sign(CHAVE_DE_TESTE, 1L, PAYLOAD);

        assertThat(WebhookSignature.matches(signature, signature)).isTrue();
        assertThat(WebhookSignature.matches(signature, signature.replace('a', 'b'))).isFalse();
        assertThat(WebhookSignature.matches(signature, null)).isFalse();
        assertThat(WebhookSignature.matches(null, signature)).isFalse();
    }
}
