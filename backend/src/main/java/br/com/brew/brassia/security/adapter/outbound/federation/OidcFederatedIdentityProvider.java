package br.com.brew.brassia.security.adapter.outbound.federation;

import br.com.brew.brassia.security.application.port.outbound.FederatedIdentityProvider;
import br.com.brew.brassia.security.application.service.OidcTokenClaimsValidator;
import br.com.brew.brassia.security.application.service.SamlAssertionValidator;
import br.com.brew.brassia.security.domain.InvalidSsoHandshakeException;
import br.com.brew.brassia.security.domain.SsoHandshake;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
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
 * <p><strong>A troca OIDC agora é real</strong> (DEB-SEC-001): o código é trocado por token no endpoint do
 * provedor, com o verificador PKCE, e o ID token tem a <em>assinatura conferida contra o JWKS</em> antes de
 * qualquer coisa dele ser lida. É exercitada contra um Keycloak em Testcontainers — não contra um dublê,
 * porque um dublê que devolve o que o código espera não prova integração nenhuma.
 *
 * <p><strong>SAML também é real agora.</strong> A assinatura XML da assertion é conferida contra o
 * certificado do provedor — e na <em>assertion</em>, não só no envelope, que é a diferença explorada pelos
 * ataques de XML Signature Wrapping. Exercitado contra o Keycloak em modo SAML.
 */
@Component
class OidcFederatedIdentityProvider implements FederatedIdentityProvider {

    private static final Duration EXCHANGE_TIMEOUT = Duration.ofSeconds(10);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final OidcTokenClaimsValidator oidcValidator = new OidcTokenClaimsValidator();
    private final Map<String, JwtDecoder> decoders = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            // Nunca seguir redirecionamento na troca: um 302 mandaria o código de autorização e o
            // client_secret para onde o provedor comprometido quisesse.
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
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
            return samlAuthnRequestUri(config, handshake, redirectUri);
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

    /**
     * Troca o código por token e verifica quem voltou (DEB-SEC-001).
     *
     * <p>A ordem importa e é esta: <strong>assinatura primeiro, conteúdo depois</strong>. Ler o
     * {@code sub} ou o {@code email} de um JWT antes de conferir a assinatura é ler o que o atacante
     * escreveu — o token é texto até a assinatura provar o contrário.
     */
    @Override
    public AssertedIdentity verify(ProviderConfig config, SsoHandshake handshake,
            Map<String, String> callback) {
        if ("SAML".equalsIgnoreCase(config.protocol())) {
            return verifySaml(config, handshake, callback);
        }
        if (!"OIDC".equalsIgnoreCase(config.protocol())) {
            throw new UnsupportedFederationExchangeException(config.protocol());
        }
        var code = callback.get("code");
        if (code == null || code.isBlank()) {
            throw new InvalidSsoHandshakeException("volta sem código de autorização");
        }

        var clientId = String.valueOf(config.configuration().getOrDefault("clientId", ""));
        var clientSecret = String.valueOf(config.configuration().getOrDefault("clientSecret", ""));
        var idToken = exchange(config, handshake, code, clientId, clientSecret);
        var jwt = decode(config, clientId, idToken);

        // O nonce amarra ESTE token a ESTA conversa. Sem ele, um token válido capturado de outra sessão
        // do mesmo provedor seria aceito aqui — assinatura correta, conversa errada.
        oidcValidator.validate(
                new OidcTokenClaimsValidator.Claims(jwt.getIssuer().toString(), jwt.getSubject(),
                        jwt.getClaimAsString("nonce"), handshake.state(), handshake.codeChallenge()),
                new OidcTokenClaimsValidator.Context(config.issuerOrEntityId(), handshake.nonce(),
                        handshake.state(), handshake.codeChallenge()));

        return new AssertedIdentity(
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                // Ausente conta como NÃO verificado: um provedor que não afirma a verificação não a fez,
                // e tratar silêncio como "sim" abriria o provisionamento automático.
                Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")),
                jwt.getClaimAsString("name"));
    }

