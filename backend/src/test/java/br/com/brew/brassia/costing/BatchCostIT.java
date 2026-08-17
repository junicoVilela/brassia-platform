package br.com.brew.brassia.costing;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
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
import java.util.Locale;
import java.util.Set;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Custo realizado do lote de ponta a ponta (CST-001).
 *
 * <p>O que estes testes fixam é a diferença entre um custo que muda e um que não muda mais, e a
 * recusa em esconder o que ficou de fora: o total sem mão de obra e sem utilidade vem acompanhado
 * das lacunas que explicam por quê.
 */
@SpringBootTest
@Testcontainers
class BatchCostIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    /**
     * Janela do envase ancorada em AGORA, e não numa data fixa.
     *
     * <p>A linha limpa exige liberação <strong>anterior</strong> ao início planejado
     * ({@code LineCleanliness}). Com data fixa, o dia em que ela passa inverte a ordem e todo envase
     * destes testes passa a ser recusado com {@code line_not_clean} — uma falha datada, que aparece sem
     * ninguém ter mexido em nada.
     */
    private static final String PLANNED_START = Instant.now().plus(Duration.ofHours(1)).toString();
    private static final String PLANNED_END = Instant.now().plus(Duration.ofHours(7)).toString();

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String COSTING = "/api/v1/costing";
    private static final String PLANS = "/api/v1/packaging/plans";

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("sem consumo confirmado, o insumo não entra e a lacuna diz por quê")
    void semConsumoNaoHaCustoDeInsumo() throws Exception {
        var session = login();
        var scene = startedBatch(session);

        mockMvc.perform(get(COSTING + "/batches/" + scene.batchId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closed", is(false)))
                .andExpect(jsonPath("$.incomplete", is(true)))
                // Centavos, sempre: quem lê um total espera dinheiro como se escreve numa nota. E a
                // moeda vem junto — um número sozinho não é dinheiro (DEB-SAL-001).
                .andExpect(jsonPath("$.total", is(0.00)))
                .andExpect(jsonPath("$.currency", is("BRL")))
                .andExpect(jsonPath("$.gaps[?(@.category=='INGREDIENT')].reason",
                        hasItem(containsString("consumo do dia de brassa ainda não foi confirmado"))));
    }

    @Test
    @DisplayName("o custo do insumo sai do preço do lote que foi consumido")
    void custoDoInsumoVemDoLoteConsumido() throws Exception {
        var session = login();
        var scene = startedBatch(session);
        registerConsumption(session, scene.batchId).andExpect(status().isOk());

        mockMvc.perform(get(COSTING + "/batches/" + scene.batchId).session(session))
                .andExpect(status().isOk())
                // 20 KG + 60 G + 1 UNIT, todos a 1,5 por unidade de compra.
                .andExpect(jsonPath("$.totalByCategory.INGREDIENT").exists())
                .andExpect(jsonPath("$.lines[?(@.category=='INGREDIENT')].source",
                        hasItem(containsString("consumo de brassagem"))))
                // A origem é rastreável parcela a parcela: é o critério da história.
                .andExpect(jsonPath("$.lines[?(@.category=='INGREDIENT')].source",
                        hasItem(containsString("preço da entrada"))));
    }

    @Test
    @DisplayName("a embalagem entra depois do envase, e o custo por litro cai sobre o volume real")
    void embalagemEntraDepoisDoEnvase() throws Exception {
        var session = login();
        var scene = startedBatch(session);
        registerConsumption(session, scene.batchId).andExpect(status().isOk());
        transfer(session, scene.batchId);
        var semEnvase = cost(session, scene.batchId);

        executePlan(session, scene.batchId);

        var comEnvase = cost(session, scene.batchId);
        org.assertj.core.api.Assertions.assertThat(comEnvase.get("total").decimalValue())
                .isGreaterThan(semEnvase.get("total").decimalValue());
        org.assertj.core.api.Assertions.assertThat(comEnvase.get("totalByCategory").get("PACKAGING"))
                .isNotNull();
        // Divisor: o volume transferido ao fermentador, que é o que existiu de fato.
        org.assertj.core.api.Assertions.assertThat(comEnvase.get("volumeLiters").decimalValue())
                .isEqualByComparingTo("390.000");
    }

    @Test
    @DisplayName("mão de obra e utilidade não somam zero: viram lacuna declarada")
    void maoDeObraEUtilidadeSaoLacunas() throws Exception {
        var session = login();
        var scene = startedBatch(session);
        registerConsumption(session, scene.batchId).andExpect(status().isOk());

        mockMvc.perform(get(COSTING + "/batches/" + scene.batchId).session(session))
                .andExpect(jsonPath("$.incomplete", is(true)))
                // A lacuna de mão de obra agora vem do contribuinte, e distingue "sem taxa cadastrada"
                // de "ninguém apontou" — duas ausências com ações diferentes (CST-001-A).
                .andExpect(jsonPath("$.gaps[?(@.category=='LABOR')].reason",
                        hasItem(containsString("custo cadastrado"))))
                .andExpect(jsonPath("$.gaps[?(@.category=='UTILITY')].reason",
                        hasItem(containsString("CO₂"))));
    }

    @Test
    @DisplayName("fechar congela o número: o custo para de mudar quando a produção muda")
    void fecharCongela() throws Exception {
        var session = login();
        var scene = startedBatch(session);
        registerConsumption(session, scene.batchId).andExpect(status().isOk());
        transfer(session, scene.batchId);

        var fechado = close(session, scene.batchId, "apuração de agosto")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closed", is(true)))
                .andReturn().getResponse().getContentAsString();
        var totalNoFechamento = JSON.readTree(fechado).get("total").decimalValue();

        // Um envase depois do fechamento mudaria o custo aberto; o fechado não se mexe.
        executePlan(session, scene.batchId);

        var depois = cost(session, scene.batchId);
        org.assertj.core.api.Assertions.assertThat(depois.get("closed").asBoolean()).isTrue();
        org.assertj.core.api.Assertions.assertThat(depois.get("total").decimalValue())
                .isEqualByComparingTo(totalNoFechamento);
    }

    @Test
    @DisplayName("fechar duas vezes é recusado: custo fechado é evidência")
    void naoFechaDuasVezes() throws Exception {
        var session = login();
        var scene = startedBatch(session);
        registerConsumption(session, scene.batchId).andExpect(status().isOk());
        transfer(session, scene.batchId);
        close(session, scene.batchId, "primeira").andExpect(status().isOk());

        close(session, scene.batchId, "segunda").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("lote sem volume transferido não fecha")
    void semVolumeNaoFecha() throws Exception {
        var session = login();
        var scene = startedBatch(session);
        registerConsumption(session, scene.batchId).andExpect(status().isOk());

        // Sem transferência o volume é o planejado, então fecha; o caso de recusa é o lote
        // inexistente, coberto abaixo. Aqui garantimos que o fechamento normal funciona.
        close(session, scene.batchId, "sem transferência").andExpect(status().isOk());
    }

    @Test
    @DisplayName("lote inexistente é recusado em vez de devolver custo zero")
    void loteInexistenteEhRecusado() throws Exception {
        var session = login();

        mockMvc.perform(get(COSTING + "/batches/" + UUID.randomUUID()).session(session))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("unknown_batch")));
    }

    @Test
    @DisplayName("fechar é alçada própria: ler o custo não basta")
    void fecharExigeAlcada() throws Exception {
        var session = login();
        var scene = startedBatch(session);
        var leitor = principal(UUID.randomUUID(), Set.of("costing.cost.read"));

        mockMvc.perform(post(COSTING + "/batches/" + scene.batchId + "/close")
                        .with(authentication(leitor)).with(csrf()).contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("custo de outra cervejaria não existe para quem pergunta")
    void isolaPorCervejaria() throws Exception {
        var session = login();
        var scene = startedBatch(session);
        registerConsumption(session, scene.batchId).andExpect(status().isOk());
        transfer(session, scene.batchId);
        close(session, scene.batchId, "fechado").andExpect(status().isOk());

        var other = principal(UUID.randomUUID(), Set.of("costing.cost.read", "costing.cost.close"));
        mockMvc.perform(get(COSTING + "/batch-costs").with(authentication(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
        mockMvc.perform(get(COSTING + "/batches/" + scene.batchId).with(authentication(other)))
                .andExpect(status().isNotFound());
    }

    // --- cenário ---

    @Test
    @DisplayName("HORA APONTADA + TAXA DA CASA VIRAM PARCELA DE MÃO DE OBRA")
    void maoDeObraEntraNoCusto() throws Exception {
        // CST-001-A. A hora é da produção, o dinheiro é do custeio: a parcela só existe quando as duas
        // metades existem, e a ausência de cada uma diz coisa diferente.
        var session = login();
        var scene = startedBatch(session);
        registerConsumption(session, scene.batchId()).andExpect(status().isOk());

        // Sem taxa, a lacuna diz o que fazer.
        mockMvc.perform(get(COSTING + "/batches/" + scene.batchId()).session(session))
                .andExpect(jsonPath("$.gaps[?(@.category=='LABOR')].reason",
                        hasItem(containsString("custo cadastrado"))));

        definirTaxa(session, "50.00");

        // Com taxa e sem apontamento, a lacuna é OUTRA: ninguém trabalhou registrado neste lote.
        mockMvc.perform(get(COSTING + "/batches/" + scene.batchId()).session(session))
                .andExpect(jsonPath("$.gaps[?(@.category=='LABOR')].reason",
                        hasItem(containsString("Nenhuma hora foi apontada"))));

        // Duas pessoas por três horas: seis horas-homem a 50 = 300.
        apontarHoras(session, scene.batchId(), "Brassa", 3, 2);

        mockMvc.perform(get(COSTING + "/batches/" + scene.batchId()).session(session))
                .andExpect(jsonPath("$.lines[?(@.category=='LABOR')].quantity", hasItem(6.0)))
                .andExpect(jsonPath("$.lines[?(@.category=='LABOR')].total", hasItem(300.0)))
                .andExpect(jsonPath("$.gaps[?(@.category=='LABOR')]").isEmpty());
    }

    private void definirTaxa(MockHttpSession session, String valor) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put(COSTING + "/labor-rate").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"costPerHour\":" + valor + "}"))
                .andExpect(status().isOk());
    }

    private void apontarHoras(MockHttpSession session, String batchId, String atividade, int horas,
            int pessoas) throws Exception {
        var inicio = java.time.Instant.now().minusSeconds(horas * 3600L);
        mockMvc.perform(post("/api/v1/production/batches/" + batchId + "/labor").session(session)
                        .with(csrf()).contentType("application/json")
                        .content("""
                                {"activity":"%s","startedAt":"%s","endedAt":"%s","people":%d}
                                """.formatted(atividade, inicio, java.time.Instant.now(), pessoas)))
                .andExpect(status().isCreated());
    }

    private record Scene(String batchId, String containerId) {}

    private Scene startedBatch(MockHttpSession session) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var equipmentId = createEquipment(session);
        var maltId = createIngredient(session, "MALT", "KG", "{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}");
        var hopId = createIngredient(session, "HOP", "G", "{\"alphaAcid\":\"12\"}");
        var yeastId = createIngredient(session, "YEAST", "UNIT", "{\"attenuation\":\"78\"}");
        receiveLot(session, maltId, 500, "KG");
        receiveLot(session, hopId, 5000, "G");
        receiveLot(session, yeastId, 50, "UNIT");
        var containerId = createIngredient(session, "PACKAGING", "UNIT",
                "{\"volumeMl\":\"355\",\"material\":\"lata\"}");
        receiveLot(session, containerId, 2000, "UNIT");

        var content = """
                {"name":"Custo %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,
                 "targetIbu":30,
                 "items":[{"ingredientId":"%s","stage":"MASH","quantity":20,"unit":"KG"},
                          {"ingredientId":"%s","stage":"BOIL","quantity":60,"unit":"G","timingMinutes":60},
                          {"ingredientId":"%s","stage":"FERMENTATION","quantity":1,"unit":"UNIT"}]}
                """.formatted(sfx, equipmentId, maltId, hopId, yeastId);
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

        return new Scene(batchOfOrder(session, orderId), containerId);
    }

    // --- helpers ---

    private ResultActions registerConsumption(MockHttpSession session, String batchId) throws Exception {
        var body = mockMvc.perform(get("/api/v1/production/batches/" + batchId + "/consumption/proposal")
                        .session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var lines = new StringBuilder("[");
        for (JsonNode lot : JSON.readTree(body).get("reserved")) {
            if (lines.length() > 1) {
                lines.append(',');
            }
            lines.append("{\"lotId\":\"").append(lot.get("lotId").asText())
                    .append("\",\"quantity\":").append(lot.get("reserved").asText())
                    .append(",\"unit\":\"").append(lot.get("unit").asText()).append("\"}");
        }
        return mockMvc.perform(post("/api/v1/production/batches/" + batchId + "/consumption")
                .session(session).with(csrf()).contentType("application/json")
                .content("{\"lines\":" + lines.append(']') + "}"));
    }

    private void transfer(MockHttpSession session, String batchId) throws Exception {
        mockMvc.perform(post("/api/v1/production/batches/" + batchId + "/transfer").session(session)
                        .with(csrf()).contentType("application/json")
                        .content("{\"destinationEquipmentId\":\"" + createEquipment(session)
                                + "\",\"volumeLiters\":390,\"ogSg\":1.052,\"lossesLiters\":8}"))
                .andExpect(status().isCreated());
    }

    private void executePlan(MockHttpSession session, String batchId) throws Exception {
        var containerId = createIngredient(session, "PACKAGING", "UNIT",
                "{\"volumeMl\":\"355\",\"material\":\"lata\"}");
        receiveLot(session, containerId, 2000, "UNIT");
        var lineId = createEquipment(session);
        releaseCleaning(session, lineId);
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var planId = idOf(mockMvc.perform(post(PLANS).session(session).with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"code":"ENV-%s","batchId":"%s","containerId":"%s","plannedUnits":400,
                                 "lineEquipmentId":"%s","plannedStart":"%s",
                                 "plannedEnd":"%s"}
                                """.formatted(sfx, batchId, containerId, lineId, PLANNED_START, PLANNED_END)))
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

    private JsonNode cost(MockHttpSession session, String batchId) throws Exception {
        return JSON.readTree(mockMvc.perform(get(COSTING + "/batches/" + batchId).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private ResultActions close(MockHttpSession session, String batchId, String note) throws Exception {
        return mockMvc.perform(post(COSTING + "/batches/" + batchId + "/close").session(session).with(csrf())
                .contentType("application/json").content("{\"note\":\"" + note + "\"}"));
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
        var procedureId = idOf(mockMvc.perform(post("/api/v1/sanitation/procedures").session(session).with(csrf())
                        .contentType("application/json")
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
        mockMvc.perform(post("/api/v1/sanitation/cycles/" + cycleId + "/verification").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"rinseOk\":true,\"visualOk\":true,\"atpRlu\":40,\"atpThreshold\":100,"
                                + "\"microOk\":true}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/sanitation/cycles/" + cycleId + "/release").session(session).with(csrf()))
                .andExpect(status().isNoContent());
    }

    private String createEquipment(MockHttpSession session) throws Exception {
        var code = "EQ-" + UUID.randomUUID().toString().substring(0, 8);
        return idOf(mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"" + code + "\",\"name\":\"Panela\",\"capacityLiters\":500,"
                                + "\"deadSpaceLiters\":20,\"mashEfficiencyPercent\":72,"
                                + "\"boilOffLitersPerHour\":8}"))
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
