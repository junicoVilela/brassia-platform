package br.com.brew.brassia.integration.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A entrega no outbox e o retry dela (INT-002).
 *
 * <p>O que estes testes fixam é o critério da história: <strong>falha não bloqueia o domínio</strong>, e
 * tentativa esgotada não some.
 */
class WebhookDeliveryTest {

    private static final UUID CERVEJARIA = UUID.randomUUID();
    private static final UUID ASSINATURA = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-08-09T10:00:00Z");

    private static WebhookDelivery enfileirada() {
        return WebhookDelivery.enqueue(CERVEJARIA, ASSINATURA, WebhookEventType.BREW_ORDER_RELEASED,
                "order-1", "{}", AGORA);
    }

    @Test
    @DisplayName("recém-enfileirada está pendente e é elegível imediatamente")
    void enfileiradaEElegivel() {
        var delivery = enfileirada();

        assertThat(delivery.status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(delivery.attempts()).isZero();
        assertThat(delivery.isDue(AGORA)).isTrue();
    }

    @Test
    @DisplayName("entrega sem identidade de evento é recusada")
    void exigeIdentidadeDoEvento() {
        assertThatThrownBy(() -> WebhookDelivery.enqueue(CERVEJARIA, ASSINATURA,
                WebhookEventType.BREW_ORDER_RELEASED, "  ", "{}", AGORA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("sucesso encerra a entrega e guarda o status da resposta")
    void sucessoEncerra() {
        var entregue = enfileirada().succeededWith(200, AGORA);

        assertThat(entregue.status()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(entregue.deliveredAt()).isEqualTo(AGORA);
        assertThat(entregue.lastResponseStatus()).isEqualTo(200);
        assertThat(entregue.nextAttemptAt()).isNull();
        assertThat(entregue.isDue(AGORA.plusSeconds(3600))).isFalse();
    }

    @Test
    @DisplayName("o backoff é exponencial: 30 s, 1 min, 2 min, 4 min")
    void backoffExponencial() {
        // Um destino que caiu costuma voltar, e martelá-lo a cada 30 s atrapalha a recuperação dele. Com
        // muitas cervejarias apontando para o mesmo destino, retry fixo vira negação de serviço acidental.
        var primeira = enfileirada().failedWith(503, "indisponível", AGORA);
        assertThat(primeira.nextAttemptAt()).isEqualTo(AGORA.plus(Duration.ofSeconds(30)));

        var segunda = primeira.failedWith(503, "indisponível", AGORA);
        assertThat(segunda.nextAttemptAt()).isEqualTo(AGORA.plus(Duration.ofMinutes(1)));

        var terceira = segunda.failedWith(503, "indisponível", AGORA);
        assertThat(terceira.nextAttemptAt()).isEqualTo(AGORA.plus(Duration.ofMinutes(2)));

        var quarta = terceira.failedWith(503, "indisponível", AGORA);
        assertThat(quarta.nextAttemptAt()).isEqualTo(AGORA.plus(Duration.ofMinutes(4)));
        assertThat(quarta.status()).isEqualTo(DeliveryStatus.PENDING);
    }

    @Test
    @DisplayName("na quinta falha desiste, e a entrega esgotada NÃO some")
    void esgotaESobrevive() {
        // Uma entrega que desiste em silêncio é a pior falha de integração: o outro lado nunca soube do
        // evento, e nós também não sabemos que ele não soube.
        var delivery = enfileirada();
        for (int i = 0; i < WebhookDelivery.MAX_ATTEMPTS; i++) {
            delivery = delivery.failedWith(500, "erro do destino", AGORA);
        }

        assertThat(delivery.status()).isEqualTo(DeliveryStatus.EXHAUSTED);
        assertThat(delivery.attempts()).isEqualTo(WebhookDelivery.MAX_ATTEMPTS);
        assertThat(delivery.lastError()).isEqualTo("erro do destino");
        assertThat(delivery.lastResponseStatus()).isEqualTo(500);
        assertThat(delivery.nextAttemptAt()).isNull();
        assertThat(delivery.isDue(AGORA.plus(Duration.ofDays(1)))).isFalse();
    }

    @Test
    @DisplayName("entrega com backoff pendente não é elegível antes da hora")
    void naoEElegivelAntesDaHora() {
        var falhou = enfileirada().failedWith(503, "indisponível", AGORA);

        assertThat(falhou.isDue(AGORA.plusSeconds(29))).isFalse();
        assertThat(falhou.isDue(AGORA.plusSeconds(30))).isTrue();
    }

    @Test
    @DisplayName("o motivo é truncado: erro de terceiro não vira coluna sem limite")
    void motivoETruncado() {
        var falhou = enfileirada().failedWith(500, "x".repeat(500), AGORA);

        assertThat(falhou.lastError()).hasSize(200);
    }

    @Test
    @DisplayName("falha sem detalhe ainda registra que falhou")
    void falhaSemDetalhe() {
        // Timeout de conexão não traz status nem corpo. "sem detalhe" é melhor que nulo: distingue
        // "falhou e não sabemos por quê" de "não falhou".
        var falhou = enfileirada().failedWith(null, null, AGORA);

        assertThat(falhou.lastError()).isEqualTo("sem detalhe");
        assertThat(falhou.lastResponseStatus()).isNull();
        assertThat(falhou.status()).isEqualTo(DeliveryStatus.PENDING);
    }

    @Test
    @DisplayName("o corpo enfileirado não muda entre tentativas")
    void corpoEstavelEntreTentativas() {
        // O que se entrega é o fato como ele foi no instante em que aconteceu. Recalcular o corpo na hora
        // de reenviar entregaria o estado de agora sob o nome de um evento de antes.
        var delivery = enfileirada().failedWith(503, "x", AGORA).failedWith(503, "x", AGORA);

        assertThat(delivery.payload()).isEqualTo("{}");
        assertThat(delivery.eventId()).isEqualTo("order-1");
        assertThat(delivery.createdAt()).isEqualTo(AGORA);
    }
}
