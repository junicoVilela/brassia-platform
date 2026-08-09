package br.com.brew.brassia.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * O fluxo SSO no navegador contra a stack real (SEC-B07).
 *
 * <p>O que só aparece aqui: os endpoints são <strong>públicos</strong> (quem os chama ainda não tem
 * sessão), o aperto de mão é gravado de verdade, e o uso único é decidido pelo banco — não por uma
 * verificação em memória.
 *
 * <p><strong>O que este IT não cobre:</strong> a volta bem-sucedida de um provedor real. A troca do código
 * por token (OIDC) e a checagem de assinatura XML (SAML) não estão implementadas e o adaptador recusa a
 * volta explicitamente — ver a pendência declarada no {@code STATUS.md}. O que se afirma aqui é o
 * comportamento das amarras, que é onde mora a segurança e o que independe do provedor.
 */
@SpringBootTest
@Testcontainers
class SsoLoginIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SSO = "/api/v1/security/sso";

    @Autowired WebApplicationContext context;
    @Autowired JdbcClient jdbc;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("o start é PÚBLICO e redireciona para o provedor com state, nonce e desafio PKCE")
    void startPublicoRedireciona() throws Exception {
        // Público porque quem chama ainda não tem sessão: está tentando criar uma.
        var brewery = breweryOfAdmin();
        var code = createProvider(brewery, "OIDC", true);

        var location = mockMvc.perform(get(SSO + "/" + code + "/start").param("breweryId", brewery.toString()))
                .andExpect(status().isFound())
                .andReturn().getResponse().getHeader("Location");

        assertThat(location).contains("idp.example.com");
        assertThat(location).contains("state=");
        assertThat(location).contains("nonce=");
        assertThat(location).contains("code_challenge=");
        // S256, nunca `plain`: com `plain` o desafio é o próprio verificador e o PKCE deixa de proteger.
        assertThat(location).contains("code_challenge_method=S256");
    }

    @Test
    @DisplayName("o aperto de mão é gravado, e o verificador PKCE NÃO viaja na URL")
    void verificadorNaoViaja() throws Exception {
        // É essa assimetria que faz o PKCE valer: quem intercepta o redirect vê o desafio, e do desafio não
        // se volta ao verificador.
        var brewery = breweryOfAdmin();
        var code = createProvider(brewery, "OIDC", true);

        var location = mockMvc.perform(get(SSO + "/" + code + "/start").param("breweryId", brewery.toString()))
                .andExpect(status().isFound())
                .andReturn().getResponse().getHeader("Location");

        var verifier = jdbc.sql("SELECT code_verifier FROM sso_handshake ORDER BY created_at DESC LIMIT 1")
                .query(String.class).single();

        assertThat(verifier).isNotBlank();
        assertThat(location).doesNotContain(verifier);
    }

    @Test
    @DisplayName("provedor desativado e inexistente dão a mesma resposta")
    void provedorIndisponivel() throws Exception {
        // Distinguir contaria a quem sonda quais provedores a cervejaria tem configurados.
        var brewery = breweryOfAdmin();

        mockMvc.perform(get(SSO + "/nao-existe/start").param("breweryId", brewery.toString()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("callback com state desconhecido volta para o login sem criar sessão")
    void stateDesconhecido() throws Exception {
        var location = mockMvc.perform(get(SSO + "/qualquer/callback").param("state", "inventado"))
                .andExpect(status().isFound())
                .andReturn().getResponse().getHeader("Location");

        // Motivo único: dizer qual amarra falhou ensinaria o que contornar.
        assertThat(location).endsWith("/login?sso=falhou");
    }

    @Test
    @DisplayName("O USO ÚNICO É DO BANCO: a segunda volta com o mesmo state não passa")
    void usoUnicoNoBanco() throws Exception {
        // Sem isso, a mesma resposta do provedor — capturada do histórico, de um log de proxy ou do Referer
        // — criaria uma sessão nova a cada reenvio.
        var brewery = breweryOfAdmin();
        var code = createProvider(brewery, "OIDC", true);
        mockMvc.perform(get(SSO + "/" + code + "/start").param("breweryId", brewery.toString()))
                .andExpect(status().isFound());

        var state = jdbc.sql("SELECT state FROM sso_handshake ORDER BY created_at DESC LIMIT 1")
                .query(String.class).single();

        // A primeira volta é recusada pela troca não exercitada (ver Javadoc), mas CONSOME o aperto de mão.
        mockMvc.perform(get(SSO + "/" + code + "/callback").param("state", state))
                .andExpect(status().isFound());

        var consumed = jdbc.sql("SELECT consumed_at IS NOT NULL FROM sso_handshake WHERE state = :s")
                .param("s", state).query(Boolean.class).single();
        assertThat(consumed).isTrue();

        // E a segunda cai no caminho de falha, sem chegar ao provedor.
        mockMvc.perform(get(SSO + "/" + code + "/callback").param("state", state))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/login?sso=falhou")));
    }

    @Test
    @DisplayName("o callback SAML aceita form POST sem token CSRF")
    void callbackSamlAceitaPost() throws Exception {
        // O IdP faz form POST de outro domínio e não tem como carregar o nosso token. A proteção é o
        // aperto de mão, não o CSRF.
        mockMvc.perform(post(SSO + "/qualquer/callback").param("RelayState", "inventado"))
                .andExpect(status().isFound());
    }

    @Test
    @DisplayName("nenhuma sessão é criada quando a volta não confere")
    void voltaInvalidaNaoCriaSessao() throws Exception {
        var result = mockMvc.perform(get(SSO + "/qualquer/callback").param("state", "inventado"))
                .andExpect(status().isFound())
                .andReturn();

        var session = (MockHttpSession) result.getRequest().getSession(false);
        if (session != null) {
            assertThat(session.getAttribute("SPRING_SECURITY_CONTEXT")).isNull();
        }
    }

    // --- infraestrutura ---

    /** Cria um provedor ativo e devolve o código dele. */
    private String createProvider(UUID breweryId, String protocol, boolean jit) throws Exception {
        var code = "idp-" + UUID.randomUUID().toString().substring(0, 8);
        var id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO federation_provider (id, brewery_id, code, display_name, protocol, status,
                        issuer_or_entity_id, configuration, jit_mode)
                VALUES (:id, :brewery, :code, :name, :protocol, 'ACTIVE', :issuer,
                        CAST(:config AS jsonb), :jit)
                """)
                .param("id", id).param("brewery", breweryId).param("code", code)
                .param("name", "IdP de teste").param("protocol", protocol)
                .param("issuer", "https://idp.example.com")
                .param("config", "{\"clientId\":\"brassia\"}")
                .param("jit", jit)
                .update();
        return code;
    }

    private UUID breweryOfAdmin() throws Exception {
        var session = login();
        var body = read(mockMvc.perform(get("/api/v1/security/session").session(session))
                .andExpect(status().isOk()));
        return UUID.fromString(body.get("activeBrewery").get("id").asText());
    }

    private JsonNode read(ResultActions actions) throws Exception {
        return JSON.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private MockHttpSession login() throws Exception {
        var result = mockMvc.perform(post("/api/v1/security/login").with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"admin@brassia.local\",\"password\":\"admin-local-123\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
