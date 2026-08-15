package br.com.brew.brassia.crm.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ContactTest {

    private static final UUID CERVEJARIA = UUID.randomUUID();
    private static final UUID CLIENTE = UUID.randomUUID();
    private static final UUID QUEM = UUID.randomUUID();
    private static final Instant MARCO = Instant.parse("2026-03-10T12:00:00Z");
    private static final Instant MAIO = Instant.parse("2026-05-10T12:00:00Z");

    private static Contact contato() {
        return Contact.create(UUID.randomUUID(), CERVEJARIA, CLIENTE, "Ana Ribeiro",
                "ana@barcentral.com.br", "+55 11 90000-0000", "compras");
    }

    @Test
    void oNomeEObrigatorioEORestoNao() {
        // Um contato de quem só se tem o telefone continua sendo um contato.
        var c = Contact.create(UUID.randomUUID(), CERVEJARIA, CLIENTE, "Ana", null, null, null);
        assertThat(c.name()).contains("Ana");
        assertThat(c.email()).isEmpty();

        assertThatThrownBy(() -> Contact.create(UUID.randomUUID(), CERVEJARIA, CLIENTE, " ", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nome");
    }

    @Test
    void oConsentimentoPassaPeloContatoEValeNoInstanteConsultado() {
        var c = contato();
        c.grant(ContactPurpose.MARKETING, MARCO, "formulário do site", QUEM);

        assertThat(c.allows(ContactPurpose.MARKETING, MAIO)).isTrue();
        assertThat(c.allows(ContactPurpose.SURVEY, MAIO)).isFalse();
    }

    @Test
    void anonimizarApagaAPessoaEMantemACasca() {
        // A casca é o que impede o histórico de expedição de ficar com um buraco inexplicável.
        var c = contato();
        var id = c.id();
        c.grant(ContactPurpose.MARKETING, MARCO, "site", QUEM);

        c.anonymize(MAIO);

        assertThat(c.id()).isEqualTo(id);
        assertThat(c.customerId()).isEqualTo(CLIENTE);
        assertThat(c.name()).isEmpty();
        assertThat(c.email()).isEmpty();
        assertThat(c.phone()).isEmpty();
        assertThat(c.role()).isEmpty();
        assertThat(c.isAnonymized()).isTrue();
        assertThat(c.anonymizedAt()).contains(MAIO);
    }

    @Test
    void oHistoricoDeConsentimentoSobreviveAAnonimizacao() {
        // É o registro de que ela pediu para sair. Sem ele, a cervejaria não demonstra que atendeu.
        var c = contato();
        c.grant(ContactPurpose.MARKETING, MARCO, "site", QUEM);

        c.anonymize(MAIO);

        assertThat(c.consents().entries()).hasSize(1);
    }

    @Test
    void contatoAnonimizadoNaoRecebeNadaNemTransacional() {
        // A base contratual autoriza mandar o aviso de entrega; ela não cria um endereço para onde mandar.
        var c = contato();
        c.anonymize(MARCO);

        assertThat(c.allows(ContactPurpose.TRANSACTIONAL, MAIO)).isFalse();
        assertThat(c.allows(ContactPurpose.MARKETING, MAIO)).isFalse();
    }

    @Test
    void naoSePedeNemSeRevogaConsentimentoDeQuemNaoExisteMais() {
        var c = contato();
        c.anonymize(MARCO);

        assertThatThrownBy(() -> c.grant(ContactPurpose.MARKETING, MAIO, "site", QUEM))
                .isInstanceOf(ContactAnonymizedException.class);
        assertThatThrownBy(() -> c.revoke(ContactPurpose.MARKETING, MAIO, "site", QUEM))
                .isInstanceOf(ContactAnonymizedException.class);
    }

    @Test
    void anonimizarDuasVezesERecusado() {
        // Não é inofensivo: a segunda chamada sobrescreveria a data do apagamento, que é a prova de
        // quando ele foi feito.
        var c = contato();
        c.anonymize(MARCO);

        assertThatThrownBy(() -> c.anonymize(MAIO)).isInstanceOf(ContactAnonymizedException.class);
        assertThat(c.anonymizedAt()).contains(MARCO);
    }
}
