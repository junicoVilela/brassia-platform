package br.com.brew.brassia.traceability.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.traceability.LineageSource.Node;
import br.com.brew.brassia.traceability.LineageSource.NodeType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Invariantes do recall e da comunicação (FDS-003). */
class RecallTest {

    private static final Instant NOW = Instant.parse("2026-08-05T10:00:00Z");
    private static final Node LOTE = new Node(NodeType.BATCH, UUID.randomUUID(), "LOTE-100");

    @Test
    @DisplayName("recall sem motivo não existe")
    void motivoObrigatorio() {
        assertThatThrownBy(() -> Recall.open(UUID.randomUUID(), "REC-2026-0001", LOTE, "  ",
                UUID.randomUUID(), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("encerrar com destino pendente é recusado — o dossiê diria que terminou sem ter avisado")
    void naoEncerraComPendencia() {
        var recall = open();

        assertThatThrownBy(() -> recall.close(UUID.randomUUID(), "todos recolhidos", 2, NOW))
                .isInstanceOf(PendingNotificationsException.class)
                .satisfies(e -> assertThat(((PendingNotificationsException) e).pending()).isEqualTo(2));
        assertThat(recall.open()).isTrue();
    }

    @Test
    @DisplayName("encerrar exige resumo e trava o recall")
    void encerraComResumo() {
        var recall = open();

        assertThatThrownBy(() -> recall.close(UUID.randomUUID(), " ", 0, NOW))
                .isInstanceOf(IllegalArgumentException.class);

        recall.close(UUID.randomUUID(), "1.200 latas recolhidas; laudo anexado", 0, NOW);

        assertThat(recall.status()).isEqualTo(Recall.RecallStatus.CLOSED);
        assertThatThrownBy(() -> recall.close(UUID.randomUUID(), "de novo", 0, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("comunicação nasce pendente: um dossiê que nasce 'tudo avisado' mente")
    void comunicacaoNascePendente() {
        var notification = notification();

        assertThat(notification.pending()).isTrue();
        assertThat(notification.channel()).isNull();
    }

    @Test
    @DisplayName("registrar comunicação exige o canal — 'avisamos' sem dizer como não prova nada")
    void comunicacaoExigeCanal() {
        var notification = notification();

        assertThatThrownBy(() -> notification.notified(UUID.randomUUID(), null, "falei com o gerente", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(notification.pending()).isTrue();
    }

    @Test
    @DisplayName("a mesma comunicação não se registra duas vezes")
    void comunicacaoNaoSeRepete() {
        var notification = notification();
        notification.notified(UUID.randomUUID(), "telefone", "falei com o gerente", NOW);

        assertThat(notification.pending()).isFalse();
        assertThatThrownBy(() -> notification.notified(UUID.randomUUID(), "e-mail", null, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    private static Recall open() {
        return Recall.open(UUID.randomUUID(), "REC-2026-0001", LOTE, "contaminação confirmada",
                UUID.randomUUID(), NOW);
    }

    private static RecallNotification notification() {
        return RecallNotification.pending(UUID.randomUUID(), UUID.randomUUID(), "LOTE-100/1",
                "Bar do Zé", "(11) 99999-0000", 120);
    }
}
