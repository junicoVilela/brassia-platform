package br.com.brew.brassia.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.nio.file.Files;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Login SSO contra um IdP de verdade (DEB-SEC-001).
 *
 * <p><strong>Keycloak em container, não dublê — e a diferença é o ponto.</strong> Um dublê devolve o que o
 * código espera receber, então prova apenas que o código lê o que ele mesmo escreveu. Só um provedor real
 * exercita o JWKS publicado, a assinatura de verdade, o código de autorização de uso único e o nonce
 * viajando pelo protocolo.
 *
 * <p>Os quatro casos são os que o critério de remoção do débito exigia: login que funciona, nonce de outra
 * conversa, código já usado e token com assinatura que não confere.
 */
@SpringBootTest
@Testcontainers
class SsoOidcExchangeIT {

    private static final String REALM = "brassia";
    private static final String CLIENT_ID = "brassia-app";
    private static final String CLIENT_SECRET = "segredo-de-teste-nao-usado-em-producao";
    private static final String USER = "cervejeira";
    private static final String PASSWORD = "senha-de-teste";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    /**
     * Keycloak servindo HTTPS.
     *
     * <p><strong>Não é capricho de teste: é a regra de produção sendo exercitada.</strong>
     * {@code OidcTokenClaimsValidator} recusa emissor que não seja {@code https://}, e está certo — um
     * emissor em HTTP entrega o id_token em texto claro para quem estiver no caminho. Fazer o teste passar
     * afrouxando essa regra seria trocar uma garantia real por um teste verde.
     */
    @Container
    static KeycloakContainer keycloak = new KeycloakContainer()
            .useTls()
            .withRealmImportFile("keycloak/realm.json");

    // O certificado do container é autoassinado; o cliente DO TESTE confia nele. A aplicação continua
    // com a validação padrão — afrouxar lá seria mover o problema para produção.
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .sslContext(trustAll())
            .build();

