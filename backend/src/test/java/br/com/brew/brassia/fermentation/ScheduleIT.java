package br.com.brew.brassia.fermentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
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
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
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
class ScheduleIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Instant START = Instant.parse("2026-08-01T08:00:00Z");
    private static final String TIME_STAGE = "{\"sequence\":1,\"name\":\"Primária\",\"targetTempC\":18.0,"
            + "\"condition\":\"TIME\",\"conditionDays\":5,\"requiresConfirmation\":true}";
    private static final String MANUAL_STAGE = "{\"sequence\":2,\"name\":\"Maturação\",\"targetTempC\":2.0,"
            + "\"condition\":\"MANUAL\",\"requiresConfirmation\":true}";

    @Autowired WebApplicationContext context;
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void plansTimelineFromPublishedProfile() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        var profileId = publishedProfile(session);

        plan(session, batchId, profileId).andExpect(status().isCreated())
                .andExpect(jsonPath("$.steps", is(2)));

        mockMvc.perform(get(schedule(batchId)).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileId", is(profileId)))
                .andExpect(jsonPath("$.steps.length()", is(2)))
                // Cada etapa tem ação, janela, condição, tolerância e responsável.
                .andExpect(jsonPath("$.steps[0].action", is("REST")))
                .andExpect(jsonPath("$.steps[0].condition", is("TIME")))
                .andExpect(jsonPath("$.steps[0].toleranceHours", is(12)))
                .andExpect(jsonPath("$.steps[0].responsibleUserId").isNotEmpty())
                .andExpect(jsonPath("$.steps[0].status", is("PLANNED")))
                // A primeira ancora; a seguinte encadeia.
                .andExpect(jsonPath("$.steps[0].dependsOnPrevious", is(false)))
                .andExpect(jsonPath("$.steps[1].dependsOnPrevious", is(true)));
    }

    @Test
    void refusesSecondScheduleAndDraftProfile() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        var profileId = publishedProfile(session);
        plan(session, batchId, profileId).andExpect(status().isCreated());

        // Um lote tem uma linha do tempo só.
        plan(session, batchId, profileId).andExpect(status().isConflict());

        var otherBatch = startedBatch(session);
        var draft = createProfile(session);
        plan(session, otherBatch, draft).andExpect(status().isConflict());
    }

    @Test
    void previewShowsTheImpactWithoutChangingAnything() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        plan(session, batchId, publishedProfile(session)).andExpect(status().isCreated());
        var steps = steps(session, batchId);
        var firstId = steps.get(0).get("id").asText();
        var originalSecondStart = steps.get(1).get("plannedStart").asText();

        mockMvc.perform(post(schedule(batchId) + "/steps/" + firstId + "/reschedule").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"newStart\":\"" + START.plus(Duration.ofDays(1)) + "\",\"apply\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deltaHours", is(24)))
                .andExpect(jsonPath("$.changes.length()", is(2)))
                .andExpect(jsonPath("$.changes[0].fromStart").isNotEmpty())
                .andExpect(jsonPath("$.changes[0].toStart").isNotEmpty());

        // Nada gravado: a agenda continua como estava.
        assertSecondStart(session, batchId, originalSecondStart);
    }

    @Test
    void applyingTheRescheduleMovesTheChainOnly() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        plan(session, batchId, publishedProfile(session)).andExpect(status().isCreated());
        var firstId = steps(session, batchId).get(0).get("id").asText();

        // Etapa-âncora com data própria: a propagação para nela.
        addStep(session, batchId, "Envase", "CONDITIONING", START.plus(Duration.ofDays(9)),
                START.plus(Duration.ofDays(10)), false).andExpect(status().isCreated());

        mockMvc.perform(post(schedule(batchId) + "/steps/" + firstId + "/reschedule").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"newStart\":\"" + START.plus(Duration.ofDays(2)) + "\",\"apply\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changes.length()", is(2)))
                .andExpect(jsonPath("$.blocked.length()", is(1)))
                .andExpect(jsonPath("$.blocked[0].name", is("Envase")));

        var after = steps(session, batchId);
        assertThatStart(after.get(0), START.plus(Duration.ofDays(2)));
        assertThatStart(after.get(1), START.plus(Duration.ofDays(7)));
        // A âncora ficou onde estava.
        assertThatStart(after.get(2), START.plus(Duration.ofDays(9)));
    }

    @Test
    void executionKeepsPlannedAndRecordsDeviationWithJustification() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        plan(session, batchId, publishedProfile(session)).andExpect(status().isCreated());
        var first = steps(session, batchId).get(0);
        var firstId = first.get("id").asText();
        var plannedStart = first.get("plannedStart").asText();

        // Fora da tolerância sem justificativa → recusado.
        execute(session, batchId, firstId, START.plus(Duration.ofDays(6)), null)
                .andExpect(status().isBadRequest());
        execute(session, batchId, firstId, START.plus(Duration.ofDays(6)), "Atraso na CIP")
                .andExpect(status().isOk());

        var executed = steps(session, batchId).get(0);
        // Planejado, executado, desvio e justificativa convivem no histórico.
        assertThat(executed.get("plannedStart").asText()).isEqualTo(plannedStart);
        assertThat(executed.get("status").asText()).isEqualTo("DONE");
        assertThat(executed.get("deviationHours").asLong()).isEqualTo(24);
        assertThat(executed.get("justification").asText()).isEqualTo("Atraso na CIP");
    }

    @Test
    void executedStepIsNotRescheduledNorExecutedTwice() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        plan(session, batchId, publishedProfile(session)).andExpect(status().isCreated());
        var firstId = steps(session, batchId).get(0).get("id").asText();
        execute(session, batchId, firstId, START.plus(Duration.ofDays(4)), null).andExpect(status().isOk());

        execute(session, batchId, firstId, START.plus(Duration.ofDays(4)), null).andExpect(status().isConflict());
        mockMvc.perform(post(schedule(batchId) + "/steps/" + firstId + "/reschedule").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"newStart\":\"" + START + "\",\"apply\":true}"))
                .andExpect(status().isConflict());
    }

    @Test
    void lateStepOpensAlertWithoutTouchingTheStep() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        // Agenda começada no passado: a primeira etapa já venceu a janela e a tolerância.
        planAt(session, batchId, publishedProfile(session), Instant.now().minus(Duration.ofDays(30)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/fermentation/schedule/late-step-alerts").session(session).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThan(0)));

        // O alerta caiu na central do lote e não mexeu na etapa.
        mockMvc.perform(get("/api/v1/production/batches/" + batchId + "/alerts").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", greaterThan(0)));
        mockMvc.perform(get(schedule(batchId)).session(session))
                .andExpect(jsonPath("$.steps[0].status", is("PLANNED")));
    }

    @Test
    void fgStabilityDerivesTheProfileFromTheSchedule() throws Exception {
        var session = login();
        var batchId = startedBatch(session);

        // Sem agenda não há critério: a avaliação explica isso.
        mockMvc.perform(get("/api/v1/fermentation/batches/" + batchId + "/fg-stability").session(session))
                .andExpect(status().isBadRequest());

        plan(session, batchId, publishedProfile(session)).andExpect(status().isCreated());

        // Com agenda, o perfil vem do lote — sem profileId por parâmetro (débito FER-003-1 encerrado).
        mockMvc.perform(get("/api/v1/fermentation/batches/" + batchId + "/fg-stability").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.policy.windowHours", is(48)))
                .andExpect(jsonPath("$.verdict", is("INSUFFICIENT_READINGS")));
    }

    @Test
    void deniesManageWithoutPermissionAndIsolatesByTenant() throws Exception {
        var session = login();
        var batchId = startedBatch(session);
        var profileId = publishedProfile(session);
        plan(session, batchId, profileId).andExpect(status().isCreated());

        mockMvc.perform(post(schedule(batchId))
                        .with(authentication(principal(UUID.randomUUID(), Set.of("fermentation.schedule.read"))))
                        .with(csrf()).contentType("application/json")
                        .content(planBody(profileId, START)))
                .andExpect(status().isForbidden());

        var other = principal(UUID.randomUUID(),
                Set.of("fermentation.schedule.read", "fermentation.schedule.manage"));
        mockMvc.perform(get(schedule(batchId)).with(authentication(other)))
                .andExpect(status().isBadRequest());
    }

    // --- helpers ---

    private static String schedule(String batchId) {
        return "/api/v1/fermentation/batches/" + batchId + "/schedule";
    }

    private static String planBody(String profileId, Instant start) {
        return "{\"profileId\":\"" + profileId + "\",\"start\":\"" + start + "\",\"responsibleUserId\":\""
                + UUID.randomUUID() + "\",\"defaultDurationDays\":2,\"toleranceHours\":12}";
    }

    private ResultActions plan(MockHttpSession session, String batchId, String profileId) throws Exception {
        return planAt(session, batchId, profileId, START);
    }

    private ResultActions planAt(MockHttpSession session, String batchId, String profileId, Instant start)
            throws Exception {
        return mockMvc.perform(post(schedule(batchId)).session(session).with(csrf())
                .contentType("application/json").content(planBody(profileId, start)));
    }

    private ResultActions addStep(MockHttpSession session, String batchId, String name, String action,
            Instant start, Instant end, boolean dependsOnPrevious) throws Exception {
        var content = """
                {"name":"%s","action":"%s","condition":"MANUAL","plannedStart":"%s","plannedEnd":"%s",
                 "toleranceHours":6,"responsibleUserId":"%s","dependsOnPrevious":%s}
                """.formatted(name, action, start, end, UUID.randomUUID(), dependsOnPrevious);
        return mockMvc.perform(post(schedule(batchId) + "/steps").session(session).with(csrf())
                .contentType("application/json").content(content));
    }

    private ResultActions execute(MockHttpSession session, String batchId, String stepId, Instant at,
            String justification) throws Exception {
        var content = "{\"executedAt\":\"" + at + "\""
                + (justification == null ? "" : ",\"justification\":\"" + justification + "\"") + "}";
        return mockMvc.perform(post(schedule(batchId) + "/steps/" + stepId + "/execute").session(session)
                .with(csrf()).contentType("application/json").content(content));
    }

    private JsonNode steps(MockHttpSession session, String batchId) throws Exception {
        var body = mockMvc.perform(get(schedule(batchId)).session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("steps");
    }

    private void assertSecondStart(MockHttpSession session, String batchId, String expected) throws Exception {
        assertThat(steps(session, batchId).get(1).get("plannedStart").asText()).isEqualTo(expected);
    }

    private static void assertThatStart(JsonNode step, Instant expected) {
        assertThat(Instant.parse(step.get("plannedStart").asText())).isEqualTo(expected);
    }

    private String createProfile(MockHttpSession session) throws Exception {
        var code = "SCH-" + UUID.randomUUID().toString().substring(0, 6);
        var body = mockMvc.perform(post("/api/v1/fermentation/profiles").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"" + code + "\",\"name\":\"Agenda\",\"stages\":["
                                + TIME_STAGE + "," + MANUAL_STAGE + "]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JSON.readTree(body).get("id").asText();
    }

    private String publishedProfile(MockHttpSession session) throws Exception {
        var id = createProfile(session);
        mockMvc.perform(post("/api/v1/fermentation/profiles/" + id + "/publish").session(session).with(csrf()))
                .andExpect(status().isOk());
        return id;
    }

    private String startedBatch(MockHttpSession session) throws Exception {
        var orderId = releasedOrder(session);
        mockMvc.perform(post("/api/v1/brew-orders/" + orderId + "/start").session(session).with(csrf()))
                .andExpect(status().isOk());
        var listBody = mockMvc.perform(get("/api/v1/production/batches").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (JsonNode node : JSON.readTree(listBody).get("content")) {
            if (node.get("orderId").asText().equals(orderId)) {
                return node.get("id").asText();
            }
        }
        throw new AssertionError("lote não encontrado para a ordem " + orderId);
    }

    private String releasedOrder(MockHttpSession session) throws Exception {
        var sfx = UUID.randomUUID().toString().substring(0, 8);
        var equipmentId = idOf(mockMvc.perform(post("/api/v1/equipment").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"code\":\"bh-" + sfx + "\",\"name\":\"BH\",\"capacityLiters\":500,"
                                + "\"deadSpaceLiters\":20,\"mashEfficiencyPercent\":72,\"boilOffLitersPerHour\":8}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        var maltId = createIngredient(session, "MALT", "m-" + sfx, "KG", "{\"potentialSg\":\"1.037\",\"colorEbc\":\"4\"}");
        var hopId = createIngredient(session, "HOP", "h-" + sfx, "G", "{\"alphaAcid\":\"12\"}");
        var yeastId = createIngredient(session, "YEAST", "y-" + sfx, "UNIT", "{\"attenuation\":\"78\"}");
        var content = """
                {"name":"Sched %s","equipmentId":"%s","batchVolumeLiters":400,"boilTimeMinutes":60,"targetIbu":30,
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
        return orderId;
    }

    private String createIngredient(MockHttpSession session, String type, String code, String unit, String attributes)
            throws Exception {
        return idOf(mockMvc.perform(post("/api/v1/catalog/ingredients").session(session).with(csrf())
                        .contentType("application/json")
                        .content("{\"type\":\"" + type + "\",\"code\":\"" + code + "\",\"name\":\"" + code
                                + "\",\"useUnit\":\"" + unit + "\",\"purchaseUnit\":\"" + unit + "\",\"attributes\":"
                                + attributes + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private static String idOf(String json) throws Exception {
        return JSON.readTree(json).get("id").asText();
    }

    private MockHttpSession login() throws Exception {
        var result = mockMvc.perform(post("/api/v1/security/login").with(csrf())
                        .contentType("application/json")
                        .content("{\"email\":\"admin@brassia.local\",\"password\":\"admin-local-123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Authentication principal(UUID breweryId, Set<String> permissions) {
        var p = new SecurityPrincipal(UUID.randomUUID(), breweryId, "Tester", permissions);
        return new UsernamePasswordAuthenticationToken(p, "n/a", Set.of());
    }
}
