package br.com.brew.brassia.packaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import br.com.brew.brassia.shared.security.SecurityPrincipal;
import br.com.brew.brassia.support.BrewScenario;
import br.com.brew.brassia.support.CommercialTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Liberação do lote acabado e o que o torna vendável (SAL-001-B).
 *
 * <p>Vivia dentro do {@code PackagingRunIT} porque precisava do cenário de envase; com a fixture
 * compartilhada, passou a caber sozinho (DEB-SAL-003).
 */
@SpringBootTest
@Testcontainers
class LotReleaseIT extends CommercialTestSupport {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    WebApplicationContext context;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
        scenario = new BrewScenario(mockMvc);
    }

    /**
     * Vendável é liberado pela qualidade, dentro da validade e sem quarentena — decisão do mantenedor
     * em 2026-08-15. Este teste percorre os três estados que o lote atravessa.
     */
    @Test
    void oLoteSoFicaVendavelDepoisDeLiberadoEComValidadeApurada() throws Exception {
        var session = login();
        var planId = reservedPlan(session, 1000);
        var body = execute(session, planId, "284", 780, 12).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var code = JSON.readTree(body).get("finishedLotCode").asText();
        var batchId = batchOfPlan(session, planId);
        var lotId = finishedLotOf(session, batchId, code).get("id").asText();

        // 1. Recém-envasado: falta assinatura. O impedimento é nomeado, e não um "não disponível".
        mockMvc.perform(get(LOTS + "/" + lotId + "/sale-status").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellable", is(false)))
                .andExpect(jsonPath("$.blocker.code", is("not_released")));

        // 2. Liberado, mas sem evidência de oxigênio: validade desconhecida não é validade em dia.
        mockMvc.perform(post(LOTS + "/" + lotId + "/release").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(LOTS + "/" + lotId + "/sale-status").session(session))
                .andExpect(jsonPath("$.sellable", is(false)))
                .andExpect(jsonPath("$.blocker.code", is("shelf_life_unknown")));

        // 3. Com validade registrada, o lote passa a ser vendável.
        registraFrescor(session, planId);
        mockMvc.perform(get(LOTS + "/" + lotId + "/sale-status").session(session))
                .andExpect(jsonPath("$.sellable", is(true)))
                .andExpect(jsonPath("$.blocker").doesNotExist())
                .andExpect(jsonPath("$.bestBefore", is(notNullValue())));
    }

    @Test
    void naoSeLiberaDuasVezes() throws Exception {
        // Sobrescrever trocaria o responsável e a data, e a auditoria deixaria de saber quem respondeu
        // pelo lote. A resposta diz quem liberou e quando.
        var session = login();
        var planId = reservedPlan(session, 1000);
        var lotId = loteAcabado(session, planId);

        mockMvc.perform(post(LOTS + "/" + lotId + "/release").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post(LOTS + "/" + lotId + "/release").session(session).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("lot_already_released")))
                .andExpect(jsonPath("$.releasedBy", is(notNullValue())))
                .andExpect(jsonPath("$.releasedAt", is(notNullValue())));
    }

    @Test
    void liberarExigeAlcadaPropriaDaQualidade() throws Exception {
        // Planejar e executar envase não dá o direito de afirmar que a cerveja pode ir ao cliente.
        var session = login();
        var planId = reservedPlan(session, 1000);
        var lotId = loteAcabado(session, planId);

        mockMvc.perform(post(LOTS + "/" + lotId + "/release")
                        .with(authentication(principal(UUID.randomUUID(),
                                Set.of("packaging.plan.read", "packaging.plan.manage"))))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void oLoteVendavelApareceNoProdutoQueOVende() throws Exception {
        // O produto é o par (receita, embalagem), e é assim que ele encontra os lotes: o lote acabado
        // sabe de que lote de produção veio, e o lote de produção sabe a receita.
        var session = login();
        var planId = reservedPlan(session, 1000);
        var lotId = loteAcabado(session, planId);
        var batchId = batchOfPlan(session, planId);
        var recipeId = receitaDoLote(session, batchId);
        var containerId = embalagemDoLote(session, batchId);
        var produtoId = criaProduto(session, recipeId, containerId);

        // Antes da liberação, o produto não tem nada a prometer.
        mockMvc.perform(get("/api/v1/sales/products/" + produtoId + "/sellable-lots").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));

        mockMvc.perform(post(LOTS + "/" + lotId + "/release").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        registraFrescor(session, planId);

        mockMvc.perform(get("/api/v1/sales/products/" + produtoId + "/sellable-lots").session(session))
                .andExpect(jsonPath("$[?(@.finishedLotId=='" + lotId + "')].units",
                        is(java.util.List.of(780))));
    }

    private static final String LOTS = "/api/v1/packaging/finished-lots";

    private String receitaDoLote(MockHttpSession session, String batchId) throws Exception {
        var body = mockMvc.perform(get("/api/v1/production/batches/" + batchId).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("recipeId").asText();
    }

    private String embalagemDoLote(MockHttpSession session, String batchId) throws Exception {
        var body = mockMvc.perform(get(LOTS).param("batchId", batchId).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get(0).get("containerId").asText();
    }

    private String criaProduto(MockHttpSession session, String recipeId, String containerId)
            throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var body = mockMvc.perform(post("/api/v1/sales/products").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"sku\":\"SKU-" + sfx + "\",\"name\":\"Produto de teste\","
                                + "\"recipeId\":\"" + recipeId + "\","
                                + "\"containerId\":\"" + containerId + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }
}