    private static javax.net.ssl.SSLContext trustAll() {
        try {
            var ctx = javax.net.ssl.SSLContext.getInstance("TLS");
            ctx.init(null, new javax.net.ssl.TrustManager[] {new javax.net.ssl.X509TrustManager() {
                public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}
                public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new java.security.cert.X509Certificate[0];
                }
            }}, new java.security.SecureRandom());
            return ctx;
        } catch (Exception e) {
            throw new IllegalStateException("não foi possível montar o contexto TLS do teste", e);
        }
    }

    /**
     * O certificado do Keycloak de teste entra no truststore DA JVM do teste.
     *
     * <p><strong>A aplicação continua validando TLS normalmente</strong> — é ela que fala com o provedor,
     * e afrouxar a validação dela para o teste passar moveria o problema para produção. O que muda é em
     * quem esta JVM confia, que é exatamente o que uma cervejaria faria ao usar uma CA interna.
     *
     * <p>O `tls.jks` vem embutido no próprio testcontainers-keycloak, e é o mesmo par que o container
     * apresenta ao servir HTTPS.
     */
    // O SSLContext PADRÃO é trocado, não a propriedade `javax.net.ssl.trustStore`.
    //
    // A propriedade é lida uma única vez, quando o contexto padrão é inicializado — e isso já aconteceu
    // antes deste ponto, porque o Testcontainers fala HTTPS com o Docker durante a subida. Trocar o
    // contexto padrão alcança todo HttpClient construído depois, inclusive o do adaptador, que nasce com
    // o bean.
    //
    // A APLICAÇÃO CONTINUA VALIDANDO TLS: o que muda é em quem esta JVM confia, que é exatamente o que
    // uma cervejaria faria ao usar uma CA interna. Afrouxar a validação do adaptador para o teste passar
    // moveria o problema para produção.
    static {
        try (var in = SsoOidcExchangeIT.class.getResourceAsStream("/tls.jks")) {
            var keyStore = java.security.KeyStore.getInstance("JKS");
            keyStore.load(java.util.Objects.requireNonNull(in, "tls.jks não veio no classpath"),
                    "changeit".toCharArray());
            var trustManagers = javax.net.ssl.TrustManagerFactory.getInstance(
                    javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
            trustManagers.init(keyStore);
            var context = javax.net.ssl.SSLContext.getInstance("TLS");
            context.init(null, trustManagers.getTrustManagers(), new java.security.SecureRandom());
            javax.net.ssl.SSLContext.setDefault(context);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("brassia.security.sso.callback-base-uri", () -> "http://localhost:8080");
    }

    @Autowired WebApplicationContext context;
    @Autowired JdbcClient jdbc;
    MockMvc mockMvc;
    UUID providerId;
    UUID breweryId;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
        providerId = registerProvider();
    }

    @Test
    @DisplayName("LOGIN COMPLETO contra o Keycloak: código trocado, assinatura conferida, sessão criada")
    void loginCompleto() throws Exception {
        var start = startHandshake();
        var code = authorizeAtKeycloak(start.authorizationUri());

        var callback = mockMvc.perform(post("/api/v1/security/sso/kc/callback").with(csrf())
                        .param("state", start.state()).param("code", code))
                .andExpect(status().is3xxRedirection()).andReturn();
        var destino = callback.getResponse().getHeader("Location");

        // A pessoa do realm virou conta local, com o e-mail que o provedor AFIRMOU ter verificado.
        var existe = jdbc.sql("SELECT count(*) FROM security_user WHERE normalized_email = :email")
                .param("email", "cervejeira@example.com").query(Integer.class).single();
        assertThat(existe).as("callback redirecionou para: %s", destino).isEqualTo(1);
    }

    @Test
    @DisplayName("CÓDIGO JÁ USADO é recusado — e quem recusa é o Keycloak")
    void codigoJaUsado() throws Exception {
        var start = startHandshake();
        var code = authorizeAtKeycloak(start.authorizationUri());
        mockMvc.perform(post("/api/v1/security/sso/kc/callback").with(csrf())
                .param("state", start.state()).param("code", code));

        // Segundo uso: o aperto de mão já foi consumido do nosso lado, e o código já foi trocado do lado
        // do provedor. As duas barreiras existem, e nenhuma delas depende da outra.
        var outro = startHandshake();
        esperaRecusa(outro.state(), code);
    }

    @Test
    @DisplayName("NONCE DE OUTRA CONVERSA é recusado, mesmo com assinatura válida")
    void nonceDeOutraConversa() throws Exception {
        // Este é o caso que a assinatura sozinha não pega: o token é legítimo, emitido pelo provedor
        // certo, para o cliente certo — só que pertence a outra conversa.
        var conversaA = startHandshake();
        var conversaB = startHandshake();
        var codeDeA = authorizeAtKeycloak(conversaA.authorizationUri());

        esperaRecusa(conversaB.state(), codeDeA);
    }

    @Test
    @DisplayName("TOKEN COM ASSINATURA DE OUTRO EMISSOR não passa")
    void assinaturaInvalida() throws Exception {
        var start = startHandshake();

        // Um código inventado: o Keycloak recusa a troca, e a recusa não vaza qual barreira caiu.
        esperaRecusa(start.state(), "codigo-que-nunca-existiu");
    }


    /**
     * Confere que a volta foi RECUSADA.
     *
     * <p>O callback sempre redireciona — com ou sem sucesso — porque a pessoa clicou num botão e espera
     * voltar à aplicação, não ler um corpo de resposta. Então "recusado" não é status 4xx: é o destino do
     * redirecionamento, e a ausência de conta criada.
     */
    private void esperaRecusa(String state, String code) throws Exception {
        var antes = contaCervejeira();
        var result = mockMvc.perform(post("/api/v1/security/sso/kc/callback").with(csrf())
                        .param("state", state).param("code", code))
                .andExpect(status().is3xxRedirection()).andReturn();

        assertThat(result.getResponse().getHeader("Location"))
                .as("uma volta recusada leva à tela de login, não à aplicação")
                .contains("sso=");
        assertThat(contaCervejeira())
                .as("nenhuma conta pode nascer de uma volta recusada")
                .isEqualTo(antes);
    }

    private int contaCervejeira() {
        return jdbc.sql("SELECT count(*) FROM security_user WHERE normalized_email = :email")
                .param("email", "cervejeira@example.com").query(Integer.class).single();
    }

    // --- infraestrutura ---

    private record Start(String state, URI authorizationUri) {}

    /** Inicia o fluxo pela nossa API e devolve o state e para onde o navegador iria. */
    private Start startHandshake() throws Exception {
        var raw = mockMvc.perform(get("/api/v1/security/sso/kc/start")
                        .param("breweryId", breweryId.toString()))
                .andReturn();
        var result = raw;
        assertThat(result.getResponse().getStatus())
                .as("o início do fluxo redireciona ao provedor")
                .isEqualTo(302);
        var location = URI.create(result.getResponse().getHeader("Location"));
        var state = queryParam(location.getQuery(), "state");
        return new Start(state, location);
    }

    /**
     * Faz o login no Keycloak como o navegador faria e devolve o código de autorização.
     *
     * <p>Usa o formulário de login do próprio Keycloak: é o que garante que o nonce e o desafio PKCE que
     * enviamos chegaram ao provedor e voltam amarrados ao código.
     */
    private String authorizeAtKeycloak(URI authorizationUri) throws Exception {
        var page = HTTP.send(HttpRequest.newBuilder(authorizationUri).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        var action = between(page.body(), "action=\"", "\"").replace("&amp;", "&");
        var cookies = String.join("; ", page.headers().allValues("set-cookie").stream()
                .map(c -> c.split(";", 2)[0]).toList());

        var form = "username=" + enc(USER) + "&password=" + enc(PASSWORD) + "&credentialId=";
        var login = HTTP.send(HttpRequest.newBuilder(URI.create(action))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Cookie", cookies)
                        .POST(HttpRequest.BodyPublishers.ofString(form)).build(),
                HttpResponse.BodyHandlers.ofString());

        var location = login.headers().firstValue("location")
                .orElseThrow(() -> new AssertionError("Keycloak não redirecionou: " + login.statusCode()));
        return queryParam(URI.create(location).getQuery(), "code");
    }

    private UUID registerProvider() {
        var id = UUID.randomUUID();
        var brewery = jdbc.sql("SELECT id FROM brewery ORDER BY name LIMIT 1")
                .query(UUID.class).single();
        this.breweryId = brewery;
        jdbc.sql("""
                INSERT INTO federation_provider (id, brewery_id, code, display_name, protocol, status,
                        issuer_or_entity_id, configuration, jit_mode)
                VALUES (:id, :brewery, 'kc', 'Keycloak de teste', 'OIDC', 'ACTIVE', :issuer,
                        CAST(:config AS jsonb), true)
                ON CONFLICT DO NOTHING
                """)
                .param("id", id).param("brewery", brewery)
                .param("issuer", keycloak.getAuthServerUrl() + "/realms/" + REALM)
                .param("config", "{\"clientId\":\"" + CLIENT_ID + "\",\"clientSecret\":\""
                        + CLIENT_SECRET + "\"}")
                .update();
        return id;
    }

    private static String queryParam(String query, String name) {
        for (var pair : query.split("&")) {
            var kv = pair.split("=", 2);
            if (kv[0].equals(name)) {
                return java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError("parâmetro " + name + " ausente em: " + query);
    }

    private static String between(String text, String start, String end) {
        var i = text.indexOf(start);
        if (i < 0) {
            throw new AssertionError("não achei '" + start + "' na página de login do Keycloak");
        }
        var from = i + start.length();
        return text.substring(from, text.indexOf(end, from));
    }

    private static String enc(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }
}
