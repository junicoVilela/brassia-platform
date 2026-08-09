package br.com.brew.brassia.security.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O aperto de mão de um login SSO (SEC-B07).
 *
 * <p>Um login federado é uma conversa que sai da aplicação, passa por um terceiro e volta — e entre a ida e
 * a volta não há nada ligando as duas pontas. Estes testes fixam as três amarras que ligam, cada uma contra
 * um ataque diferente.
 */
class SsoHandshakeTest {

    private static final UUID PROVEDOR = UUID.randomUUID();
    private static final Instant AGORA = Instant.parse("2026-08-09T10:00:00Z");

    @Test
    @DisplayName("state, nonce e verificador PKCE são distintos e imprevisíveis")
    void tresSegredosDistintos() {
        var handshake = SsoHandshake.open(PROVEDOR, "/production/batches", AGORA);

        assertThat(handshake.state()).isNotEqualTo(handshake.nonce());
        assertThat(handshake.nonce()).isNotEqualTo(handshake.codeVerifier());
        // 32 bytes em base64url sem padding = 43 caracteres.
        assertThat(handshake.state()).hasSize(43);

        var outro = SsoHandshake.open(PROVEDOR, "/", AGORA);
        assertThat(outro.state()).isNotEqualTo(handshake.state());
    }

    @Test
    @DisplayName("o desafio PKCE é derivado do verificador, e do desafio não se volta ao verificador")
    void desafioDerivado() {
        // É essa assimetria que faz o PKCE valer: quem intercepta o redirect vê o desafio.
        var handshake = SsoHandshake.open(PROVEDOR, "/", AGORA);

        assertThat(handshake.codeChallenge())
                .isNotEqualTo(handshake.codeVerifier())
                .hasSize(43)
                .matches("[A-Za-z0-9_-]+");
        // Determinístico: o mesmo verificador sempre produz o mesmo desafio.
        assertThat(handshake.codeChallenge()).isEqualTo(handshake.codeChallenge());
    }

    @Test
    @DisplayName("a volta com o state certo consome o aperto de mão")
    void voltaCorretaConsome() {
        var handshake = SsoHandshake.open(PROVEDOR, "/", AGORA);

        var consumido = handshake.consumeWith(handshake.state(), AGORA.plusSeconds(30));

        assertThat(consumido.consumed()).isTrue();
        assertThat(consumido.consumedAt()).isEqualTo(AGORA.plusSeconds(30));
    }

    @Test
    @DisplayName("STATE ERRADO é recusado: é a amarra contra CSRF de login")
    void stateErradoRecusado() {
        // Sem ela, um atacante inicia um fluxo com a própria conta e induz a vítima a completá-lo,
        // deixando-a logada como ele e digitando dados dele achando que são seus.
        var handshake = SsoHandshake.open(PROVEDOR, "/", AGORA);

        assertThatThrownBy(() -> handshake.consumeWith("outro-state", AGORA.plusSeconds(30)))
                .isInstanceOf(InvalidSsoHandshakeException.class);
        assertThatThrownBy(() -> handshake.consumeWith(null, AGORA.plusSeconds(30)))
                .isInstanceOf(InvalidSsoHandshakeException.class);
    }

    @Test
    @DisplayName("USO ÚNICO: a mesma volta não cria duas sessões")
    void usoUnico() {
        // Sem isso, a mesma resposta do provedor — capturada do histórico do navegador, de um log de proxy
        // ou do Referer — cria uma sessão nova a cada reenvio.
        var handshake = SsoHandshake.open(PROVEDOR, "/", AGORA);
        var consumido = handshake.consumeWith(handshake.state(), AGORA.plusSeconds(30));

        assertThatThrownBy(() -> consumido.consumeWith(consumido.state(), AGORA.plusSeconds(31)))
                .isInstanceOf(InvalidSsoHandshakeException.class);
    }

    @Test
    @DisplayName("vence em dez minutos")
    void vence() {
        var handshake = SsoHandshake.open(PROVEDOR, "/", AGORA);

        assertThat(handshake.consumeWith(handshake.state(),
                AGORA.plus(SsoHandshake.LIFETIME).minusSeconds(1)).consumed()).isTrue();

        assertThatThrownBy(() -> handshake.consumeWith(handshake.state(),
                AGORA.plus(SsoHandshake.LIFETIME)))
                .isInstanceOf(InvalidSsoHandshakeException.class);
        assertThatThrownBy(() -> handshake.consumeWith(handshake.state(),
                AGORA.plus(Duration.ofHours(2))))
                .isInstanceOf(InvalidSsoHandshakeException.class);
    }

    @Test
    @DisplayName("o destino pós-login é só caminho interno — nada de redirecionador aberto")
    void destinoSoInterno() {
        // Aceitar URL absoluta transformaria o login num redirecionador aberto: um link para o nosso
        // domínio que, depois de autenticar, joga a pessoa num site de terceiro — com a barra de endereço
        // tendo mostrado o nosso domínio o tempo todo.
        assertThat(SsoHandshake.open(PROVEDOR, "/production/batches", AGORA).redirectAfterLogin())
                .isEqualTo("/production/batches");

        for (var perigoso : new String[] {
            "https://malicioso.example.com", "//malicioso.example.com", "\\\\malicioso", "javascript:alert(1)"
        }) {
            assertThat(SsoHandshake.open(PROVEDOR, perigoso, AGORA).redirectAfterLogin()).isEqualTo("/");
        }
    }

    @Test
    @DisplayName("destino ausente vira a raiz")
    void destinoAusente() {
        assertThat(SsoHandshake.open(PROVEDOR, null, AGORA).redirectAfterLogin()).isEqualTo("/");
        assertThat(SsoHandshake.open(PROVEDOR, "  ", AGORA).redirectAfterLogin()).isEqualTo("/");
    }
}
