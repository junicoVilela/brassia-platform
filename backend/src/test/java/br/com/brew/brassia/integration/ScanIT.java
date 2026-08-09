package br.com.brew.brassia.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import br.com.brew.brassia.shared.security.SecurityPrincipal;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Leitura de código (INT-003).
 *
 * <p>O que este IT prova é o critério da história contra a stack real: <strong>o código não concede
 * acesso</strong>. Um QR colado num tanque é legível por quem entra na sala, e o que ele contém é apenas a
 * pergunta — a autorização é verificada depois, e é a mesma de quem chega pelo menu.
 */
@SpringBootTest
@Testcontainers
class ScanIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final String SCAN = "/api/v1/integration/scan";

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("resolve o código e diz para onde ir")
    void resolve() throws Exception {
        var session = login();

        mockMvc.perform(get(SCAN).param("code", "brassia://equipamento/TANQUE-01").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("equipamento"))
                .andExpect(jsonPath("$.identifier").value("TANQUE-01"))
                .andExpect(jsonPath("$.route").value("/equipment"));
    }

    @Test
    @DisplayName("lê a etiqueta de envase JÁ IMPRESSA, com o sufixo do plano")
    void leEtiquetaJaImpressa() throws Exception {
        // PKG-004 imprime `brassia://lote/<código>/envase/<plano>`. Recusar o sufixo invalidaria toda
        // etiqueta que já está colada numa caixa.
        var session = login();

        mockMvc.perform(get(SCAN).param("code", "brassia://lote/LOTE-2026-014/envase/ENV-3").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("lote"))
                .andExpect(jsonPath("$.identifier").value("LOTE-2026-014"));
    }

    @Test
    @DisplayName("O CÓDIGO NÃO CONCEDE ACESSO: sem a permissão do tipo, 403")
    void codigoNaoConcedeAcesso() throws Exception {
        // O critério central. Quem apontou a câmera para uma etiqueta real recebe 403 — e não uma tela
        // vazia nem um "não encontrado". A resposta honesta é que ela não pode ver aquilo.
        var brewery = breweryOf(login());
        var semAlcada = principal(brewery, Set.of("integration.webhook.read"));

        mockMvc.perform(get(SCAN).param("code", "brassia://equipamento/TANQUE-01")
                        .with(authentication(semAlcada)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a permissão exigida é a DO TIPO apontado, não uma permissão de leitura genérica")
    void permissaoEDoTipo() throws Exception {
        // Quem pode ver equipamento não passa a ver lote por ter lido um QR de lote.
        var brewery = breweryOf(login());
        var soEquipamento = principal(brewery, Set.of("equipment.read"));

        mockMvc.perform(get(SCAN).param("code", "brassia://equipamento/TANQUE-01")
                        .with(authentication(soEquipamento)))
                .andExpect(status().isOk());

        mockMvc.perform(get(SCAN).param("code", "brassia://lote/LOTE-1")
                        .with(authentication(soEquipamento)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("sem cervejaria ativa não resolve: sem tenant não há alçada que valha")
    void semCervejariaNaoResolve() throws Exception {
        var semTenant = principal(null, Set.of("equipment.read"));

        mockMvc.perform(get(SCAN).param("code", "brassia://equipamento/TANQUE-01")
                        .with(authentication(semTenant)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("sem sessão nenhuma, não responde")
    void semSessaoNaoResponde() throws Exception {
        mockMvc.perform(get(SCAN).param("code", "brassia://equipamento/TANQUE-01"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("código de tipo desconhecido responde 422 com a mesma mensagem de qualquer outro erro")
    void tipoDesconhecidoE422() throws Exception {
        // A uniformidade é deliberada: distinguir os motivos ensinaria quais tipos existem a quem sonda.
        var session = login();

        mockMvc.perform(get(SCAN).param("code", "brassia://custo/1").session(session))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("unknown_scan_code"));

        mockMvc.perform(get(SCAN).param("code", "https://malicioso.example.com/lote/1").session(session))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("unknown_scan_code"));
    }

    @Test
    @DisplayName("identificador com caminho ou script é recusado — a etiqueta é entrada de terceiro")
    void identificadorPerigosoERecusado() throws Exception {
        // Qualquer um imprime um QR e cola no tanque.
        var session = login();

        for (var perigoso : new String[] {
            "brassia://lote/../../admin", "brassia://lote/1?admin=true", "brassia://lote/<script>"
        }) {
            mockMvc.perform(get(SCAN).param("code", perigoso).session(session))
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    @Test
    @DisplayName("a leitura não altera nada: é GET, e o QR pode ser um link que a câmera abre sozinha")
    void leituraNaoAltera() throws Exception {
        // Sem POST e sem corpo — é o que permite ao aplicativo de câmera do telefone abrir o link sem
        // instalar nada e sem biblioteca de leitura do nosso lado.
        var session = login();

        mockMvc.perform(post(SCAN).with(csrf()).session(session)
                        .param("code", "brassia://equipamento/TANQUE-01"))
                .andExpect(status().isMethodNotAllowed());
    }

    // --- infraestrutura ---

    private UUID breweryOf(MockHttpSession session) throws Exception {
        var body = mockMvc.perform(get("/api/v1/security/session").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(body).get("activeBrewery").get("id").asText());
    }

    private MockHttpSession login() throws Exception {
        var result = mockMvc.perform(post("/api/v1/security/login").with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"admin@brassia.local\",\"password\":\"admin-local-123\"}"))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Authentication principal(UUID breweryId, Set<String> permissions) {
        var p = new SecurityPrincipal(UUID.randomUUID(), breweryId, "Tester", permissions);
        return new UsernamePasswordAuthenticationToken(p, "n/a", Set.of());
    }
}
