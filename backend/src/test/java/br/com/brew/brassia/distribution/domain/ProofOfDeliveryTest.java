package br.com.brew.brassia.distribution.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProofOfDeliveryTest {

    private static final UUID PARADA = UUID.randomUUID();
    private static final UUID ENTREGADOR = UUID.randomUUID();
    private static final UUID KEG_A = UUID.randomUUID();
    private static final UUID KEG_B = UUID.randomUUID();
    private static final UUID VAZIO = UUID.randomUUID();
    private static final Instant MANHA = Instant.parse("2026-08-17T10:00:00Z");

    private static ProofOfDelivery entrega(DeliveryOutcome desfecho, List<UUID> entregues,
            List<UUID> recolhidos, String nota) {
        return ProofOfDelivery.record(UUID.randomUUID(), PARADA, desfecho, MANHA, ENTREGADOR,
                entregues, recolhidos, nota, null, null, false);
    }

    // --- append-only ---

    @Test
    void aProvaNaoSeEditaESeCorrigePorEventoNovo() {
        // O critério transversal da sprint. Uma prova reescrita é a pior espécie de registro: parece
        // original e diz outra coisa, e ninguém sabe o que o entregador anotou às dez da manhã.
        var original = entrega(DeliveryOutcome.DELIVERED, List.of(KEG_A, KEG_B), List.of(), null);

        var correcao = ProofOfDelivery.correcting(UUID.randomUUID(), original,
                DeliveryOutcome.PARTIAL, MANHA.plus(Duration.ofHours(2)), ENTREGADOR, List.of(KEG_A),
                List.of(), "o segundo keg voltou; marquei errado na pressa");

        assertThat(correcao.isCorrection()).isTrue();
        assertThat(correcao.correctsProofId()).contains(original.id());
        assertThat(correcao.deliveredContainerIds()).containsExactly(KEG_A);
        // A original continua de pé, dizendo o que dizia.
        assertThat(original.outcome()).isEqualTo(DeliveryOutcome.DELIVERED);
        assertThat(original.deliveredContainerIds()).containsExactly(KEG_A, KEG_B);
    }

    @Test
    void aCorrecaoPrecisaDizerOQueEstavaErrado() {
        // Uma versão nova sem explicação faz quem lê seis meses depois não saber em qual acreditar.
        var original = entrega(DeliveryOutcome.DELIVERED, List.of(KEG_A), List.of(), null);

        assertThatThrownBy(() -> ProofOfDelivery.correcting(UUID.randomUUID(), original,
                DeliveryOutcome.REFUSED, MANHA, ENTREGADOR, List.of(), List.of(), "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("o que estava errado");
    }

    @Test
    void naoSeCorrigeUmaCorrecao() {
        // Encadear versões tornaria "a última palavra" uma pergunta.
        var original = entrega(DeliveryOutcome.DELIVERED, List.of(KEG_A), List.of(), null);
        var correcao = ProofOfDelivery.correcting(UUID.randomUUID(), original, DeliveryOutcome.REFUSED,
                MANHA, ENTREGADOR, List.of(), List.of(), "cliente recusou");

        assertThatThrownBy(() -> ProofOfDelivery.correcting(UUID.randomUUID(), correcao,
                DeliveryOutcome.ABSENT, MANHA, ENTREGADOR, List.of(), List.of(), "de novo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prova original");
    }

    // --- consentimento ---

    @Test
    void naoHaAssinaturaNemFotoSemConsentimento() {
        // O objeto não existe sem o consentimento: não há caminho no código que guarde a mídia sem ele.
        assertThatThrownBy(() -> new ConsentedMedia(ConsentedMedia.MediaKind.SIGNATURE, "s3://x", "  ",
                MANHA, "comprovar a entrega"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("consentimento");
    }

    @Test
    void oConsentimentoPrecisaDeFinalidade() {
        // Sem finalidade escrita, o consentimento vira cheque em branco.
        assertThatThrownBy(() -> new ConsentedMedia(ConsentedMedia.MediaKind.PHOTO, "s3://x", "Bruno",
                MANHA, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("finalidade");
    }

    @Test
    void aEntregaSemMidiaEEstadoLegitimo() {
        // Recusar consentimento não pode travar a operação: o cliente que não quer assinar continua
        // recebendo a cerveja.
        var p = entrega(DeliveryOutcome.DELIVERED, List.of(KEG_A), List.of(), null);

        assertThat(p.hasMedia()).isFalse();
        assertThat(p.outcome()).isEqualTo(DeliveryOutcome.DELIVERED);
    }

    @Test
    void aMidiaGuardaAChaveDoArquivoEQuemConsentiu() {
        var media = new ConsentedMedia(ConsentedMedia.MediaKind.SIGNATURE, "s3://provas/1", "Bruno",
                MANHA, "comprovar a entrega");
        var p = ProofOfDelivery.record(UUID.randomUUID(), PARADA, DeliveryOutcome.DELIVERED, MANHA,
                ENTREGADOR, List.of(KEG_A), List.of(), null, media, null, false);

        assertThat(p.media()).contains(media);
        assertThat(media.purpose()).isEqualTo("comprovar a entrega");
    }

    // --- geolocalização minimizada ---

    @Test
    void aCoordenadaChegaArredondadaEACheiaNaoEGuardada() {
        // A coordenada cheia do celular do entregador, parada a parada, todo dia, é um rastro de
        // movimentação de uma pessoa — e a operação só precisa saber se foi no lugar certo.
        var lugar = CoarseLocation.of(new BigDecimal("-23.5614321"), new BigDecimal("-46.6565987"));

        assertThat(lugar.latitude()).isEqualByComparingTo("-23.561");
        assertThat(lugar.longitude()).isEqualByComparingTo("-46.657");
        assertThat(lugar.latitude().scale()).isEqualTo(3);
    }

    @Test
    void oAgregadoRecusaCoordenadaFinaDemais() {
        // A porta de entrada é o `of`, e o construtor recusa quem tentar contornar.
        assertThatThrownBy(() -> new CoarseLocation(new BigDecimal("-23.5614321"),
                new BigDecimal("-46.657")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("arredondada");
    }

    @Test
    void aEntregaSemLocalizacaoEEstadoLegitimo() {
        // Celular sem sinal no subsolo do bar acontece, e a entrega não pode depender do GPS.
        assertThat(entrega(DeliveryOutcome.DELIVERED, List.of(KEG_A), List.of(), null).location())
                .isEmpty();
    }

    // --- desfechos ---

    @Test
    void entregueSemItensNaoEEntrega() {
        // É o clique automático do fim do dia, e ele fecharia a parada com o caminhão ainda cheio.
        assertThatThrownBy(() -> entrega(DeliveryOutcome.DELIVERED, List.of(), List.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sem itens");
    }

    @Test
    void aNaoEntregaPrecisaDoMotivo() {
        // O que fazer amanhã depende dele: "recusado" sozinho não diz se foi preço, avaria ou pedido
        // errado.
        assertThatThrownBy(() -> entrega(DeliveryOutcome.REFUSED, List.of(), List.of(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("motivo");

        assertThat(entrega(DeliveryOutcome.ABSENT, List.of(), List.of(), "bar fechado às 7h")
                .note()).contains("bar fechado às 7h");
    }

    @Test
    void naoSeRegistraItemEntregueNumaNaoEntrega() {
        // Contradição que faria o estoque acreditar em uma das duas metades.
        assertThatThrownBy(() -> entrega(DeliveryOutcome.REFUSED, List.of(KEG_A), List.of(), "recusou"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não entrega");
    }

    @Test
    void oQueNaoDesceuVoltaParaCasa() {
        // A carga precisa saber disso hoje, e não amanhã quando o keg não aparecer no depósito.
        var p = entrega(DeliveryOutcome.PARTIAL, List.of(KEG_A), List.of(), "só um coube na câmara");

        assertThat(p.returning(List.of(KEG_A, KEG_B))).containsExactly(KEG_B);
    }

    // --- coleta ---

    @Test
    void coletarEFatoSeparadoDeEntregar() {
        // O motorista frequentemente recolhe vazios num bar onde não deixou nada. Amarrar os dois faria
        // uma coleta exigir uma entrega inventada.
        var p = entrega(DeliveryOutcome.ABSENT, List.of(), List.of(VAZIO), "ninguém para receber, mas "
                + "os vazios estavam na calçada");

        assertThat(p.deliveredContainerIds()).isEmpty();
        assertThat(p.collectedContainerIds()).containsExactly(VAZIO);
    }

    @Test
    void oMesmoVasilhameNaoEEntregueERecolhidoNaMesmaParada() {
        assertThatThrownBy(() -> entrega(DeliveryOutcome.DELIVERED, List.of(KEG_A), List.of(KEG_A),
                null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entregue e recolhido");
    }

    // --- janela ---

    @Test
    void foraDaJanelaSeRegistraENaoImpede() {
        // A janela era compromisso; perdê-la é fato a explicar depois, e não motivo para recusar a
        // entrega que o cliente aceitou.
        var p = ProofOfDelivery.record(UUID.randomUUID(), PARADA, DeliveryOutcome.DELIVERED, MANHA,
                ENTREGADOR, List.of(KEG_A), List.of(), null, null, null, true);

        assertThat(p.outsideWindow()).isTrue();
        assertThat(p.outcome()).isEqualTo(DeliveryOutcome.DELIVERED);
    }
}
