package br.com.brew.brassia.reporting;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import br.com.brew.brassia.shared.security.SecurityPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.assertj.core.api.Assertions;
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
 * Painel operacional de ponta a ponta (RPT-002).
 *
 * <p>O que estes testes fixam é o critério da história sobre dados reais: todo indicador que chega
 * na resposta tem definição, período e destino de drill-down — e os cinco blocos estão de fato
 * ligados, cada um pela porta federada do seu módulo.
 */
@SpringBootTest
@Testcontainers
class DashboardIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DASHBOARD = "/api/v1/reporting/dashboard";

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("todo indicador chega com definição, período e drill-down")
    void todoIndicadorTemOsTres() throws Exception {
        var session = login();

        mockMvc.perform(get(DASHBOARD).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indicators[*].definition", everyItem(not(is("")))))
                .andExpect(jsonPath("$.indicators[*].to", everyItem(not(is(nullValue())))))
                .andExpect(jsonPath("$.indicators[*].drillDown.resource", everyItem(not(is("")))))
                .andExpect(jsonPath("$.indicators[*].code", everyItem(not(is("")))));
    }

    @Test
    @DisplayName("os cinco blocos respondem: produção, estoque, qualidade, fermentação e custo")
    void osCincoBlocosRespondem() throws Exception {
        var session = login();

        mockMvc.perform(get(DASHBOARD).session(session))
                .andExpect(jsonPath("$.sources", greaterThan(4)))
                .andExpect(jsonPath("$.indicators[*].group", hasItem("PRODUCTION")))
                .andExpect(jsonPath("$.indicators[*].group", hasItem("STOCK")))
                .andExpect(jsonPath("$.indicators[*].group", hasItem("QUALITY")))
                .andExpect(jsonPath("$.indicators[*].group", hasItem("FERMENTATION")))
                .andExpect(jsonPath("$.indicators[*].group", hasItem("COST")));
    }

    @Test
    @DisplayName("o lote iniciado aparece na produção do período")
    void oLoteIniciadoAparece() throws Exception {
        var session = login();
        var from = Instant.now();
        startedBatch(session);

        var indicator = indicatorOf(dashboard(session, from, from.plusSeconds(120)),
                "producao.lotes_iniciados");

        Assertions.assertThat(indicator.get("value").decimalValue()).isEqualByComparingTo("1");
        Assertions.assertThat(indicator.get("positional").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("indicador de posição ignora o intervalo e responde pela foto do instante")
    void posicaoIgnoraOIntervalo() throws Exception {
        var session = login();
        startedBatch(session);

        // Janela de 2020: nada foi iniciado nela, mas o lote continua em andamento agora.
        var antigo = dashboard(session, Instant.parse("2020-01-01T00:00:00Z"),
                Instant.parse("2020-02-01T00:00:00Z"));

        Assertions.assertThat(indicatorOf(antigo, "producao.lotes_iniciados").get("value")
                .decimalValue()).isEqualByComparingTo("0");
        var posicao = indicatorOf(antigo, "producao.lotes_em_andamento");
        Assertions.assertThat(posicao.get("positional").asBoolean()).isTrue();
        Assertions.assertThat(posicao.get("from").isNull()).isTrue();
    }

    @Test
    @DisplayName("percentual sobre zero medição vem com a ressalva de que não fala de nada")
    void conformidadeSemMedicaoTemRessalva() throws Exception {
        var session = login();

        var vazio = dashboard(session, Instant.parse("2020-01-01T00:00:00Z"),
                Instant.parse("2020-02-01T00:00:00Z"));

        var conformidade = indicatorOf(vazio, "qualidade.conformidade");
        Assertions.assertThat(conformidade.get("gap").asText()).contains("não fala de nada");
    }

    @Test
    @DisplayName("sem custo fechado no período, a média por litro declara que não tem base")
    void mediaSemCustoFechadoDeclara() throws Exception {
        var session = login();

        var vazio = dashboard(session, Instant.parse("2020-01-01T00:00:00Z"),
                Instant.parse("2020-02-01T00:00:00Z"));

        Assertions.assertThat(indicatorOf(vazio, "custo.medio_por_litro").get("gap").asText())
                .contains("nenhum custo foi fechado");
    }

    @Test
    @DisplayName("período invertido é erro de quem perguntou")
    void recusaPeriodoInvertido() throws Exception {
        var session = login();

        mockMvc.perform(get(DASHBOARD).session(session)
                        .param("from", "2026-08-01T00:00:00Z").param("to", "2026-07-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("o painel tem alçada própria")
    void exigeAlcada() throws Exception {
        mockMvc.perform(get(DASHBOARD).with(authentication(principal(UUID.randomUUID(), Set.of()))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("o painel de outra cervejaria não mostra a produção desta")
    void isolaPorCervejaria() throws Exception {
        var session = login();
        startedBatch(session);
        var other = principal(UUID.randomUUID(), Set.of("reporting.dashboard.read"));

        var body = JSON.readTree(mockMvc.perform(get(DASHBOARD).with(authentication(other)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        // Os indicadores existem — a estrutura do painel é a mesma —, e todos vêm zerados.
        Assertions.assertThat(indicatorOf(body, "producao.lotes_iniciados").get("value")
                .decimalValue()).isEqualByComparingTo("0");
        Assertions.assertThat(indicatorOf(body, "producao.lotes_em_andamento").get("value")
                .decimalValue()).isEqualByComparingTo("0");
    }

    // --- helpers ---

    private static org.hamcrest.Matcher<Object> nullValue() {
        return org.hamcrest.Matchers.nullValue();
    }

    private JsonNode dashboard(MockHttpSession session, Instant from, Instant to) throws Exception {
        return JSON.readTree(mockMvc.perform(get(DASHBOARD).session(session)
                        .param("from", from.toString()).param("to", to.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static JsonNode indicatorOf(JsonNode dashboard, String code) {
        for (JsonNode indicator : dashboard.get("indicators")) {
            if (indicator.get("code").asText().equals(code)) {
                return indicator;
            }
        }
        throw new AssertionError("sem indicador de código " + code);
    }

    private void startedBatch(MockHttpSession session) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var maltId = createIngredient(session, "MALT", "KG", "{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}");
        var hopId = createIngredient(session, "HOP", "G", "{\"alphaAcid\":\"12\"}");
        var yeastId = createIngredient(session, "YEAST", "UNIT", "{\"attenuation\":\"78\"}");
        receiveLot(session, maltId, 500, "KG");
        receiveLot(session, hopId, 5000, "G");
        receiveLot(session, yeastId, 50, "UNIT");

        var content = """
                {"name":"Painel %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,
                 "targetIbu":30,
                 "items":[{"ingredientId":"%s","stage":"MASH","quantity":20,"unit":"KG"},
                          {"ingredientId":"%s","stage":"BOIL","quantity":60,"unit":"G","timingMinutes":60},
                          {"ingredientId":"%s","stage":"FERMENTATION","quantity":1,"unit":"UNIT"}]}
                """.formatted(sfx, equipment(session), maltId, hopId, yeastId);
        var recipeId = idOf(mockMvc.perform(post("/api/v1/recipes").session(session).with(csrf())
                        .contentType("application/json").content(content))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/recipes/" + recipeId + "/metrics").session(session).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/recipes/" + recipeId + "/publish").session(session).with(csrf()))
                .andExpect(status().isOk());

        var orderId = idOf(mockMvc.perform(post("/api/v1/brew-orders").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"recipeId\":\"" + recipeId + "\",\"volumeLiters\":400}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/release").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"assignedUserId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/reserve-stock").session(session).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/start").session(session).with(csrf()))
                .andExpect(status().isOk());
    }

    private String receiveLot(MockHttpSession session, String ingredientId, int quantity, String unit)
            throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var supplierId = idOf(mockMvc.perform(post("/api/v1/suppliers").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Sup " + sfx + "\",\"code\":\"S-" + sfx + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        return idOf(mockMvc.perform(post("/api/v1/inventory/lots").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"ingredientId\":\"" + ingredientId + "\",\"supplierId\":\"" + supplierId
                                + "\",\"quantity\":" + quantity + ",\"unit\":\"" + unit + "\",\"unitCost\":1.5,"
                                + "\"supplierLotCode\":\"F-" + sfx + "\","
                                + "\"expiryDate\":\"2028-01-01\",\"inspection\":\"APPROVED\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String createIngredient(MockHttpSession session, String type, String unit, String attributes)
            throws Exception {
        var code = type.toLowerCase(Locale.ROOT).charAt(0) + "-" + UUID.randomUUID().toString().substring(0, 8);
        return idOf(mockMvc.perform(post("/api/v1/catalog/ingredients").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"type\":\"" + type + "\",\"code\":\"" + code + "\",\"name\":\"" + code
                                + "\",\"useUnit\":\"" + unit + "\",\"purchaseUnit\":\"" + unit
                                + "\",\"attributes\":" + attributes + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String equipment(MockHttpSession session) throws Exception {
        var code = "EQ-" + UUID.randomUUID().toString().substring(0, 8);
        return idOf(mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"" + code + "\",\"name\":\"Panela\",\"capacityLiters\":500,"
                                + "\"deadSpaceLiters\":20,\"mashEfficiencyPercent\":72,"
                                + "\"boilOffLitersPerHour\":8}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private static String idOf(String json) throws Exception {
        return JSON.readTree(json).get("id").asText();
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
