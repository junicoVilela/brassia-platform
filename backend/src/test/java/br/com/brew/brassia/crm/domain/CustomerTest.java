package br.com.brew.brassia.crm.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerTest {

    private static final UUID CERVEJARIA = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-08-15T10:00:00Z");

    private static Customer cliente(String razao, String fantasia) {
        return Customer.create(UUID.randomUUID(), CERVEJARIA, razao, fantasia, "12.345.678/0001-90", AGORA);
    }

    @Test
    void aRazaoSocialEObrigatoria() {
        assertThatThrownBy(() -> cliente("  ", "Bar Central"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("razão social");
    }

    @Test
    void oNomeDeTelaEOFantasiaQuandoExiste() {
        // Quem atende o balcão conhece o bar pelo nome da fachada, não pela razão social.
        assertThat(cliente("Central Bebidas Ltda", "Bar Central").displayName()).isEqualTo("Bar Central");
        assertThat(cliente("Central Bebidas Ltda", null).displayName()).isEqualTo("Central Bebidas Ltda");
    }

    @Test
    void oDocumentoNaoEValidadoAqui() {
        // DEC-CRM-002: cliente estrangeiro não tem CNPJ, e recusar cadastro por formato seria a
        // plataforma decidindo com quem a cervejaria pode vender.
        var estrangeiro = Customer.create(UUID.randomUUID(), CERVEJARIA, "Craft Imports LLC", null,
                "EIN 98-7654321", AGORA);

        assertThat(estrangeiro.taxId()).contains("EIN 98-7654321");
    }

    @Test
    void clienteSemDocumentoEAceito() {
        // O cadastro costuma nascer antes do documento chegar, e travar aqui empurraria o vendedor
        // para inventar um número.
        assertThat(Customer.create(UUID.randomUUID(), CERVEJARIA, "Bar do Zé", null, null, AGORA).taxId())
                .isEmpty();
    }

    @Test
    void naoSeApagaClienteDesativaSe() {
        // Remover deixaria pedido e expedição apontando para o nada — e é o histórico de expedição que
        // um recall percorre para saber a quem avisar.
        var c = cliente("Central Bebidas Ltda", "Bar Central");
        assertThat(c.isActive()).isTrue();

        c.deactivate();
        assertThat(c.isActive()).isFalse();
        assertThat(c.legalName()).isEqualTo("Central Bebidas Ltda");

        c.reactivate();
        assertThat(c.isActive()).isTrue();
    }
}
