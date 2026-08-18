package br.com.brew.brassia.packaging;

import br.com.brew.brassia.support.BrewScenario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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

@SpringBootTest
@Testcontainers
class PackagingRunIT {

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
    private static final String PLANS = "/api/v1/packaging/plans";

    @Autowired WebApplicationContext context;

    @Autowired org.springframework.jdbc.core.simple.JdbcClient jdbc;
    MockMvc mockMvc;

    /**
     * A máquina de construir cerveja mora fora daqui (DEB-SAL-003).
     *
     * <p>Os métodos abaixo viraram delegação de uma linha: o cenário é o mesmo, e agora ele tem UM dono.
     * Duas cópias divergem na primeira regra nova, e a segunda a mudar não avisa a primeira.
     */
    BrewScenario cenario;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
        cenario = new BrewScenario(mockMvc);
    }

    @Test
    void executionDerivesLossesAndClosesTheBalance() throws Exception {
        var session = login();
        var scene = reservedPlan(session, 1000);

        // 780 boas + 12 rejeitadas de 355 ml = 281,16 L; saíram 284 L → 2,84 L de perda.
        execute(session, scene.planId, "284", 780, 12).andExpect(status().isOk())
                .andExpect(jsonPath("$.packagedVolumeLiters", is(276.9)))
                .andExpect(jsonPath("$.lossesLiters", is(2.84)))
                .andExpect(jsonPath("$.containersConsumed", is(792)));

        mockMvc.perform(get(PLANS + "/" + scene.planId + "/execution").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.producedUnits", is(780)))
                .andExpect(jsonPath("$.rejectedUnits", is(12)))
                .andExpect(jsonPath("$.rejectedVolumeLiters", is(4.26)))
                .andExpect(jsonPath("$.lossPercent", closeTo(1.0, 0.01)));

        mockMvc.perform(get(PLANS + "/" + scene.planId).session(session))
                .andExpect(jsonPath("$.status", is("EXECUTED")));
    }

    @Test
    void packagingConsumptionLeavesTheStockAndReleasesTheLeftoverReservation() throws Exception {
        var session = login();
        var scene = reservedPlan(session, 1000);

        // Reservou 800; 792 viraram lata (boas + rejeitos) e 8 voltam ao disponível.
        mockMvc.perform(get("/api/v1/inventory/lots/" + scene.lotId + "/balance").session(session))
                .andExpect(jsonPath("$.available", is(200.0)))
                .andExpect(jsonPath("$.onHand", is(1000.0)));

        execute(session, scene.planId, "284", 780, 12).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/inventory/lots/" + scene.lotId + "/balance").session(session))
                // Consumo é saída física: o on-hand caiu de verdade.
                .andExpect(jsonPath("$.onHand", is(208.0)))
                // Nada continua reservado: o plano executado não segura estoque.
                .andExpect(jsonPath("$.available", is(208.0)));

        mockMvc.perform(get("/api/v1/inventory/lots/" + scene.lotId + "/movements").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type=='CONSUMPTION')]").isNotEmpty());
    }

    @Test
    void refusesUnitsThatHoldMoreBeerThanLeftTheTank() throws Exception {
        var session = login();
        var scene = reservedPlan(session, 1000);

        // 800 unidades de 355 ml = 284 L, mas só saíram 280 L do tanque.
        execute(session, scene.planId, "280", 800, 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("volume_balance")))
                .andExpect(jsonPath("$.balance.inputVolumeLiters", is(280)))
                .andExpect(jsonPath("$.balance.packagedVolumeLiters", is(284.0)))
                .andExpect(jsonPath("$.balance.shortfallLiters", is(4.0)));

        // Recusa não consome embalagem nem executa o plano.
        mockMvc.perform(get("/api/v1/inventory/lots/" + scene.lotId + "/balance").session(session))
                .andExpect(jsonPath("$.onHand", is(1000.0)));
        mockMvc.perform(get(PLANS + "/" + scene.planId).session(session))
                .andExpect(jsonPath("$.status", is("RESERVED")));
    }

    @Test
    void rejectedUnitsCountAgainstTheBalanceAndConsumePackaging() throws Exception {
        var session = login();
        var scene = reservedPlan(session, 1000);

        // 780 boas cabem em 280 L; 780 + 30 rejeitadas não.
        execute(session, scene.planId, "280", 780, 30).andExpect(status().isConflict());

        execute(session, scene.planId, "284", 700, 92).andExpect(status().isOk())
                .andExpect(jsonPath("$.containersConsumed", is(792)));
    }

    @Test
    void refusesToPackageMoreThanTheBatchHasLeft() throws Exception {
        var session = login();
        var scene = reservedPlan(session, 1000);

        // O lote foi transferido com 390 L; pedir 400 estoura o que existiu no tanque.
        execute(session, scene.planId, "400", 800, 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("batch_volume_exceeded")))
                .andExpect(jsonPath("$.batchVolume.remainingLiters", is(390.0)))
                .andExpect(jsonPath("$.batchVolume.requestedLiters", is(400)));
    }

    @Test
    void onlyReservedPlanIsExecutedAndExecutionIsTerminal() throws Exception {
        var session = login();
        var scene = plannedOnly(session);

        // Plano ainda sem reserva não executa.
        execute(session, scene.planId, "100", 200, 0).andExpect(status().isConflict());

        var reserved = reservedPlan(session, 1000);
        execute(session, reserved.planId, "284", 780, 12).andExpect(status().isOk());
        // Executar de novo é conflito, não um segundo envase.
        execute(session, reserved.planId, "50", 100, 0).andExpect(status().isConflict());
        // Executado não é cancelável: desfazer produção não é cancelar plano.
        mockMvc.perform(post(PLANS + "/" + reserved.planId + "/cancel").session(session).with(csrf())
                        .contentType("application/json").content("{\"reason\":\"mudei de ideia\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void executionUsesFreeStockWhenTheRunSpentMoreThanReserved() throws Exception {
        var session = login();
        var scene = reservedPlan(session, 1000);

        // Reservou 800, mas a linha gastou 850 (com rejeitos): o excedente sai do saldo livre.
        execute(session, scene.planId, "310", 800, 50).andExpect(status().isOk())
                .andExpect(jsonPath("$.containersConsumed", is(850)));

        mockMvc.perform(get("/api/v1/inventory/lots/" + scene.lotId + "/balance").session(session))
                .andExpect(jsonPath("$.onHand", is(150.0)))
                .andExpect(jsonPath("$.available", is(150.0)));
    }

    @Test
    void reportsShortfallWhenThereIsNotEnoughPackagingForTheRun() throws Exception {
        var session = login();
        var scene = reservedPlan(session, 810);

        execute(session, scene.planId, "310", 800, 50)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("insufficient_packaging_stock")))
                .andExpect(jsonPath("$.shortfall.requested", is(850)));

        // Nada consumido: a recusa não deixa efeito parcial.
        mockMvc.perform(get("/api/v1/inventory/lots/" + scene.lotId + "/balance").session(session))
                .andExpect(jsonPath("$.onHand", is(810.0)));
    }

    @Test
    void batchCanBeSplitAcrossRunsWhileTheSumFits() throws Exception {
        var session = login();
        var batchId = fermentingBatch(session);
        var containerId = createIngredient(session, "PACKAGING", "UNIT",
                "{\"volumeMl\":\"355\",\"material\":\"lata\"}");
        receiveContainers(session, containerId, 2000);

        var first = reserveFor(session, batchId, containerId, 500);
        execute(session, first, "177.5", 500, 0).andExpect(status().isOk());

        var second = reserveFor(session, batchId, containerId, 500);
        execute(session, second, "177.5", 500, 0).andExpect(status().isOk());

        // 177,5 + 177,5 = 355 L de 390; um terceiro envase de 100 L estoura o lote.
        var third = reserveFor(session, batchId, containerId, 300);
        execute(session, third, "100", 280, 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("batch_volume_exceeded")))
                .andExpect(jsonPath("$.batchVolume.alreadyPackagedLiters", is(355.0)));
    }

    @Test
    void rejectsRunWithoutUnitsOrWithoutInput() throws Exception {
        var session = login();
        var scene = reservedPlan(session, 1000);

        execute(session, scene.planId, "284", 0, 0).andExpect(status().isBadRequest());
        execute(session, scene.planId, "0", 800, 0).andExpect(status().isBadRequest());
        execute(session, scene.planId, "284", -1, 0).andExpect(status().isBadRequest());
    }

    @Test
    void deniesManageWithoutPermission() throws Exception {
        var session = login();
        var scene = reservedPlan(session, 1000);

        mockMvc.perform(post(PLANS + "/" + scene.planId + "/execution")
                        .with(authentication(principal(UUID.randomUUID(), Set.of("packaging.plan.read"))))
                        .with(csrf()).contentType("application/json").content(body("284", 780, 12)))
                .andExpect(status().isForbidden());
    }

    @Test
    void isolatesByTenant() throws Exception {
        var session = login();
        var scene = reservedPlan(session, 1000);
        execute(session, scene.planId, "284", 780, 12).andExpect(status().isOk());

        var other = principal(UUID.randomUUID(), Set.of("packaging.plan.read", "packaging.plan.manage"));
        mockMvc.perform(get(PLANS + "/" + scene.planId + "/execution").with(authentication(other)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(PLANS + "/" + scene.planId + "/execution").with(authentication(other)).with(csrf())
                        .contentType("application/json").content(body("100", 100, 0)))
                .andExpect(status().isBadRequest());
    }

    // --- cenário ---

    private record Scene(String planId, String lotId) {}

    /** Plano reservado de 800 latas de 355 ml, com {@code stock} latas em estoque. */
    @Test
    void oEnvaseGeraLoteDeProdutoAcabado() throws Exception {
        var session = login();
        var scene = reservedPlan(session, 1000);

        var body = execute(session, scene.planId, "284", 780, 12).andExpect(status().isOk())
                // O código sai na resposta da execução: é o que vai impresso na embalagem.
                .andExpect(jsonPath("$.finishedLotCode", is(notNullValue())))
                .andReturn().getResponse().getContentAsString();
        var code = JSON.readTree(body).get("finishedLotCode").asText();

        // A suíte compartilha a cervejaria de bootstrap: filtrar pelo lote desta execução é o que
        // torna a asserção sobre ESTE envase, e não sobre o primeiro da lista.
        var batchId = batchOfPlan(session, scene.planId);
        var lote = finishedLotOf(session, batchId, code);
        // Só as unidades boas: as 12 rejeitadas consumiram lata e não viraram produto.
        assertThat(lote.get("units").asInt()).isEqualTo(780);
        assertThat(lote.get("volumeLiters").asDouble()).isEqualTo(276.9);
    }

    private JsonNode finishedLotOf(MockHttpSession session, String batchId, String code)
            throws Exception {
        return cenario.finishedLotOf(session, batchId, code);
    }

    @Test
    void doisEnvasesDoMesmoLoteGeramLotesDistintos() throws Exception {
        var session = login();
        var batchId = fermentingBatch(session);
        var containerId = createIngredient(session, "PACKAGING", "UNIT",
                "{\"volumeMl\":\"355\",\"material\":\"lata\"}");
        receiveContainers(session, containerId, 4000);

        var primeiro = reserveFor(session, batchId, containerId, 400);
        execute(session, primeiro, "100", 280, 0).andExpect(status().isOk());
        var segundo = reserveFor(session, batchId, containerId, 400);
        execute(session, segundo, "100", 280, 0).andExpect(status().isOk());

        var body = mockMvc.perform(get("/api/v1/packaging/finished-lots").param("batchId", batchId)
                        .session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var codigos = new java.util.ArrayList<String>();
        for (JsonNode lot : JSON.readTree(body)) {
            codigos.add(lot.get("code").asText());
        }

        // Foram latas diferentes em momentos diferentes: um recall pode atingir só uma das duas.
        assertThat(codigos).hasSize(2).doesNotHaveDuplicates();
        assertThat(codigos).allMatch(c -> c.contains("/"));
    }

    @Test
    void aGenealogiaDoLoteChegaAoProdutoAcabado() throws Exception {
        var session = login();
        var scene = reservedPlan(session, 1000);
        execute(session, scene.planId, "284", 780, 12).andExpect(status().isOk());
        var batchId = batchOfPlan(session, scene.planId);

        mockMvc.perform(get("/api/v1/traceability/genealogy").session(session)
                        .param("nodeType", "BATCH").param("nodeId", batchId).param("direction", "FORWARD"))
                .andExpect(status().isOk())
                // A cadeia para a frente não termina mais na execução (TRC-001-B).
                .andExpect(jsonPath("$.nodes[*].type", hasItem("FINISHED_LOT")))
                .andExpect(jsonPath("$.edges[*].kind", hasItem("lote de produto acabado")))
                // O que falta agora é o passo de fora da fábrica.
                .andExpect(jsonPath("$.gaps[*].expectedLink", hasItem("expedição e destino")));
    }

    private String batchOfPlan(MockHttpSession session, String planId) throws Exception {
        var body = mockMvc.perform(get(PLANS + "/" + planId).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("batchId").asText();
    }

    private Scene reservedPlan(MockHttpSession session, int stock) throws Exception {
        var batchId = fermentingBatch(session);
        var containerId = createIngredient(session, "PACKAGING", "UNIT",
                "{\"volumeMl\":\"355\",\"material\":\"lata\"}");
        var lotId = receiveContainers(session, containerId, stock);
        return new Scene(reserveFor(session, batchId, containerId, 800), lotId);
    }

    private Scene plannedOnly(MockHttpSession session) throws Exception {
        var batchId = fermentingBatch(session);
        var containerId = createIngredient(session, "PACKAGING", "UNIT",
                "{\"volumeMl\":\"355\",\"material\":\"lata\"}");
        var lotId = receiveContainers(session, containerId, 1000);
        return new Scene(plan(session, batchId, containerId, createEquipment(session), 800), lotId);
    }

    private String reserveFor(MockHttpSession session, String batchId, String containerId, int units)
            throws Exception {
        return cenario.reservedPlan(session, batchId, containerId, units);
    }

    private String plan(MockHttpSession session, String batchId, String containerId, String lineId,
            int units) throws Exception {
        return cenario.plan(session, batchId, containerId, lineId, units);
    }

    // --- helpers ---

    private ResultActions execute(MockHttpSession session, String planId, String input, int produced,
            int rejected) throws Exception {
        return mockMvc.perform(post(PLANS + "/" + planId + "/execution").session(session).with(csrf())
                .contentType("application/json").content(body(input, produced, rejected)));
    }

    private static String body(String input, int produced, int rejected) {
        return "{\"inputVolumeLiters\":" + input + ",\"producedUnits\":" + produced
                + ",\"rejectedUnits\":" + rejected + "}";
    }

    private void releaseCleaning(MockHttpSession session, String equipmentId) throws Exception {
        cenario.releaseCleaning(session, equipmentId);
    }

    private String receiveContainers(MockHttpSession session, String containerId, int quantity)
            throws Exception {
        return cenario.receiveContainers(session, containerId, quantity);
    }

    private String fermentingBatch(MockHttpSession session) throws Exception {
        return cenario.fermentingBatch(session);
    }

    private String startedBatch(MockHttpSession session) throws Exception {
        return cenario.startedBatch(session);
    }

    private String releasedOrder(MockHttpSession session) throws Exception {
        return cenario.releasedOrder(session);
    }

    private String createEquipment(MockHttpSession session) throws Exception {
        return cenario.equipment(session);
    }

    private String createIngredient(MockHttpSession session, String type, String unit, String attrs)
            throws Exception {
        return cenario.ingredient(session, type, unit, attrs);
    }

    private static String idOf(String json) throws Exception {
        return JSON.readTree(json).get("id").asText();
    }

    private MockHttpSession login() throws Exception {
        return cenario.login();
    }

    private Authentication principal(UUID breweryId, Set<String> permissions) {
        var p = new SecurityPrincipal(UUID.randomUUID(), breweryId, "Tester", permissions);
        return new UsernamePasswordAuthenticationToken(p, "n/a", Set.of());
    }
}
