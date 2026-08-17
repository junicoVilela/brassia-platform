package br.com.brew.brassia.distribution.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OfflineOperationTest {

    private static final UUID APARELHO = UUID.randomUUID();
    private static final UUID CARGA = UUID.randomUUID();
    private static final UUID PARADA = UUID.randomUUID();
    private static final Instant NO_BAR = Instant.parse("2026-08-18T10:00:00Z");
    private static final Instant NO_PATIO = Instant.parse("2026-08-18T18:00:00Z");

    private static OfflineOperation operacao(UUID id, int sequencia) {
        return OfflineOperation.received(id, APARELHO, CARGA, PARADA, NO_BAR, NO_PATIO, sequencia);
    }

    // --- idempotência ---

    @Test
    void oIdentificadorEDoAparelhoENaoDoServidor() {
        // É o que torna o reenvio seguro: offline não há como pedir um número ao servidor, e sem um id
        // que o aparelho consiga gerar sozinho, "sincronizar" duas vezes vira duas entregas.
        var id = UUID.randomUUID();
        var op = operacao(id, 1);

        assertThat(op.clientOperationId()).isEqualTo(id);
        assertThat(op.deviceId()).isEqualTo(APARELHO);
    }

    @Test
    void oReenvioDevolveOMesmoResultadoENaoCriaOutro() {
        // O entregador que aperta "sincronizar" duas vezes num sinal ruim não pode registrar duas
        // entregas para o mesmo cliente — o estoque perderia a conta.
        var prova = UUID.randomUUID();
        var primeira = operacao(UUID.randomUUID(), 1);
        primeira.applied(prova);

        var reenvio = operacao(primeira.clientOperationId(), 1);
        reenvio.duplicateOf(prova);

        assertThat(reenvio.status()).isEqualTo(SyncStatus.DUPLICATE);
        assertThat(reenvio.resultId()).contains(prova);
        // E a duplicata NÃO é erro: ela devolve o que já existia, para o aparelho poder fechar o item.
        assertThat(reenvio.needsDecision()).isFalse();
    }

    // --- conflito ---

    @Test
    void oConflitoNaoSeResolveSozinho() {
        // Último-a-escrever-ganha descartaria em silêncio o registro de quem estava lá — ou o do
        // escritório. Nos dois casos alguém descobre semanas depois, sem saber o que perdeu.
        var op = operacao(UUID.randomUUID(), 1);
        op.conflicted("o escritório já registrou esta parada às 11h");

        assertThat(op.status()).isEqualTo(SyncStatus.CONFLICTED);
        assertThat(op.needsDecision()).isTrue();
        assertThat(op.reason()).contains("o escritório já registrou esta parada às 11h");
        assertThat(op.isApplied()).isFalse();
    }

    @Test
    void aRecusaPrecisaDeMotivo() {
        // Sem motivo, o entregador fica com um item vermelho na tela e nada a fazer.
        var op = operacao(UUID.randomUUID(), 1);

        assertThatThrownBy(() -> op.rejected("  ")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("motivo");
        assertThatThrownBy(() -> op.conflicted(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aOperacaoRecusadaNaoSome() {
        // Ela fica com o motivo, como a prova de entrega: o que o aparelho tentou registrar é parte do
        // que aconteceu naquele dia.
        var op = operacao(UUID.randomUUID(), 1);
        op.rejected("a carga já foi encerrada");

        assertThat(op.status()).isEqualTo(SyncStatus.REJECTED);
        assertThat(op.reason()).contains("a carga já foi encerrada");
        assertThat(op.stopId()).isEqualTo(PARADA);
        assertThat(op.occurredAt()).isEqualTo(NO_BAR);
    }

    // --- tempo ---

    @Test
    void aHoraDoFatoEDoAparelhoEAdeChegadaEDoServidor() {
        // Usar a hora do servidor para o fato colocaria toda entrega feita offline no momento em que o
        // caminhão voltou ao depósito — e ninguém entregou nada no pátio às seis da tarde.
        var op = operacao(UUID.randomUUID(), 1);

        assertThat(op.occurredAt()).isEqualTo(NO_BAR);
        assertThat(op.receivedAt()).isEqualTo(NO_PATIO);
        assertThat(Duration.between(op.occurredAt(), op.receivedAt())).hasHours(8);
    }

    @Test
    void oRelogioAdiantadoEMarcadoENaoRecusado() {
        // O celular do entregador não se ajusta sozinho no subsolo do bar, e recusar por causa disso
        // perderia o registro do que aconteceu de verdade.
        var adiantado = OfflineOperation.received(UUID.randomUUID(), APARELHO, CARGA, PARADA,
                NO_PATIO.plus(Duration.ofHours(2)), NO_PATIO, 1);

        assertThat(adiantado.clockAhead()).isTrue();
        assertThat(adiantado.isApplied()).isTrue();
        assertThat(operacao(UUID.randomUUID(), 1).clockAhead()).isFalse();
    }

    // --- ordem ---

    @Test
    void aOrdemEadoAparelho() {
        // Aplicar fora dela entregaria antes de despachar. A fila é do dispositivo, e não da ordem em
        // que os pacotes chegaram pela rede.
        var terceira = operacao(UUID.randomUUID(), 3);
        var primeira = operacao(UUID.randomUUID(), 1);
        var segunda = operacao(UUID.randomUUID(), 2);

        var fila = List.of(terceira, primeira, segunda).stream()
                .sorted(Comparator.comparingInt(OfflineOperation::sequence)).toList();

        assertThat(fila).extracting(OfflineOperation::sequence).containsExactly(1, 2, 3);
    }

    @Test
    void aSequenciaComecaEmUm() {
        assertThatThrownBy(() -> operacao(UUID.randomUUID(), 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sequência");
    }

    @Test
    void aOperacaoPrecisaDeAparelhoEDeParada() {
        // Sem aparelho não há a quem perguntar; sem parada não há o que registrar.
        assertThatThrownBy(() -> OfflineOperation.received(UUID.randomUUID(), null, CARGA, PARADA,
                NO_BAR, NO_PATIO, 1)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> OfflineOperation.received(UUID.randomUUID(), APARELHO, CARGA, null,
                NO_BAR, NO_PATIO, 1)).isInstanceOf(NullPointerException.class);
    }
}
