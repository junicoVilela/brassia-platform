package br.com.brew.brassia.packaging.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Invariantes da expedição (TRC-001-D). */
class ShipmentTest {

    private static final Instant NOW = Instant.parse("2026-08-05T10:00:00Z");
    private static final LocalDate SHIPPED = LocalDate.of(2026, 8, 4);

    @Test
    @DisplayName("expedição sem destino não diz a quem avisar")
    void destinoObrigatorio() {
        assertThatThrownBy(() -> record("  ", 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("destino");
    }

    @Test
    @DisplayName("expedição sem unidades não é expedição")
    void unidadesPositivas() {
        assertThatThrownBy(() -> record("Bar do Zé", 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("contato é opcional: destino sem contato é lacuna a mostrar, não motivo de recusa")
    void contatoOpcional() {
        var shipment = Shipment.record(UUID.randomUUID(), UUID.randomUUID(), "Bar do Zé", "  ", 120,
                SHIPPED, null, UUID.randomUUID(), NOW);

        assertThat(shipment.contact()).isNull();
        assertThat(shipment.destination()).isEqualTo("Bar do Zé");
    }

    @Test
    @DisplayName("A EXPEDIÇÃO NASCE VALENDO, e o estorno a tira do recall sem apagá-la")
    void estornoTiraDoRecall() {
        // Apagar tornaria indistinguível "nunca houve expedição" de "houve e foi estornada" — e a segunda
        // precisa ser demonstrável para quem recebeu a comunicação de um recall baseado nela.
        var shipment = record("Bar do Zé", 200);
        assertThat(shipment.isActive()).isTrue();

        shipment.reverse(ATOR, "Destino digitado errado: era o Bar do João", NOW);

        assertThat(shipment.isActive()).isFalse();
        assertThat(shipment.units()).isEqualTo(200);
        assertThat(shipment.destination()).isEqualTo("Bar do Zé");
        assertThat(shipment.reversal()).isPresent();
        assertThat(shipment.reversal().orElseThrow().reason()).contains("digitado errado");
    }

    @Test
    @DisplayName("MOTIVO EVASIVO É RECUSADO: a justificativa existe para ser lida meses depois")
    void motivoPrecisaDizerAlgo() {
        // Sem conteúdo, o histórico mostra uma expedição que deixou de valer sem dizer se foi digitação,
        // destino trocado ou carga que não saiu — e as três exigem reações diferentes.
        var shipment = record("Bar do Zé", 200);

        assertThatThrownBy(() -> shipment.reverse(ATOR, "n/a", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> shipment.reverse(ATOR, "   ", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(shipment.isActive()).isTrue();
    }

    @Test
    @DisplayName("estornar duas vezes é recusado: a segunda sobrescreveria quem estornou de verdade")
    void naoEstornaDuasVezes() {
        var shipment = record("Bar do Zé", 200);
        shipment.reverse(ATOR, "Carga não saiu do galpão", NOW);

        assertThatThrownBy(() -> shipment.reverse(UUID.randomUUID(), "outro motivo qualquer", NOW))
                .isInstanceOf(Shipment.AlreadyReversedException.class);
        assertThat(shipment.reversal().orElseThrow().by()).isEqualTo(ATOR);
    }

    private static final UUID ATOR = UUID.randomUUID();

    private static Shipment record(String destination, int units) {
        return Shipment.record(UUID.randomUUID(), UUID.randomUUID(), destination, null, units, SHIPPED,
                null, UUID.randomUUID(), NOW);
    }
}