    /**
     * Monta o {@code AuthnRequest} e o redirect para o IdP (binding HTTP-Redirect).
     *
     * <p><strong>O RelayState carrega o nosso state</strong>, e é o que amarra a volta à ida: o SAML não
     * tem nonce, então é o RelayState — devolvido pelo IdP sem alteração — que diz a qual conversa a
     * assertion pertence.
     *
     * <p>O corpo vai <em>deflacionado</em> e em base64 porque é o que o binding HTTP-Redirect exige: XML
     * cru numa query string estoura o limite de URL no primeiro atributo mais longo.
     *
     * <p>O {@code AssertionConsumerServiceURL} viaja no pedido e é conferido na volta contra o
     * {@code Recipient} da assertion. Sem isso, uma assertion emitida para outro serviço do mesmo IdP
     * chegaria aqui com assinatura válida.
     */
    private URI samlAuthnRequestUri(ProviderConfig config, SsoHandshake handshake, String redirectUri) {
        var ssoUrl = String.valueOf(config.configuration()
                .getOrDefault("ssoUrl", config.issuerOrEntityId() + "/protocol/saml"));
        var entityId = String.valueOf(config.configuration().getOrDefault("audience", ""));
        if (entityId.isBlank()) {
            throw new InvalidSsoHandshakeException("provedor SAML sem identificador de serviço");
        }

        var authnRequest = """
                <samlp:AuthnRequest xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol" \
                xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion" ID="%s" Version="2.0" \
                IssueInstant="%s" Destination="%s" \
                ProtocolBinding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST" \
                AssertionConsumerServiceURL="%s">\
                <saml:Issuer>%s</saml:Issuer></samlp:AuthnRequest>"""
                .formatted("_" + handshake.id(), clock.instant(), ssoUrl, redirectUri, entityId)
                .replace("\\\n", "").replace("\n", "");

        return URI.create(ssoUrl
                + (ssoUrl.contains("?") ? "&" : "?")
                + "SAMLRequest=" + encode(deflateAndEncode(authnRequest))
                + "&RelayState=" + encode(handshake.state()));
    }

    /** DEFLATE cru (sem cabeçalho zlib) seguido de base64, como o binding HTTP-Redirect define. */
    private static String deflateAndEncode(String xml) {
        var deflater = new java.util.zip.Deflater(java.util.zip.Deflater.DEFLATED, true);
        deflater.setInput(xml.getBytes(StandardCharsets.UTF_8));
        deflater.finish();
        var buffer = new java.io.ByteArrayOutputStream();
        var chunk = new byte[1024];
        while (!deflater.finished()) {
            buffer.write(chunk, 0, deflater.deflate(chunk));
        }
        deflater.end();
        return java.util.Base64.getEncoder().encodeToString(buffer.toByteArray());
    }

    /**
     * Verifica a volta SAML.
     *
     * <p>A ordem é a mesma do OIDC e pelo mesmo motivo: <strong>assinatura primeiro, conteúdo depois</strong>.
     *
     * <p>Depois da assinatura vêm as checagens que ela <em>não</em> faz — e cada uma pega um ataque
     * diferente. Assinatura válida prova que o IdP emitiu aquilo; não prova que era para nós
     * ({@code Audience}), nem para este endpoint ({@code Destination}), nem que ainda vale
     * ({@code NotOnOrAfter}). Uma assertion legítima capturada de outro serviço do mesmo IdP passa na
     * assinatura e falha na audiência.
     */
    private AssertedIdentity verifySaml(ProviderConfig config, SsoHandshake handshake,
            Map<String, String> callback) {
        var samlResponse = callback.get("SAMLResponse");
        if (samlResponse == null || samlResponse.isBlank()) {
            throw new InvalidSsoHandshakeException("volta sem resposta SAML");
        }
        var certificate = String.valueOf(config.configuration().getOrDefault("certificate", ""));
        if (certificate.isBlank()) {
            throw new InvalidSsoHandshakeException("provedor SAML sem certificado cadastrado");
        }

        var assertion = SamlResponseVerifier.verify(samlResponse, certificate);

        var conditions = assertion.getConditions();
        samlValidator.validate(
                new SamlAssertionValidator.Assertion(
                        assertion.getIssuer() == null ? null : assertion.getIssuer().getValue(),
                        audienceOf(assertion),
                        destinationOf(assertion),
                        conditions == null ? null : conditions.getNotBefore(),
                        conditions == null ? null : conditions.getNotOnOrAfter()),
                new SamlAssertionValidator.Context(config.issuerOrEntityId(),
                        String.valueOf(config.configuration().getOrDefault("audience", "")),
                        redirectUri(config), clock.instant()));

        var subject = assertion.getSubject() == null || assertion.getSubject().getNameID() == null
                ? null : assertion.getSubject().getNameID().getValue();
        if (subject == null || subject.isBlank()) {
            // Sem NameID não há quem: uma assertion válida sobre ninguém não autentica ninguém.
            throw new InvalidSsoHandshakeException("resposta SAML sem identificador de sujeito");
        }

        var email = first(SamlResponseVerifier.attributeValues(assertion, "email"))
                .orElseGet(() -> first(SamlResponseVerifier.attributeValues(assertion,
                        "urn:oid:1.2.840.113549.1.9.1")).orElse(subject));
        return new AssertedIdentity(subject, email,
                // SAML não tem um `email_verified` padronizado. Tratar a ausência como verificado abriria
                // o provisionamento automático a quem escolhesse o próprio e-mail no IdP — então a
                // ausência conta como NÃO verificado, igual ao OIDC.
                Boolean.parseBoolean(first(SamlResponseVerifier.attributeValues(assertion,
                        "emailVerified")).orElse("false")),
                first(SamlResponseVerifier.attributeValues(assertion, "displayName")).orElse(subject));
    }

