package br.com.brew.brassia.reporting;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import br.com.brew.brassia.shared.security.SecurityPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Relatório do lote de ponta a ponta (RPT-001).
 *
 * <p>O relatório atravessa seis módulos, e é o teste que prova que as seis consultas publicadas
 * estão montadas. O que ele fixa além disso são as duas regras que fazem um documento ser levável a
 * auditoria: nada afirmado em silêncio, e a exportação deixando rastro.
 */
@SpringBootTest
@Testcontainers
class BatchReportIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String REPORTING = "/api/v1/reporting";
    private static final String PLANS = "/api/v1/packaging/plans";

    @Autowired WebApplicationContext context;
    @Autowired JdbcClient jdbc;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("o dossiê junta plano, execução, qualidade, custo e genealogia num documento só")
    void juntaAsCincoSecoes() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        consumeAsReserved(session, batchId);
        transfer(session, batchId);
        executePlan(session, batchId);

        var report = report(session, batchId);

        Assertions.assertThat(report.get("batchCode").asText()).isNotBlank();
        Assertions.assertThat(report.get("plan").get("materials")).isNotEmpty();
        Assertions.assertThat(report.get("execution").get("transferred").asBoolean()).isTrue();
        Assertions.assertThat(report.get("execution").get("packaged").asBoolean()).isTrue();
        Assertions.assertThat(report.get("quality")).isNotNull();
        Assertions.assertThat(report.get("cost").get("total")).isNotNull();
        // A genealogia chega pelo mesmo grafo federado da tela de rastreabilidade.
        Assertions.assertThat(report.get("lineage").get("origins")).isNotEmpty();
        Assertions.assertThat(report.get("generatedAt").asText()).isNotBlank();
    }

    @Test
    @DisplayName("lote sem medição de qualidade diz que não foi medido, e não que passou")
    void semMedicaoDeclaraQueNaoMediu() throws Exception {
        var session = login();
        var batchId = startedBatch(session);

        mockMvc.perform(get(REPORTING + "/batches/" + batchId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quality.unmeasured", is(true)))
                .andExpect(jsonPath("$.incomplete", is(true)))
                .andExpect(jsonPath("$.gaps", hasItem(containsString("nenhuma medição"))));
    }

    @Test
    @DisplayName("lote recém-iniciado declara as ausências em vez de mostrar seções vazias")
    void loteNovoDeclaraAsAusencias() throws Exception {
        var session = login();
        var batchId = startedBatch(session);

        mockMvc.perform(get(REPORTING + "/batches/" + batchId).session(session))
                .andExpect(jsonPath("$.execution.transferred", is(false)))
                .andExpect(jsonPath("$.execution.packaged", is(false)))
                .andExpect(jsonPath("$.gaps", hasItem(containsString("ainda não foi transferido"))))
                .andExpect(jsonPath("$.gaps", hasItem(containsString("ainda não foi envasado"))));
    }

    @Test
    @DisplayName("exportar devolve o documento como anexo e deixa rastro na auditoria")
    void exportarDeixaRastro() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        var antes = exportsOf(batchId);

        mockMvc.perform(post(REPORTING + "/batches/" + batchId + "/export").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString(".json")))
                .andExpect(jsonPath("$.batchId", is(batchId)));

        // O rastro é o ponto da história: o documento saiu do sistema, e há registro de quem tirou.
        Assertions.assertThat(exportsOf(batchId)).isEqualTo(antes + 1);
    }

    @Test
    @DisplayName("exportar é alçada própria: ler na tela não dá direito de levar embora")
    void exportarExigeAlcadaPropria() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        var soLeitor = principal(breweryOf(session), Set.of("reporting.batch.read"));

        mockMvc.perform(get(REPORTING + "/batches/" + batchId).with(authentication(soLeitor)))
                .andExpect(status().isOk());
        mockMvc.perform(post(REPORTING + "/batches/" + batchId + "/export")
                        .with(authentication(soLeitor)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("exportação recusada não deixa rastro de exportação")
    void exportacaoRecusadaNaoDeixaRastro() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        var antes = exportsOf(batchId);

        mockMvc.perform(post(REPORTING + "/batches/" + batchId + "/export")
                        .with(authentication(principal(breweryOf(session), Set.of()))).with(csrf()))
                .andExpect(status().isForbidden());

        Assertions.assertThat(exportsOf(batchId)).isEqualTo(antes);
    }

    @Test
    @DisplayName("ler o relatório também tem alçada")
    void lerExigeAlcada() throws Exception {
        var session = login();
        var batchId = startedBatch(session);

        mockMvc.perform(get(REPORTING + "/batches/" + batchId)
                        .with(authentication(principal(UUID.randomUUID(), Set.of()))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("lote de outra cervejaria não existe para quem pergunta")
    void isolaPorCervejaria() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        var other = principal(UUID.randomUUID(), Set.of("reporting.batch.read"));

        mockMvc.perform(get(REPORTING + "/batches/" + batchId).with(authentication(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("unknown_batch")));
    }

    @Test
    @DisplayName("lote inexistente é recusado em vez de devolver dossiê vazio")
    void loteInexistenteEhRecusado() throws Exception {
        var session = login();

        mockMvc.perform(get(REPORTING + "/batches/" + UUID.randomUUID()).session(session))
                .andExpect(status().isNotFound());
    }

    // --- auditoria ---

    private int exportsOf(String batchId) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM audit_event
                WHERE action = 'reporting.batch.export' AND target_id = :batch
                """)
                .param("batch", batchId)
                .query(Integer.class).single();
    }

    private UUID breweryOf(MockHttpSession session) throws Exception {
        var body = mockMvc.perform(get("/api/v1/security/session").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(JSON.readTree(body).get("activeBrewery").get("id").asText());
    }

    // --- cenário ---

    private String startedBatch(MockHttpSession session) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var maltId = createIngredient(session, "MALT", "KG", "{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}");
        var hopId = createIngredient(session, "HOP", "G", "{\"alphaAcid\":\"12\"}");
        var yeastId = createIngredient(session, "YEAST", "UNIT", "{\"attenuation\":\"78\"}");
        receiveLot(session, maltId, 500, "KG");
        receiveLot(session, hopId, 5000, "G");
        receiveLot(session, yeastId, 50, "UNIT");

        var content = """
                {"name":"Relatório %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,
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
        return batchOfOrder(session, orderId);
    }

    private void consumeAsReserved(MockHttpSession session, String batchId) throws Exception {
        var body = JSON.readTree(mockMvc.perform(
                        get("/api/v1/production/batches/" + batchId + "/consumption/proposal")
                                .session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var lines = new StringBuilder("[");
        for (JsonNode lot : body.get("reserved")) {
            if (lines.length() > 1) {
                lines.append(',');
            }
            lines.append("{\"lotId\":\"").append(lot.get("lotId").asText())
                    .append("\",\"quantity\":").append(lot.get("reserved").asText())
                    .append(",\"unit\":\"").append(lot.get("unit").asText()).append("\"}");
        }
        mockMvc.perform(post("/api/v1/production/batches/" + batchId + "/consumption").session(session)
                        .with(csrf()).contentType("application/json")
                        .content("{\"lines\":" + lines.append(']') + "}"))
                .andExpect(status().isOk());
    }

    private void transfer(MockHttpSession session, String batchId) throws Exception {
        mockMvc.perform(post("/api/v1/production/batches/" + batchId + "/transfer").session(session)
                        .with(csrf()).contentType("application/json")
                        .content("{\"destinationEquipmentId\":\"" + equipment(session)
                                + "\",\"volumeLiters\":390,\"ogSg\":1.052,\"lossesLiters\":8}"))
                .andExpect(status().isCreated());
    }

    private void executePlan(MockHttpSession session, String batchId) throws Exception {
        var containerId = createIngredient(session, "PACKAGING", "UNIT",
                "{\"volumeMl\":\"355\",\"material\":\"lata\"}");
        receiveLot(session, containerId, 2000, "UNIT");
        var lineId = equipment(session);
        releaseCleaning(session, lineId);
        var start = Instant.now().plus(1, ChronoUnit.HOURS);
        var planId = idOf(mockMvc.perform(post(PLANS).session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"code":"ENV-%s","batchId":"%s","containerId":"%s","plannedUnits":400,
                                 "lineEquipmentId":"%s","plannedStart":"%s","plannedEnd":"%s"}
                                """.formatted(UUID.randomUUID().toString().substring(0, 8), batchId,
                                containerId, lineId, start, start.plus(4, ChronoUnit.HOURS))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        for (var item : new String[] {"CONTAINER_INSPECTED", "SEAL_TEST", "GAS_SUPPLY"}) {
            mockMvc.perform(post(PLANS + "/" + planId + "/checklist").session(session).with(csrf())
                            .contentType("application/json").content("{\"item\":\"" + item + "\"}"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post(PLANS + "/" + planId + "/reserve").session(session).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post(PLANS + "/" + planId + "/execution").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"inputVolumeLiters\":145,\"producedUnits\":390,\"rejectedUnits\":5}"))
                .andExpect(status().isOk());
    }

    // --- helpers ---

    private JsonNode report(MockHttpSession session, String batchId) throws Exception {
        return JSON.readTree(mockMvc.perform(get(REPORTING + "/batches/" + batchId).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String batchOfOrder(MockHttpSession session, String orderId) throws Exception {
        var body = mockMvc.perform(get("/api/v1/production/batches").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode node : JSON.readTree(body).get("content")) {
            if (node.get("orderId").asText().equals(orderId)) {
                return node.get("id").asText();
            }
        }
        throw new AssertionError("lote não encontrado para a ordem " + orderId);
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

    private void releaseCleaning(MockHttpSession session, String equipmentId) throws Exception {
        var code = "CIP-" + UUID.randomUUID().toString().substring(0, 8);
        var step = "{\"sequence\":1,\"method\":\"CIP\",\"product\":\"soda\",\"concentrationMinPct\":1.0,"
                + "\"concentrationMaxPct\":3.0,\"tempMinC\":50,\"tempMaxC\":70,\"timeMinutes\":15,"
                + "\"evidenceRequired\":false}";
        var procedureId = idOf(mockMvc.perform(post("/api/v1/sanitation/procedures").session(session)
                        .with(csrf()).contentType("application/json")
                        .content("{\"code\":\"" + code + "\",\"name\":\"CIP linha\",\"steps\":[" + step + "]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/sanitation/procedures/" + procedureId + "/publish").session(session)
                .with(csrf())).andExpect(status().isOk());
        var cycleId = idOf(mockMvc.perform(post("/api/v1/sanitation/cycles").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"procedureCode\":\"" + code + "\",\"equipmentId\":\"" + equipmentId + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post("/api/v1/sanitation/cycles/" + cycleId + "/steps").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"sequence\":1,\"measuredConcentrationPct\":2.0,\"measuredTempC\":60,"
                                + "\"measuredTimeMinutes\":20}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/sanitation/cycles/" + cycleId + "/complete").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/sanitation/cycles/" + cycleId + "/verification").session(session)
                        .with(csrf()).contentType("application/json")
                        .content("{\"rinseOk\":true,\"visualOk\":true,\"atpRlu\":40,\"atpThreshold\":100,"
                                + "\"microOk\":true}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/sanitation/cycles/" + cycleId + "/release").session(session).with(csrf()))
                .andExpect(status().isNoContent());
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
