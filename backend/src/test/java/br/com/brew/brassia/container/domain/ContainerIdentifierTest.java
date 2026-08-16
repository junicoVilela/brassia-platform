package br.com.brew.brassia.container.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContainerIdentifierTest {

    private static final UUID CONTEINER = UUID.randomUUID();
    private static final Instant HOJE = Instant.parse("2026-08-16T10:00:00Z");
    private static final Instant DEPOIS = HOJE.plus(Duration.ofDays(30));

    private static ContainerIdentifier etiqueta(String valor) {
        return ContainerIdentifier.assign(UUID.randomUUID(), CONTEINER, valor,
                IdentifierTechnology.QR, HOJE);
    }

    @Test
    void lerUmCodigoIdentificaENaoAutoriza() {
        // O critério transversal da sprint, escrito em código: a etiqueta responde "qual keg é esta" e
        // nada mais. Um código fotografado no bar não pode virar chave de nada, então não existe campo
        // aqui que conceda alçada, cervejaria ou token.
        var e = etiqueta("QR-ABC-123");

        assertThat(e.containerId()).isEqualTo(CONTEINER);
        assertThat(Arrays.stream(ContainerIdentifier.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getName))
                .doesNotContain("permission", "permissions", "breweryId", "token", "secret", "role");
    }

    @Test
    void aEtiquetaAposentadaNaoVoltaAApontarParaNada() {
        // Reaproveitar o valor faria um registro antigo — uma entrega de seis meses atrás — passar a
        // resolver para outro contêiner, e a genealogia ficaria errada sem ninguém perceber.
        var e = etiqueta("QR-ABC-123");
        e.retire(DEPOIS);

        assertThat(e.isActive()).isFalse();
        assertThat(e.resolvesAt(DEPOIS.plus(Duration.ofDays(1)))).isFalse();
        // E continua explicando o passado: a leitura de antes da troca ainda se entende.
        assertThat(e.resolvesAt(HOJE.plus(Duration.ofDays(1)))).isTrue();
        assertThat(e.value()).isEqualTo("QR-ABC-123");
    }

    @Test
    void aposentarDuasVezesNaoMudaAData() {
        // Quem clica de novo quer o mesmo resultado, e a data é o registro de quando o vínculo acabou.
        var e = etiqueta("RFID-9");
        e.retire(DEPOIS);
        e.retire(DEPOIS.plus(Duration.ofDays(5)));

        assertThat(e.retiredAt()).contains(DEPOIS);
    }

    @Test
    void oMesmoConteinerAceitaMaisDeUmaEtiquetaAtiva() {
        // Um keg com adesivo de QR e tag RFID é o caso normal, e não exceção: são dois jeitos de ler o
        // mesmo vasilhame.
        var qr = etiqueta("QR-1");
        var rfid = ContainerIdentifier.assign(UUID.randomUUID(), CONTEINER, "RFID-1",
                IdentifierTechnology.RFID, HOJE);

        assertThat(qr.containerId()).isEqualTo(rfid.containerId());
        assertThat(qr.isActive()).isTrue();
        assertThat(rfid.isActive()).isTrue();
    }

    @Test
    void aEtiquetaPrecisaDeValor() {
        assertThatThrownBy(() -> etiqueta("  ")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valor");
    }
}
