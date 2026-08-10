package br.com.brew.brassia.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Login SAML contra o Keycloak em modo IdP SAML (DEB-SEC-001, parte SAML).
 *
 * <p><strong>Não precisou de imagem própria.</strong> O Keycloak fala SAML nativamente, e é o mesmo
 * container do IT de OIDC com um cliente a mais no realm — o que também torna o custo de CI incremental
 * em vez de dobrado.
 *
 * <p>O que só um IdP real exercita: a assinatura XML de verdade, com o certificado que ele publica nos
 * metadados, sobre um documento que ele montou. Uma assertion escrita à mão pelo teste provaria que o
 * parser lê o que o teste escreveu.
 */
@SpringBootTest
@Testcontainers
class SsoSamlExchangeIT {

    private static final String REALM = "brassia";
    private static final String SAML_CLIENT = "https://brassia.local/saml";
    private static final String USER = "cervejeira";
    private static final String PASSWORD = "senha-de-teste";

    // Mesmo truststore do IT de OIDC: a aplicação continua validando TLS; o que muda é em quem esta JVM
    // confia. Ver SsoOidcExchangeIT para o porquê de ser SSLContext e não propriedade.
    static {
        try (var in = SsoSamlExchangeIT.class.getResourceAsStream("/tls.jks")) {
            var keyStore = java.security.KeyStore.getInstance("JKS");
            keyStore.load(java.util.Objects.requireNonNull(in), "changeit".toCharArray());
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

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Container
    static KeycloakContainer keycloak = new KeycloakContainer()
            .useTls()
            .withRealmImportFile("keycloak/realm.json");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER).build();

    @Autowired org.springframework.web.context.WebApplicationContext context;
    @Autowired JdbcClient jdbc;
    MockMvc mockMvc;
    UUID breweryId;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
        breweryId = jdbc.sql("SELECT id FROM brewery ORDER BY name LIMIT 1").query(UUID.class).single();
        registrarProvedorSaml();
    }

    @Test
    @DisplayName("LOGIN SAML COMPLETO: assertion assinada pelo IdP cria a conta")
    void loginCompleto() throws Exception {
        var start = iniciar();
        var samlResponse = autenticarNoKeycloak(start);

        mockMvc.perform(post("/api/v1/security/sso/kc-saml/callback").with(csrf())
                        .param("SAMLResponse", samlResponse)
                        .param("RelayState", start.state()))
                .andExpect(status().is3xxRedirection());

        var existe = jdbc.sql("SELECT count(*) FROM security_user WHERE normalized_email = :email")
                .param("email", "cervejeira@example.com").query(Integer.class).single();
        assertThat(existe).isEqualTo(1);
    }

    @Test
    @DisplayName("ASSERTION ADULTERADA é recusada — a assinatura não fecha")
    void assertionAdulterada() throws Exception {
        // Um byte trocado no XML invalida a assinatura. É o teste que prova que a verificação acontece:
        // sem ela, o conteúdo alterado passaria e o e-mail do atacante viraria conta.
        var start = iniciar();
        var original = autenticarNoKeycloak(start);
        var xml = new String(java.util.Base64.getMimeDecoder().decode(original), StandardCharsets.UTF_8);
        var adulterado = xml.replace("cervejeira@example.com", "invasor@example.com");
        var reencodado = java.util.Base64.getEncoder()
                .encodeToString(adulterado.getBytes(StandardCharsets.UTF_8));

        var antes = contas("invasor@example.com");
        mockMvc.perform(post("/api/v1/security/sso/kc-saml/callback").with(csrf())
                        .param("SAMLResponse", reencodado).param("RelayState", start.state()))
                .andExpect(status().is3xxRedirection());

        assertThat(contas("invasor@example.com"))
                .as("nenhuma conta pode nascer de assertion adulterada")
                .isEqualTo(antes);
    }

