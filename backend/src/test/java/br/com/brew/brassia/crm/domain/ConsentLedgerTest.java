package br.com.brew.brassia.crm.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConsentLedgerTest {

    private static final UUID QUEM = UUID.randomUUID();
    private static final Instant MARCO = Instant.parse("2026-03-10T12:00:00Z");
    private static final Instant ABRIL = Instant.parse("2026-04-10T12:00:00Z");
    private static final Instant MAIO = Instant.parse("2026-05-10T12:00:00Z");

    @Test
    void silencioNaoEPermissao() {
        // Quem nunca decidiu nada não é contactável. A ausência de "não" não vale como "sim".
        assertThat(ConsentLedger.empty().allows(ContactPurpose.MARKETING, MAIO)).isFalse();
    }

    @Test
    void oConsentimentoEPorFinalidade() {
        // Aceitar oferta comercial não é aceitar responder pesquisa.
        var l = ConsentLedger.empty();
        l.record(new ConsentEntry(ContactPurpose.MARKETING, ConsentDecision.GRANTED, MARCO, "site", QUEM));

        assertThat(l.allows(ContactPurpose.MARKETING, MAIO)).isTrue();
        assertThat(l.allows(ContactPurpose.SURVEY, MAIO)).isFalse();
    }

    @Test
    void aFinalidadeContratualNaoDependeDeConsentimento() {
        // Sem esta regra, revogar oferta comercial derrubaria junto o aviso de entrega — e a cervejaria
        // ficaria proibida de cumprir o que vendeu.
        var l = ConsentLedger.empty();
        l.record(new ConsentEntry(ContactPurpose.MARKETING, ConsentDecision.REVOKED, MARCO, "telefone", QUEM));

        assertThat(l.allows(ContactPurpose.MARKETING, MAIO)).isFalse();
        assertThat(l.allows(ContactPurpose.TRANSACTIONAL, MAIO)).isTrue();
    }

    @Test
    void naoSeRegistraConsentimentoParaFinalidadeContratual() {
        // Registrar daria a entender que o aviso de entrega depende de consentimento, e abriria a porta
        // para alguém "revogá-lo".
        assertThatThrownBy(() -> new ConsentEntry(ContactPurpose.TRANSACTIONAL, ConsentDecision.GRANTED,
                MARCO, "contrato", QUEM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("não se apoia em consentimento");
    }

    @Test
    void revogarNaoApagaOQueValiaAntes() {
        // A pergunta que a auditoria faz é "ela aceitava quando mandamos?", e não "ela aceita hoje?".
        var l = ConsentLedger.empty();
        l.record(new ConsentEntry(ContactPurpose.MARKETING, ConsentDecision.GRANTED, MARCO, "site", QUEM));
        l.record(new ConsentEntry(ContactPurpose.MARKETING, ConsentDecision.REVOKED, MAIO, "e-mail", QUEM));

        assertThat(l.allows(ContactPurpose.MARKETING, ABRIL)).isTrue();
        assertThat(l.allows(ContactPurpose.MARKETING, MAIO)).isFalse();
        assertThat(l.entries()).hasSize(2);
    }

    @Test
    void decisaoPosteriorNaoContaminaAConsultaDoPassado() {
        // Perguntar "podia em março?" e receber a resposta de hoje é o erro que faz a auditoria concluir
        // infração onde não houve.
        var l = ConsentLedger.empty();
        l.record(new ConsentEntry(ContactPurpose.MARKETING, ConsentDecision.GRANTED, ABRIL, "site", QUEM));

        assertThat(l.allows(ContactPurpose.MARKETING, MARCO)).isFalse();
        assertThat(l.decisionAt(ContactPurpose.MARKETING, MARCO)).isEmpty();
    }

    @Test
    void valeAOrdemDoMundoENaoADaDigitacao() {
        // Decisão tomada por telefone na segunda pode ser digitada na quarta, depois de outra.
        var l = ConsentLedger.empty();
        l.record(new ConsentEntry(ContactPurpose.MARKETING, ConsentDecision.REVOKED, MAIO, "e-mail", QUEM));
        l.record(new ConsentEntry(ContactPurpose.MARKETING, ConsentDecision.GRANTED, MARCO, "site", QUEM));

        // A revogação é de maio: ela continua sendo a última, mesmo tendo sido inserida primeiro.
        assertThat(l.allows(ContactPurpose.MARKETING, MAIO)).isFalse();
        assertThat(l.allows(ContactPurpose.MARKETING, ABRIL)).isTrue();
    }

    @Test
    void concederDeNovoCriaUmaEntradaNovaENaoAlteraAAntiga() {
        var l = ConsentLedger.empty();
        l.record(new ConsentEntry(ContactPurpose.MARKETING, ConsentDecision.GRANTED, MARCO, "site", QUEM));
        l.record(new ConsentEntry(ContactPurpose.MARKETING, ConsentDecision.REVOKED, ABRIL, "e-mail", QUEM));
        l.record(new ConsentEntry(ContactPurpose.MARKETING, ConsentDecision.GRANTED, MAIO, "balcão", QUEM));

        assertThat(l.allows(ContactPurpose.MARKETING, MAIO)).isTrue();
        assertThat(l.entries()).hasSize(3);
        assertThat(l.entries().getFirst().at()).isEqualTo(MARCO);
        assertThat(l.entries().getLast().source()).isEqualTo("balcão");
    }

    @Test
    void aOrigemDaDecisaoEObrigatoria() {
        // Consentimento que não se consegue demonstrar vale o mesmo que nenhum.
        assertThatThrownBy(() -> new ConsentEntry(ContactPurpose.MARKETING, ConsentDecision.GRANTED, MARCO,
                "  ", QUEM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("origem");
    }

    @Test
    void oHistoricoNaoSeAlteraPorFora() {
        var l = ConsentLedger.empty();
        l.record(new ConsentEntry(ContactPurpose.MARKETING, ConsentDecision.GRANTED, MARCO, "site", QUEM));

        assertThatThrownBy(() -> l.entries().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(l.entries()).hasSize(1);
    }
}
