package br.com.brew.brassia.traceability.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.brew.brassia.traceability.LineageSource.Node;
import br.com.brew.brassia.traceability.LineageSource.NodeType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Invariantes da quarentena (FDS-002). */
class QuarantineTest {

    private static final Instant NOW = Instant.parse("2026-08-05T10:00:00Z");
    private static final Node LOTE = new Node(NodeType.BATCH, UUID.randomUUID(), "LOTE-100");

    @Test
    @DisplayName("abrir exige motivo: contenção sem motivo não é investigação")
    void abrirExigeMotivo() {
        assertThatThrownBy(() -> Quarantine.open(UUID.randomUUID(), LOTE, "  ", UUID.randomUUID(), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("motivo");
    }

    @Test
    @DisplayName("liberar exige justificativa — é a metade da alçada que a permissão não dá")
    void liberarExigeJustificativa() {
        var quarantine = open();

        assertThatThrownBy(() -> quarantine.release(UUID.randomUUID(), null, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("justificativa");
        assertThat(quarantine.open()).isTrue();
    }

    @Test
    @DisplayName("liberar duas vezes é recusado: a segunda liberação não tem o que liberar")
    void liberarDuasVezesEhRecusado() {
        var quarantine = open();
        quarantine.release(UUID.randomUUID(), "análise microbiológica aprovada", NOW);

        assertThat(quarantine.status()).isEqualTo(QuarantineStatus.RELEASED);
        assertThatThrownBy(() -> quarantine.release(UUID.randomUUID(), "de novo", NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("liberar não apaga: a investigação encerrada continua legível")
    void liberarPreservaAHistoria() {
        var quarantine = open();
        var actor = UUID.randomUUID();

        quarantine.release(actor, "  contraprova negativa  ", NOW);

        assertThat(quarantine.reason()).isEqualTo("suspeita de contaminação");
        assertThat(quarantine.releaseJustification()).isEqualTo("contraprova negativa");
        assertThat(quarantine.releasedBy()).isEqualTo(actor);
        assertThat(quarantine.releasedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("o rótulo da origem é congelado na abertura")
    void origemCongelada() {
        var quarantine = open();

        assertThat(quarantine.origin()).isEqualTo(LOTE);
        assertThat(quarantine.originLabel()).isEqualTo("LOTE-100");
    }

    private static Quarantine open() {
        return Quarantine.open(UUID.randomUUID(), LOTE, "suspeita de contaminação", UUID.randomUUID(), NOW);
    }
}
