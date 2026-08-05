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

    private static Shipment record(String destination, int units) {
        return Shipment.record(UUID.randomUUID(), UUID.randomUUID(), destination, null, units, SHIPPED,
                null, UUID.randomUUID(), NOW);
    }
}
