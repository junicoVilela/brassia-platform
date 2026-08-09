package br.com.brew.brassia.security.adapter.outbound.federation;

import br.com.brew.brassia.security.application.port.outbound.FederatedIdentityProvider;
import br.com.brew.brassia.security.application.service.OidcTokenClaimsValidator;
import br.com.brew.brassia.security.application.service.SamlAssertionValidator;
import br.com.brew.brassia.security.domain.InvalidSsoHandshakeException;
import br.com.brew.brassia.security.domain.SsoHandshake;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * A fronteira concreta com o provedor de identidade (SEC-B07).
 *
 * <p><strong>O que está aqui e o que está declarado como pendente.</strong> A montagem da URL de
 * autorização — com state, nonce e desafio PKCE — está completa e é o que o navegador precisa para sair
 * daqui. A verificação da volta <em>reaproveita os validadores que já existiam</em>
 * ({@link OidcTokenClaimsValidator} e {@link SamlAssertionValidator}, de SEC-014/015), que é o que a
 * história pede.
 *
 * <p>O que <strong>não</strong> está: a troca do código por token contra o endpoint real do provedor
 * (OIDC) e a checagem de assinatura XML da assertion (SAML). As duas exigem um IdP de verdade para serem
 * exercitadas, e escrevê-las sem esse exercício produziria código que compila e que ninguém sabe se
 * funciona — ver a pendência declarada no {@code STATUS.md} da sprint. Enquanto isso, este adaptador recusa
 * a volta em vez de aceitá-la: {@code UnsupportedFederationExchangeException} é uma recusa honesta, e é
 * infinitamente melhor que um caminho que aparenta autenticar.
 */
@Component
class OidcFederatedIdentityProvider implements FederatedIdentityProvider {

    private final OidcTokenClaimsValidator oidcValidator = new OidcTokenClaimsValidator();
    private final SamlAssertionValidator samlValidator = new SamlAssertionValidator();
    private final Clock clock = Clock.systemUTC();
    private final String callbackBaseUri;

    OidcFederatedIdentityProvider(
            @Value("${brassia.security.sso.callback-base-uri:http://localhost:8080}") String callbackBaseUri) {
        this.callbackBaseUri = Objects.requireNonNull(callbackBaseUri);
    }

    @Override
    public URI authorizationUri(ProviderConfig config, SsoHandshake handshake) {
        var redirectUri = callbackBaseUri + "/api/v1/security/sso/" + config.code() + "/callback";
        if ("SAML".equalsIgnoreCase(config.protocol())) {
            // SP-initiated: o RelayState carrega o nosso state, que é o que amarra a volta à ida.
            return URI.create(config.issuerOrEntityId()
                    + (config.issuerOrEntityId().contains("?") ? "&" : "?")
                    + "RelayState=" + encode(handshake.state()));
        }
        var clientId = String.valueOf(config.configuration().getOrDefault("clientId", ""));
        oidcValidator.validateProviderConfig(config.issuerOrEntityId(), clientId);
        return URI.create(config.issuerOrEntityId() + "/protocol/openid-connect/auth"
                + "?response_type=code"
                + "&client_id=" + encode(clientId)
                + "&redirect_uri=" + encode(redirectUri)
                + "&scope=" + encode("openid email profile")
                + "&state=" + encode(handshake.state())
                + "&nonce=" + encode(handshake.nonce())
                // S256, nunca `plain`: com `plain` o desafio é o próprio verificador, e o PKCE deixa de
                // proteger contra quem intercepta o redirect.
                + "&code_challenge=" + encode(handshake.codeChallenge())
                + "&code_challenge_method=S256");
    }

    @Override
    public AssertedIdentity verify(ProviderConfig config, SsoHandshake handshake,
            Map<String, String> callback) {
        // Recusa explícita enquanto a troca real não é exercitada contra um IdP. Um caminho que aparentasse
        // autenticar seria muito pior que uma recusa: ele criaria sessão sobre uma verificação que nunca
        // aconteceu.
        throw new UnsupportedFederationExchangeException(config.protocol());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * A troca com o provedor real ainda não foi exercitada.
     *
     * <p>Estende {@link InvalidSsoHandshakeException} para que a borda HTTP a trate como qualquer outra
     * volta que não confere — quem tenta não recebe pista sobre o motivo.
     */
    static final class UnsupportedFederationExchangeException extends InvalidSsoHandshakeException {

        UnsupportedFederationExchangeException(String protocol) {
            super("troca " + protocol + " ainda não exercitada contra provedor real");
        }
    }
}