    @Test
    @DisplayName("resposta sem SAMLResponse é recusada")
    void semResposta() throws Exception {
        var start = iniciar();

        mockMvc.perform(post("/api/v1/security/sso/kc-saml/callback").with(csrf())
                        .param("RelayState", start.state()))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("RelayState de outra conversa não aproveita a assertion")
    void relayStateDeOutraConversa() throws Exception {
        var conversaA = iniciar();
        var conversaB = iniciar();
        var assertionDeA = autenticarNoKeycloak(conversaA);

        var antes = contas("cervejeira@example.com");
        mockMvc.perform(post("/api/v1/security/sso/kc-saml/callback").with(csrf())
                        .param("SAMLResponse", assertionDeA).param("RelayState", conversaB.state()))
                .andExpect(status().is3xxRedirection());
        // A assertion é válida e assinada; o que não confere é a conversa. O destino da assertion aponta
        // para o nosso endpoint, mas o aperto de mão B nunca pediu esta ida.
        assertThat(contas("cervejeira@example.com")).isEqualTo(antes);
    }

    // --- infraestrutura ---

    private record Start(String state, URI authorizationUri) {}

    private Start iniciar() throws Exception {
        var result = mockMvc.perform(get("/api/v1/security/sso/kc-saml/start")
                        .param("breweryId", breweryId.toString()))
                .andExpect(status().is3xxRedirection()).andReturn();
        var location = URI.create(result.getResponse().getHeader("Location"));
        var state = param(location.getQuery(), "RelayState");
        return new Start(state, location);
    }

    /** Faz o login no Keycloak como o navegador faria e devolve o SAMLResponse do form de retorno. */
    private String autenticarNoKeycloak(Start start) throws Exception {
        var page = HTTP.send(HttpRequest.newBuilder(start.authorizationUri()).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        var action = entre(page.body(), "action=\"", "\"").replace("&amp;", "&");
        var cookies = String.join("; ", page.headers().allValues("set-cookie").stream()
                .map(c -> c.split(";", 2)[0]).toList());

        var form = "username=" + enc(USER) + "&password=" + enc(PASSWORD) + "&credentialId=";
        var login = HTTP.send(HttpRequest.newBuilder(URI.create(action))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Cookie", cookies)
                        .POST(HttpRequest.BodyPublishers.ofString(form)).build(),
                HttpResponse.BodyHandlers.ofString());

        // O Keycloak devolve um form auto-submit com o SAMLResponse em campo oculto.
        return entre(login.body(), "name=\"SAMLResponse\" value=\"", "\"").replace("&#13;", "");
    }

    private void registrarProvedorSaml() {
        var certificate = certificadoDoRealm();
        jdbc.sql("""
                INSERT INTO federation_provider (id, brewery_id, code, display_name, protocol, status,
                        issuer_or_entity_id, configuration, jit_mode)
                VALUES (:id, :brewery, 'kc-saml', 'Keycloak SAML', 'SAML', 'ACTIVE', :issuer,
                        CAST(:config AS jsonb), true)
                ON CONFLICT DO NOTHING
                """)
                .param("id", UUID.randomUUID()).param("brewery", breweryId)
                .param("issuer", keycloak.getAuthServerUrl() + "/realms/" + REALM)
                .param("config", JSON.createObjectNode()
                        .put("certificate", certificate)
                        .put("audience", SAML_CLIENT)
                        .put("ssoUrl", keycloak.getAuthServerUrl() + "/realms/" + REALM
                                + "/protocol/saml")
                        .toString())
                .update();
    }

    /** Lê o certificado de assinatura dos metadados SAML publicados pelo realm. */
    private String certificadoDoRealm() {
        try {
            var metadata = HTTP.send(HttpRequest.newBuilder(URI.create(
                            keycloak.getAuthServerUrl() + "/realms/" + REALM
                                    + "/protocol/saml/descriptor")).GET().build(),
                    HttpResponse.BodyHandlers.ofString()).body();
            return entre(metadata, "<ds:X509Certificate>", "</ds:X509Certificate>").trim();
        } catch (Exception e) {
            throw new IllegalStateException("não foi possível ler os metadados SAML do Keycloak", e);
        }
    }

    private int contas(String email) {
        return jdbc.sql("SELECT count(*) FROM security_user WHERE normalized_email = :email")
                .param("email", email).query(Integer.class).single();
    }

    private static String param(String query, String name) {
        for (var pair : query.split("&")) {
            var kv = pair.split("=", 2);
            if (kv[0].equals(name)) {
                return java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError("parâmetro " + name + " ausente em: " + query);
    }

    private static String entre(String text, String inicio, String fim) {
        var i = text.indexOf(inicio);
        if (i < 0) {
            throw new AssertionError("não achei '" + inicio + "' na resposta do Keycloak");
        }
        var from = i + inicio.length();
        return text.substring(from, text.indexOf(fim, from));
    }

    private static String enc(String v) {
        return java.net.URLEncoder.encode(v, StandardCharsets.UTF_8);
    }
}
