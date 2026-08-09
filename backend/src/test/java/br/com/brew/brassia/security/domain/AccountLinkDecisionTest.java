package br.com.brew.brassia.security.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A decisão de vínculo num login federado (SEC-B07).
 *
 * <p>Esta é a parte perigosa de todo SSO. O caminho tentador — "o provedor disse que é ana@cervejaria.com,
 * então logue como a nossa ana@cervejaria.com" — entrega qualquer conta local a quem controlar ou enganar
 * um provedor configurado. Estes testes fixam que <strong>e-mail nunca vincula sozinho a uma conta que já
 * existe</strong>.
 */
class AccountLinkDecisionTest {

    private static final UUID ANA = UUID.randomUUID();
    private static final UUID CONTA_LOCAL = UUID.randomUUID();

    @Test
    @DisplayName("vínculo existente ganha de tudo: é o segundo login de quem já provou os dois lados")
    void vinculoExistenteGanha() {
        var decision = AccountLinkDecision.decide(Optional.of(ANA), Optional.of(CONTA_LOCAL), true, true);

        assertThat(decision.outcome()).isEqualTo(AccountLinkDecision.Outcome.LINK_EXISTS);
        assertThat(decision.userId()).contains(ANA);
    }

    @Test
    @DisplayName("com vínculo, o e-mail asserido agora nem entra na decisão")
    void vinculoIgnoraEmailAtual() {
        // Uma pessoa pode ter trocado de e-mail no provedor sem deixar de ser a mesma pessoa.
        var decision = AccountLinkDecision.decide(Optional.of(ANA), Optional.empty(), false, false);

        assertThat(decision.outcome()).isEqualTo(AccountLinkDecision.Outcome.LINK_EXISTS);
        assertThat(decision.userId()).contains(ANA);
    }

    @Test
    @DisplayName("SEM VÍNCULO E COM CONTA LOCAL DE MESMO E-MAIL: RECUSA — seria sequestro")
    void contaLocalSemVinculoERecusada() {
        // O ataque: quem controla ou engana um provedor configurado afirma o e-mail de um administrador.
        // Vincular aqui entregaria a conta dele. O caminho legítimo é entrar pela conta local e vincular
        // o provedor de dentro dela, provando os dois lados.
        var decision = AccountLinkDecision.decide(Optional.empty(), Optional.of(CONTA_LOCAL), true, true);

        assertThat(decision.outcome()).isEqualTo(AccountLinkDecision.Outcome.REFUSE_WOULD_HIJACK);
        assertThat(decision.userId()).isEmpty();
    }

    @Test
    @DisplayName("nem com JIT ligado e e-mail verificado a conta local é vinculada")
    void nemComTudoLigadoVincula() {
        // JIT e verificação de e-mail não são permissão para tomar conta existente — eles autorizam CRIAR.
        var decision = AccountLinkDecision.decide(Optional.empty(), Optional.of(CONTA_LOCAL), true, true);

        assertThat(decision.outcome()).isEqualTo(AccountLinkDecision.Outcome.REFUSE_WOULD_HIJACK);
    }

    @Test
    @DisplayName("sem conta nenhuma, com JIT e e-mail verificado: cria")
    void provisiona() {
        // Seguro porque não há nada a sequestrar: a conta nasce agora, pertencendo a esta identidade
        // externa desde o primeiro instante.
        var decision = AccountLinkDecision.decide(Optional.empty(), Optional.empty(), true, true);

        assertThat(decision.outcome()).isEqualTo(AccountLinkDecision.Outcome.PROVISION);
    }

    @Test
    @DisplayName("sem JIT habilitado não cria conta")
    void semJitNaoCria() {
        var decision = AccountLinkDecision.decide(Optional.empty(), Optional.empty(), false, true);

        assertThat(decision.outcome()).isEqualTo(AccountLinkDecision.Outcome.REFUSE_WOULD_HIJACK);
    }

    @Test
    @DisplayName("e-mail não verificado não cria conta")
    void emailNaoVerificadoNaoCria() {
        // Sem verificação, qualquer pessoa que consiga um cadastro no provedor escolhe o e-mail com que
        // aparece aqui.
        var decision = AccountLinkDecision.decide(Optional.empty(), Optional.empty(), true, false);

        assertThat(decision.outcome()).isEqualTo(AccountLinkDecision.Outcome.REFUSE_WOULD_HIJACK);
    }

    @Test
    @DisplayName("o e-mail é comparado sem caixa: é a mesma caixa postal")
    void normalizaEmail() {
        assertThat(AccountLinkDecision.normalizeEmail("  Ana@Cervejaria.COM "))
                .isEqualTo("ana@cervejaria.com");
    }

    @Test
    @DisplayName("e-mail ausente é recusado")
    void exigeEmail() {
        assertThatThrownBy(() -> AccountLinkDecision.normalizeEmail(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AccountLinkDecision.normalizeEmail("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
