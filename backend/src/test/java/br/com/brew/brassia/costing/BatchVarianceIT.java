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
import java.math.BigDecimal;
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
 * Planejado versus real de ponta a ponta (CST-002).
 *
 * <p>O que estes testes fixam é a conciliação sobre dados reais: o plano vem da explosão da
 * receita, a base de preço vem dos movimentos de reserva que sobreviveram à liberação, e o real vem
 * do consumo confirmado. Se a soma não fechasse, seria aqui que apareceria.
 */
@SpringBootTest
@Testcontainers
class BatchVarianceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

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
    @DisplayName("consumindo exatamente o reservado, preço e consumo dão zero e a conta fecha")
    void semDesvioAVariacaoEhZero() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        consumeAsReserved(session, batchId);

        var variance = variance(session, batchId);

        Assertions.assertThat(variance.get("reconciles").asBoolean()).isTrue();
        Assertions.assertThat(variance.get("priceVariance").decimalValue())
                .isEqualByComparingTo(BigDecimal.ZERO);
        // A reserva é do mesmo lote e do mesmo preço, e a receita pediu exatamente aquilo.
        Assertions.assertThat(variance.get("consumptionVariance").decimalValue())
                .isEqualByComparingTo(BigDecimal.ZERO);
        Assertions.assertThat(variance.get("totalVariance").decimalValue())
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("consumir mais do que a receita pedia vira variação de consumo, e a conta continua fechando")
    void consumirMaisViraVariacaoDeConsumo() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        // 25 KG de malte onde a receita pedia 20: cinco quilos a mais, ao preço do lote reservado.
        consume(session, batchId, 25);

        var variance = variance(session, batchId);

        Assertions.assertThat(variance.get("consumptionVariance").decimalValue())
                .isGreaterThan(BigDecimal.ZERO);
        Assertions.assertThat(variance.get("reconciles").asBoolean()).isTrue();
        var soma = variance.get("priceVariance").decimalValue()
                .add(variance.get("consumptionVariance").decimalValue());
        Assertions.assertThat(soma).isEqualByComparingTo(variance.get("totalVariance").decimalValue());
        Assertions.assertThat(soma).isEqualByComparingTo(variance.get("actualCost").decimalValue()
                .subtract(variance.get("plannedCost").decimalValue()));
    }

    @Test
    @DisplayName("consumir lote mais caro que o reservado vira variação de preço")
    void consumirLoteMaisCaroViraVariacaoDePreco() throws Exception {
        var session = login();
        var scene = startedScene(session);
        // O brewer usou outro lote do mesmo malte, comprado mais caro que o que a OP separou.
        var caro = receiveLot(session, scene.maltId, 500, "KG", "3.0");
        consumeLots(session, scene.batchId, lot(caro, 20, "KG"));

        var variance = variance(session, scene.batchId);

        Assertions.assertThat(variance.get("priceVariance").decimalValue())
                .isGreaterThan(BigDecimal.ZERO);
        Assertions.assertThat(variance.get("reconciles").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("sem consumo confirmado, o real é vazio e a lacuna diz por quê")
    void semConsumoNaoHaReal() throws Exception {
        var session = login();
        var batchId = startedBatch(session);

        mockMvc.perform(get(COSTING + "/batches/" + batchId + "/variance").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incomplete", is(true)))
                .andExpect(jsonPath("$.gaps[*].reason",
                        hasItem(containsString("ainda não foi confirmado"))))
                .andExpect(jsonPath("$.materials[0].actualQuantity").doesNotExist());
    }

    @Test
    @DisplayName("rendimento compara o planejado com o transferido, e a perda entra sem base")
    void rendimentoEPerda() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        consumeAsReserved(session, batchId);
        transfer(session, batchId);

        mockMvc.perform(get(COSTING + "/batches/" + batchId + "/variance").session(session))
                .andExpect(status().isOk())
                // 390 L transferidos contra 400 planejados: −10 L, −2,50%.
                .andExpect(jsonPath("$.volumes[?(@.kind=='YIELD')].variance", hasItem(-10.0)))
                .andExpect(jsonPath("$.volumes[?(@.kind=='YIELD')].unfavorable", hasItem(true)))
                .andExpect(jsonPath("$.volumes[?(@.kind=='LOSS')].comparable", hasItem(false)))
                .andExpect(jsonPath("$.gaps[*].reason", hasItem(containsString("CST-002-A"))));
    }

    @Test
    @DisplayName("depois do envase, cada plano executado compara o planejado com o envasado")
    void envaseEntraNaComparacao() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        consumeAsReserved(session, batchId);
        transfer(session, batchId);
        executePlan(session, batchId);

        var variance = variance(session, batchId);

        var envase = false;
        for (JsonNode volume : variance.get("volumes")) {
            if (volume.get("what").asText().contains("envasado no plano")) {
                envase = true;
                Assertions.assertThat(volume.get("comparable").asBoolean()).isTrue();
            }
        }
        Assertions.assertThat(envase).isTrue();
        Assertions.assertThat(reasons(variance)).noneMatch(reason -> reason.contains("não foi envasado"));
    }

    @Test
    @DisplayName("a variação continua sendo derivada depois de o custo ser fechado")
    void fecharOCustoNaoCongelaAExplicacao() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        consumeAsReserved(session, batchId);
        transfer(session, batchId);
        mockMvc.perform(post(COSTING + "/batches/" + batchId + "/close").session(session).with(csrf())
                        .contentType("application/json").content("{\"note\":\"apuração\"}"))
                .andExpect(status().isOk());

        var antes = variance(session, batchId);
        executePlan(session, batchId);
        var depois = variance(session, batchId);

        // O custo fechado não muda; a explicação sim, porque o envase é um fato novo sobre o lote.
        Assertions.assertThat(antes.get("volumes").size()).isLessThan(depois.get("volumes").size());
    }

    @Test
    @DisplayName("ver variação é alçada própria: ela expõe preço de compra por insumo")
    void exigeAlcadaPropria() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        var soCusto = principal(UUID.randomUUID(), Set.of("costing.cost.read"));

        mockMvc.perform(get(COSTING + "/batches/" + batchId + "/variance").with(authentication(soCusto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("lote de outra cervejaria não existe para quem pergunta")
    void isolaPorCervejaria() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        var other = principal(UUID.randomUUID(), Set.of("costing.variance.read"));

        mockMvc.perform(get(COSTING + "/batches/" + batchId + "/variance").with(authentication(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("unknown_batch")));
    }

    @Test
    @DisplayName("lote inexistente é recusado em vez de devolver variação vazia")
    void loteInexistenteEhRecusado() throws Exception {
        var session = login();

        mockMvc.perform(get(COSTING + "/batches/" + UUID.randomUUID() + "/variance").session(session))
                .andExpect(status().isNotFound());
    }

    // --- cenário ---

    private record Scene(String batchId, String maltId) {}

    private String startedBatch(MockHttpSession session) throws Exception {
        return startedScene(session).batchId;
    }

    /** Receita de 20 KG de malte, 60 G de lúpulo e 1 fermento, com lotes a 1,50 por unidade. */
    private Scene startedScene(MockHttpSession session) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var maltId = createIngredient(session, "MALT", "KG", "{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}");
        var hopId = createIngredient(session, "HOP", "G", "{\"alphaAcid\":\"12\"}");
        var yeastId = createIngredient(session, "YEAST", "UNIT", "{\"attenuation\":\"78\"}");
        receiveLot(session, maltId, 500, "KG", "1.5");
        receiveLot(session, hopId, 5000, "G", "1.5");
        receiveLot(session, yeastId, 50, "UNIT", "1.5");

        var content = """
                {"name":"Variação %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,
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

        return new Scene(batchOfOrder(session, orderId), maltId);
    }

    // --- helpers ---

    private JsonNode variance(MockHttpSession session, String batchId) throws Exception {
        return JSON.readTree(mockMvc.perform(get(COSTING + "/batches/" + batchId + "/variance")
                        .session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static java.util.List<String> reasons(JsonNode variance) {
        var reasons = new java.util.ArrayList<String>();
        for (JsonNode gap : variance.get("gaps")) {
            reasons.add(gap.get("reason").asText());
        }
        return reasons;
    }

    /** Confirma o consumo exatamente como a OP reservou. */
    private void consumeAsReserved(MockHttpSession session, String batchId) throws Exception {
        var body = proposal(session, batchId);
        var lines = new StringBuilder("[");
        for (JsonNode lot : body.get("reserved")) {
            if (lines.length() > 1) {
                lines.append(',');
            }
            lines.append(lot(lot.get("lotId").asText(), lot.get("reserved").asText(),
                    lot.get("unit").asText()));
        }
        postConsumption(session, batchId, lines.append(']').toString()).andExpect(status().isOk());
    }

    /** Confirma o consumo trocando a quantidade de malte pela informada. */
    private void consume(MockHttpSession session, String batchId, int maltQuantity) throws Exception {
        var body = proposal(session, batchId);
        var lines = new StringBuilder("[");
        for (JsonNode lot : body.get("reserved")) {
            if (lines.length() > 1) {
                lines.append(',');
            }
            var quantity = "KG".equals(lot.get("unit").asText()) ? String.valueOf(maltQuantity)
                    : lot.get("reserved").asText();
            lines.append(lot(lot.get("lotId").asText(), quantity, lot.get("unit").asText()));
        }
        postConsumption(session, batchId, lines.append(']').toString()).andExpect(status().isOk());
    }

    /** Confirma o consumo com lotes escolhidos à mão — inclusive os que a OP não reservou. */
    private void consumeLots(MockHttpSession session, String batchId, String... lines) throws Exception {
        postConsumption(session, batchId, "[" + String.join(",", lines) + "]")
                .andExpect(status().isOk());
    }

    private static String lot(String lotId, Object quantity, String unit) {
        return "{\"lotId\":\"" + lotId + "\",\"quantity\":" + quantity + ",\"unit\":\"" + unit + "\"}";
    }

    private JsonNode proposal(MockHttpSession session, String batchId) throws Exception {
        return JSON.readTree(mockMvc.perform(
                        get("/api/v1/production/batches/" + batchId + "/consumption/proposal")
                                .session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private ResultActions postConsumption(MockHttpSession session, String batchId, String lines)
            throws Exception {
        return mockMvc.perform(post("/api/v1/production/batches/" + batchId + "/consumption")
                .session(session).with(csrf()).contentType("application/json")
                .content("{\"lines\":" + lines + "}"));
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
        receiveLot(session, containerId, 2000, "UNIT", "1.5");
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

    private String batchOfOrder(MockHttpSession session, String orderId) throws Exception {
        var body = mockMvc.perform(get("/api/v1/production/batches").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode node : JSON.readTree(body)) {
            if (node.get("orderId").asText().equals(orderId)) {
                return node.get("id").asText();
            }
        }
        throw new AssertionError("lote não encontrado para a ordem " + orderId);
    }

    private String receiveLot(MockHttpSession session, String ingredientId, int quantity, String unit,
            String unitCost) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var supplierId = idOf(mockMvc.perform(post("/api/v1/suppliers").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"name\":\"Sup " + sfx + "\",\"code\":\"S-" + sfx + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        return idOf(mockMvc.perform(post("/api/v1/inventory/lots").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"ingredientId\":\"" + ingredientId + "\",\"supplierId\":\"" + supplierId
                                + "\",\"quantity\":" + quantity + ",\"unit\":\"" + unit + "\",\"unitCost\":"
                                + unitCost + ",\"supplierLotCode\":\"F-" + sfx + "\","
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