    private static String audienceOf(org.opensaml.saml.saml2.core.Assertion assertion) {
        var conditions = assertion.getConditions();
        if (conditions == null) {
            return null;
        }
        return conditions.getAudienceRestrictions().stream()
                .flatMap(r -> r.getAudiences().stream())
                .map(a -> a.getURI())
                .findFirst().orElse(null);
    }

    private static String destinationOf(org.opensaml.saml.saml2.core.Assertion assertion) {
        return assertion.getSubject() == null ? null
                : assertion.getSubject().getSubjectConfirmations().stream()
                        .map(c -> c.getSubjectConfirmationData())
                        .filter(java.util.Objects::nonNull)
                        .map(d -> d.getRecipient())
                        .filter(java.util.Objects::nonNull)
                        .findFirst().orElse(null);
    }

    private static java.util.Optional<String> first(java.util.List<String> values) {
        return values.isEmpty() ? java.util.Optional.empty()
                : java.util.Optional.ofNullable(values.getFirst());
    }

    /** POST no endpoint de token, com o verificador PKCE que fecha o desafio enviado na ida. */
    private String exchange(ProviderConfig config, SsoHandshake handshake, String code,
            String clientId, String clientSecret) {
        var form = "grant_type=authorization_code"
                + "&code=" + encode(code)
                + "&redirect_uri=" + encode(redirectUri(config))
                + "&client_id=" + encode(clientId)
                + "&code_verifier=" + encode(handshake.codeVerifier())
                + (clientSecret.isBlank() ? "" : "&client_secret=" + encode(clientSecret));

        var request = HttpRequest.newBuilder(URI.create(tokenEndpoint(config)))
                .timeout(EXCHANGE_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                // O corpo do erro do provedor NÃO entra na exceção: ele repete o código de autorização e
                // às vezes o client_id, e essa mensagem chega perto de log e de tela.
                throw new InvalidSsoHandshakeException(
                        "provedor recusou a troca (HTTP " + response.statusCode() + ")");
            }
            var idToken = JSON.readTree(response.body()).path("id_token").asText(null);
            if (idToken == null || idToken.isBlank()) {
                // Sem id_token não há identidade: um access_token sozinho diz o que se pode fazer, não
                // quem se é. Aceitar aqui seria autenticar por autorização.
                throw new InvalidSsoHandshakeException("provedor não devolveu id_token");
            }
            return idToken;
        } catch (java.io.IOException e) {
            // O TIPO do erro entra, a mensagem não: "SSLHandshakeException" e "ConnectException" apontam
            // problemas operacionais diferentes, e distinguir vale muito num incidente. A mensagem, essa,
            // costuma carregar a URL inteira do provedor.
            throw new InvalidSsoHandshakeException(
                    "falha ao falar com o provedor: " + e.getClass().getSimpleName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InvalidSsoHandshakeException("troca interrompida");
        }
    }

    /**
     * Decodifica conferindo a assinatura contra o JWKS do provedor.
     *
     * <p>O decodificador é cacheado por emissor porque ele guarda as chaves do JWKS; criar um por
     * requisição buscaria o JWKS a cada login — carga no provedor e latência no caminho de autenticação.
     */
    private Jwt decode(ProviderConfig config, String clientId, String idToken) {
        var decoder = decoders.computeIfAbsent(config.issuerOrEntityId(), issuer -> {
            var jwtDecoder = NimbusJwtDecoder.withJwkSetUri(issuer + "/protocol/openid-connect/certs")
                    .build();
            jwtDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
            return jwtDecoder;
        });
        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (JwtException e) {
            // Assinatura inválida, expirado, emissor errado — todos viram a mesma recusa. Distinguir
            // diria a quem tenta qual barreira ele passou.
            throw new InvalidSsoHandshakeException("token do provedor não confere");
        }
        // A audiência é conferida aqui e não no validador de claims: `aud` é lista, e um token emitido
        // para OUTRO cliente do mesmo provedor tem assinatura válida e emissor correto.
        if (!jwt.getAudience().contains(clientId)) {
            throw new InvalidSsoHandshakeException("token do provedor não confere");
        }
        return jwt;
    }

    private String redirectUri(ProviderConfig config) {
        return callbackBaseUri + "/api/v1/security/sso/" + config.code() + "/callback";
    }

    private static String tokenEndpoint(ProviderConfig config) {
        return config.issuerOrEntityId() + "/protocol/openid-connect/token";
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
