package br.com.brew.brassia.ai;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import br.com.brew.brassia.shared.security.SecurityPrincipal;
import java.util.Set;
import java.util.UUID;
import org.hamcrest.Matchers;
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
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.junit.jupiter.Container;

/**
 * Avaliação de lote pela borda HTTP (AIA-002).
 *
 * <p><strong>O que este teste cobre, e o que não cobre — declarado de propósito.</strong>
 *
 * <p>Cobre o que só um teste de contexto cobre: que o caso de uso é montado com as consultas publicadas
 * <em>reais</em> de cinco módulos (produção, receita, qualidade, custeio e o gateway) — se alguma delas não
 * existisse ou não resolvesse, o contexto não subiria e este teste falharia antes da primeira asserção —, e o
 * contrato HTTP com as suas recusas: lote inexistente, falta de alçada e ausência de cervejaria ativa.
 *
 * <p><strong>Não</strong> cobre a montagem dos fatos a partir de um lote com dados reais. Montar um lote
 * completo pela API — insumos, lotes de estoque, receita publicada, ordem, brassagem e transferência — são
 * cerca de duzentas linhas já escritas em {@code BatchReportIT}, e duplicá-las aqui testaria a produção, não a
 * avaliação. A montagem dos fatos é coberta em {@code BatchAssessmentHandlerTest} com dublês das mesmas
 * consultas publicadas, e o comportamento real de cada consulta é testado no IT do módulo que a publica —
 * onde o cenário daquele módulo já existe.
 */
@SpringBootTest
@Testcontainers
class BatchAssessmentIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final String ASSESS = "/api/v1/ai/copilot/batches/";

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("lote que não existe nesta cervejaria é recusado com Problem Details")
    void loteInexistenteEhRecusado() throws Exception {
        var session = login();

        mockMvc.perform(post(ASSESS + UUID.randomUUID() + "/assessment").with(csrf()).session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", Matchers.is("unknown_batch")))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    @DisplayName("avaliar é alçada própria: perguntar sobre um manual não dá acesso ao custo de um lote")
    void avaliarExigeAlcadaPropria() throws Exception {
        var asker = principal(UUID.randomUUID(), Set.of("ai.answer.ask", "knowledge.document.read"));

        mockMvc.perform(post(ASSESS + UUID.randomUUID() + "/assessment").with(csrf())
                        .with(authentication(asker)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("sem cervejaria ativa não há lote a avaliar")
    void semCervejariaNaoAvalia() throws Exception {
        var tenantless = principal(null, Set.of("ai.assessment.batch"));

        mockMvc.perform(post(ASSESS + UUID.randomUUID() + "/assessment").with(csrf())
                        .with(authentication(tenantless)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("identificador de lote malformado é recusado antes de chegar ao caso de uso")
    void idMalformadoEhRecusado() throws Exception {
        var session = login();

        mockMvc.perform(post(ASSESS + "nao-e-uuid/assessment").with(csrf()).session(session))
                .andExpect(status().isBadRequest());
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
