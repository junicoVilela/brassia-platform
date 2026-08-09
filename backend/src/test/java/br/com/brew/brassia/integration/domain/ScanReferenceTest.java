package br.com.brew.brassia.integration.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O que está escrito num código lido (INT-003).
 *
 * <p>O que estes testes fixam é o critério da história: <strong>o código não concede acesso</strong>. Ele
 * carrega apenas o quê — tipo e identificador —, e a autorização é uma verificação separada, feita depois.
 */
class ScanReferenceTest {

    @Test
    @DisplayName("interpreta o formato do esquema")
    void interpretaFormato() {
        var reference = ScanReference.parse("brassia://equipamento/TANQUE-01");

        assertThat(reference.target()).isEqualTo(ScanTarget.EQUIPAMENTO);
        assertThat(reference.identifier()).isEqualTo("TANQUE-01");
    }

    @Test
    @DisplayName("lê as etiquetas de envase JÁ IMPRESSAS, que têm sufixo")
    void leEtiquetasJaImpressas() {
        // PKG-004 imprime `brassia://lote/<código>/envase/<plano>`. Recusar o sufixo invalidaria toda
        // etiqueta que já está colada numa caixa.
        var reference = ScanReference.parse("brassia://lote/LOTE-2026-014/envase/ENV-3");

        assertThat(reference.target()).isEqualTo(ScanTarget.LOTE);
        assertThat(reference.identifier()).isEqualTo("LOTE-2026-014");
    }

    @Test
    @DisplayName("o esquema é aceito sem diferenciar maiúsculas")
    void esquemaSemCaixa() {
        assertThat(ScanReference.parse("BRASSIA://lote/L-1").target()).isEqualTo(ScanTarget.LOTE);
    }

    @Test
    @DisplayName("os quatro tipos da história são reconhecidos")
    void quatroTipos() {
        assertThat(ScanReference.parse("brassia://equipamento/E-1").target()).isEqualTo(ScanTarget.EQUIPAMENTO);
        assertThat(ScanReference.parse("brassia://lote/L-1").target()).isEqualTo(ScanTarget.LOTE);
        assertThat(ScanReference.parse("brassia://op/O-1").target()).isEqualTo(ScanTarget.OP);
        assertThat(ScanReference.parse("brassia://embalagem/P-1").target()).isEqualTo(ScanTarget.EMBALAGEM);
    }

    @Test
    @DisplayName("O CÓDIGO NÃO CARREGA CREDENCIAL: cada tipo declara a permissão que a leitura exige")
    void codigoNaoCarregaCredencial() {
        // Um QR colado num tanque é legível por quem entra na sala, fotografável de longe e copiável para
        // outra etiqueta. Qualquer segredo impresso nele é um segredo público.
        for (var target : ScanTarget.values()) {
            assertThat(target.requiredPermission()).isNotBlank();
            assertThat(target.route()).startsWith("/");
        }
        assertThat(ScanTarget.EQUIPAMENTO.requiredPermission()).isEqualTo("equipment.read");
        assertThat(ScanTarget.LOTE.requiredPermission()).isEqualTo("production.batch.read");
    }

    @Test
    @DisplayName("esquema estranho é recusado")
    void recusaEsquemaEstranho() {
        assertThatThrownBy(() -> ScanReference.parse("https://malicioso.example.com/lote/1"))
                .isInstanceOf(UnknownScanCodeException.class);
        assertThatThrownBy(() -> ScanReference.parse("lote/1"))
                .isInstanceOf(UnknownScanCodeException.class);
    }

    @Test
    @DisplayName("tipo desconhecido é recusado")
    void recusaTipoDesconhecido() {
        assertThatThrownBy(() -> ScanReference.parse("brassia://custo/1"))
                .isInstanceOf(UnknownScanCodeException.class);
    }

    @Test
    @DisplayName("sem identificador é recusado")
    void recusaSemIdentificador() {
        assertThatThrownBy(() -> ScanReference.parse("brassia://lote"))
                .isInstanceOf(UnknownScanCodeException.class);
        assertThatThrownBy(() -> ScanReference.parse("brassia://lote/"))
                .isInstanceOf(UnknownScanCodeException.class);
    }

    @Test
    @DisplayName("identificador com caminho, query ou script é recusado")
    void recusaIdentificadorPerigoso() {
        // A etiqueta é entrada de terceiro tanto quanto um formulário: qualquer um imprime um QR e cola no
        // tanque.
        assertThatThrownBy(() -> ScanReference.parse("brassia://lote/../../admin"))
                .isInstanceOf(UnknownScanCodeException.class);
        assertThatThrownBy(() -> ScanReference.parse("brassia://lote/1?admin=true"))
                .isInstanceOf(UnknownScanCodeException.class);
        assertThatThrownBy(() -> ScanReference.parse("brassia://lote/<script>"))
                .isInstanceOf(UnknownScanCodeException.class);
        assertThatThrownBy(() -> ScanReference.parse("brassia://lote/" + "x".repeat(81)))
                .isInstanceOf(UnknownScanCodeException.class);
    }

    @Test
    @DisplayName("código vazio é recusado")
    void recusaVazio() {
        assertThatThrownBy(() -> ScanReference.parse(null)).isInstanceOf(UnknownScanCodeException.class);
        assertThatThrownBy(() -> ScanReference.parse("   ")).isInstanceOf(UnknownScanCodeException.class);
    }

    @Test
    @DisplayName("o que se imprime volta a ser lido igual")
    void ciclo() {
        var original = new ScanReference(ScanTarget.EQUIPAMENTO, "TANQUE-01");

        assertThat(ScanReference.parse(original.encoded())).isEqualTo(original);
    }
}
