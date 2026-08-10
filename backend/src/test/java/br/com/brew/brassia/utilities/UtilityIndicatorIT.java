package br.com.brew.brassia.utilities;

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
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Água, energia e CO₂ por litro envasado, de ponta a ponta (UTL-001).
 *
 * <p>O indicador não tem tabela: o que estes testes provam é que ele enxerga as medições que já
 * existem em módulos que não se conhecem — o ciclo de limpeza e a conexão de gás — e que se recusa
 * a inventar o que não foi medido.
 *
 * <p><strong>Os testes dividem o mesmo banco</strong>, então nenhum deles afirma um total absoluto:
 * cada um cria o fato que quer observar e verifica a relação, não o número acumulado da suíte.
 */
@SpringBootTest
@Testcontainers
class UtilityIndicatorIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String INDICATORS = "/api/v1/utilities/indicators";
    private static final String PLANS = "/api/v1/packaging/plans";
    private static final String CYLINDERS = "/api/v1/gas/cylinders";
    private static final String COMPONENTS = "/api/v1/gas/components";
    private static final String CONNECTIONS = "/api/v1/gas/connections";

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @DisplayName("a água e a energia do ciclo de limpeza chegam ao indicador sem ninguém integrar nada")
    void aguaEEnergiaDoCicloEntram() throws Exception {
        var session = login();
        var antes = indicator(report(session), "WATER");

        var procedureCode = releaseCleaning(session, equipment(session), "300", "12", "1.5");

        var depois = indicator(report(session), "WATER");
        Assertions.assertThat(total(depois).subtract(total(antes))).isEqualByComparingTo("300");
        Assertions.assertThat(indicator(report(session), "ENERGY")).isNotNull();
        // A origem viaja junto: um número de sustentabilidade sem procedência não prova nada.
        mockMvc.perform(get(INDICATORS).session(session))
                .andExpect(jsonPath("$.indicators[?(@.type=='WATER')].sources[*]",
                        hasItem(containsString(procedureCode))));
    }

    @Test
    @DisplayName("o CO₂ pesado na conexão entra, e sem cobertura declarada não se diz medido por completo")
    void co2DaConexaoEntra() throws Exception {
        var session = login();
        var antes = indicator(report(session), "CO2");

        consumeGas(session, "4");

        var depois = indicator(report(session), "CO2");
        Assertions.assertThat(total(depois).subtract(antes == null ? BigDecimal.ZERO : total(antes)))
                .isEqualByComparingTo("4");
        // UTL-001-A: não existe consumo esperado de CO₂ contra o qual comparar.
        Assertions.assertThat(depois.get("coverage")).isEmpty();
        Assertions.assertThat(depois.get("fullyMeasured").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("ciclo encerrado sem consumo lançado derruba a cobertura: o indicador fala por um pedaço")
    void cicloSemConsumoDerrubaACobertura() throws Exception {
        var session = login();
        releaseCleaning(session, equipment(session), null, null, null);

        var water = indicator(report(session), "WATER");

        Assertions.assertThat(water.get("fullyMeasured").asBoolean()).isFalse();
        var coverage = water.get("coverage").get(0);
        Assertions.assertThat(coverage.get("reported").asInt())
                .isLessThan(coverage.get("expected").asInt());
        Assertions.assertThat(coverage.get("what").asText()).contains("ciclos de limpeza");
    }

    @Test
    @DisplayName("sem envase no período, o consumo aparece e o por litro não: zero seria dizer que foi eficiente")
    void semEnvaseNaoHaPorLitro() throws Exception {
        var session = login();
        // A janela abre agora e só o que este teste criar cabe nela: houve limpeza, não houve envase.
        var from = Instant.now();
        releaseCleaning(session, equipment(session), "300", "12", "1.5");
        var body = report(session, from, from.plusSeconds(120));

        Assertions.assertThat(body.get("packagedLiters").decimalValue()).isEqualByComparingTo("0");
        var water = indicator(body, "WATER");
        Assertions.assertThat(total(water)).isGreaterThan(BigDecimal.ZERO);
        Assertions.assertThat(water.get("perLiter").isNull()).isTrue();
        Assertions.assertThat(water.get("measuredPerLiter").isNull()).isTrue();
    }

    @Test
    @DisplayName("com envase no período, o por litro é o consumo dividido pelos litros que saíram")
    void porLitroDivideOEnvasado() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        transfer(session, batchId);
        releaseCleaning(session, equipment(session), "300", "12", "1.5");
        executePlan(session, batchId);

        var body = report(session);

        var liters = body.get("packagedLiters").decimalValue();
        Assertions.assertThat(liters).isGreaterThan(BigDecimal.ZERO);
        var water = indicator(body, "WATER");
        Assertions.assertThat(water.get("perLiter").decimalValue())
                .isEqualByComparingTo(total(water).divide(liters, 4, RoundingMode.HALF_UP));
    }

    @Test
    @DisplayName("fora do período nada entra: o indicador é do recorte que se pediu")
    void foraDoPeriodoNadaEntra() throws Exception {
        var session = login();
        releaseCleaning(session, equipment(session), "300", "12", "1.5");

        mockMvc.perform(get(INDICATORS).session(session)
                        .param("from", "2020-01-01T00:00:00Z").param("to", "2020-02-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indicators.length()", is(0)))
                .andExpect(jsonPath("$.packagedLiters", is(0)));
    }

    @Test
    @DisplayName("período invertido é erro de quem perguntou, não relatório vazio")
    void recusaPeriodoInvertido() throws Exception {
        var session = login();

        mockMvc.perform(get(INDICATORS).session(session)
                        .param("from", "2026-08-01T00:00:00Z").param("to", "2026-07-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("o indicador tem alçada própria")
    void exigeAlcada() throws Exception {
        mockMvc.perform(get(INDICATORS).with(authentication(principal(UUID.randomUUID(), Set.of()))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("o consumo de outra cervejaria não existe para quem pergunta")
    void isolaPorCervejaria() throws Exception {
        var session = login();
        releaseCleaning(session, equipment(session), "300", "12", "1.5");

        var other = principal(UUID.randomUUID(), Set.of("utilities.indicator.read"));
        mockMvc.perform(get(INDICATORS).with(authentication(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indicators.length()", is(0)))
                .andExpect(jsonPath("$.packagedLiters", is(0)));
    }

    // --- leitura ---

    private JsonNode report(MockHttpSession session) throws Exception {
        return JSON.readTree(mockMvc.perform(get(INDICATORS).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode report(MockHttpSession session, Instant from, Instant to) throws Exception {
        return JSON.readTree(mockMvc.perform(get(INDICATORS).session(session)
                        .param("from", from.toString()).param("to", to.toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    /** O indicador do tipo pedido, ou nulo quando ninguém mediu aquilo ainda. */
    private static JsonNode indicator(JsonNode report, String type) {
        for (JsonNode node : report.get("indicators")) {
            if (node.get("type").asText().equals(type)) {
                return node;
            }
        }
        return null;
    }

    private static BigDecimal total(JsonNode indicator) {
        return indicator == null ? BigDecimal.ZERO : indicator.get("total").decimalValue();
    }

    // --- cenário: limpeza ---

    /** Cria, executa e libera um ciclo; lança o consumo quando informado. Devolve o código do procedimento. */
    private String releaseCleaning(MockHttpSession session, String equipmentId, String water, String energy,
            String product) throws Exception {
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
        if (water != null) {
            mockMvc.perform(post("/api/v1/sanitation/cycles/" + cycleId + "/consumption").session(session)
                            .with(csrf()).contentType("application/json")
                            .content("{\"waterLiters\":" + water + ",\"energyKwh\":" + energy
                                    + ",\"productKg\":" + product + "}"))
                    .andExpect(status().isNoContent());
        }
        return code;
    }

    // --- cenário: gás ---

    private void consumeGas(MockHttpSession session, String kg) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var due = LocalDate.now().plusYears(3).toString();
        var cylinderId = idOf(mockMvc.perform(post(CYLINDERS).session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"CIL-" + sfx + "\",\"gasType\":\"CO2\",\"capacityKg\":10,"
                                + "\"tareKg\":12.5,\"contentKg\":10,\"requalificationDueOn\":\"" + due + "\","
                                + "\"location\":\"Casa de gases\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        var regulatorId = component(session, "REGULATOR", "R-" + sfx, "10", "3");
        var manifoldId = component(session, "MANIFOLD", "M-" + sfx, "6", null);
        var connectionId = idOf(mockMvc.perform(post(CONNECTIONS).session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"cylinderId\":\"" + cylinderId + "\",\"regulatorId\":\"" + regulatorId
                                + "\",\"manifoldId\":\"" + manifoldId + "\",\"pointOfUseEquipmentId\":\""
                                + equipment(session) + "\",\"workingPressureBar\":3}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mockMvc.perform(post(CONNECTIONS + "/" + connectionId + "/leak-test").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"passed\":true,\"method\":\"espuma\",\"pressureDropBar\":0}"))
                .andExpect(status().isOk());
        mockMvc.perform(post(CONNECTIONS + "/" + connectionId + "/consumption").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"kg\":" + kg + ",\"reason\":\"carbonatação\"}"))
                .andExpect(status().isOk());
    }

    private String component(MockHttpSession session, String kind, String code, String maxBar, String setBar)
            throws Exception {
        var content = "{\"kind\":\"" + kind + "\",\"code\":\"" + code + "\",\"name\":\"" + code + "\","
                + "\"maxPressureBar\":" + maxBar
                + (setBar == null ? "" : ",\"setPressureBar\":" + setBar) + "}";
        return idOf(mockMvc.perform(post(COMPONENTS).session(session).with(csrf())
                        .contentType("application/json").content(content))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    // --- cenário: produção e envase ---

    private String startedBatch(MockHttpSession session) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var maltId = createIngredient(session, "MALT", "KG", "{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}");
        var hopId = createIngredient(session, "HOP", "G", "{\"alphaAcid\":\"12\"}");
        var yeastId = createIngredient(session, "YEAST", "UNIT", "{\"attenuation\":\"78\"}");
        receiveLot(session, maltId, 500, "KG");
        receiveLot(session, hopId, 5000, "G");
        receiveLot(session, yeastId, 50, "UNIT");

        var content = """
                {"name":"Utilidades %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,
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
        releaseCleaning(session, lineId, "80", "3", "0.4");
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
        for (JsonNode node : JSON.readTree(body).get("content")) {
            if (node.get("orderId").asText().equals(orderId)) {
                return node.get("id").asText();
            }
        }
        throw new AssertionError("lote não encontrado para a ordem " + orderId);
    }

    // --- helpers ---

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
                        .content("{\"code\":\"" + code + "\",\"name\":\"Tanque\",\"capacityLiters\":500,"
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
